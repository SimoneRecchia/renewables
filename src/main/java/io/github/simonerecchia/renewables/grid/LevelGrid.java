package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;
import io.github.simonerecchia.renewables.catalog.ConductorCatalog;
import io.github.simonerecchia.renewables.net.Net;
import io.github.simonerecchia.renewables.net.packet.PowerUpdatePayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The network of one level: builds the graph from the saved spans and the machines that are loaded, hands it to
 * {@link GridSolver}, and delivers the answer to every machine and to the clients that can see it.
 *
 * The graph is read from the world on every calculation, because the things that decide it change without telling
 * anyone: a switch changes its buses, a chunk goes and takes its machines with it.  What the solver works out from
 * the graph alone is kept for as long as the graph reads the same ({@link #network}), so a steady tick only pushes
 * the generators' output through.  The rest this object keeps between calculations is what has to survive them -
 * which positions were powered (so a machine that drops out is told zero), what the clients were last sent (so that
 * a steady reading is not repeated every tick) and the disturbance a turbine is in the middle of.
 *
 * A machine that throws is left out of the calculation, not allowed to stop it; the failure is logged once a
 * minute ({@link FaultLog}).
 *
 * Chunks are never loaded from here.  A span with an end in a chunk that is not loaded is left alone and left out of
 * the graph until the chunk returns; a span whose end is in a loaded chunk but is no longer a machine of the kind it
 * was strung to is stale and goes, with its refund.
 */
final class LevelGrid {
	/** A reading that has moved by less than this many kW is not worth a packet. */
	static final double SEND_TOLERANCE_KW = 0.05;
	/** But a client that has been told nothing for this long is told again, so a lost or missed update heals in a second. */
	static final long HEARTBEAT_TICKS = 20;

	/** What a span end resolves to this calculation. */
	private enum Status {
		/** A machine of the right kind that owns the id. */
		ACTIVE,
		/** Not to be judged now: the chunk is not loaded, or the machine has not been given its ids yet. */
		DORMANT,
		/** Loaded and wrong: nothing there, or another kind of machine, or an id the machine does not have. */
		STALE
	}

	private record End(Status status, @Nullable InsulatorHost host, int index) {
		static final End DORMANT = new End(Status.DORMANT, null, -1);
		static final End STALE = new End(Status.STALE, null, -1);
	}

	/** What one block position holds, read once per calculation. */
	private record Slot(boolean loaded, @Nullable InsulatorHost host, int[] ids) {
		static final Slot UNLOADED = new Slot(false, null, new int[0]);
	}

	private record Sent(double kw, long tick) {
	}

	/** The positions that were given power last time, plus those that have yet to be told they lost it. */
	private Set<Long> powered = new HashSet<>();
	private final Map<Long, Sent> sent = new HashMap<>();
	/** The disturbance each turbine (by its fitting id) is in the middle of. */
	private Map<Integer, PowerDeliveryEvent> memory = new HashMap<>();
	private long memoryTick = Long.MIN_VALUE;
	private boolean running;
	private final FaultLog faults = new FaultLog();

	/** The solver for the graph last read, and that graph: the per-generator work it caches outlives a tick. */
	@Nullable
	private GridSolver network;
	private List<GridSolver.Node> networkNodes = List.of();
	private List<GridSolver.Edge> networkEdges = List.of();
	private Set<Long> networkLoads = Set.of();

	/** Whether there is nothing left to remember, so the owner may let this go until the level has spans again. */
	boolean idle() {
		return powered.isEmpty() && sent.isEmpty() && memory.isEmpty();
	}

	/** Where this level's grid reports what went wrong. */
	FaultLog faults() {
		return faults;
	}

	/** Builds the graph, solves it and delivers.  Does nothing if a calculation is already under way (a delivery that triggers one). */
	void solve(ServerLevel level) {
		if (running) return;

		running = true;
		try {
			compute(level);
		} finally {
			running = false;
		}
	}

	private void compute(ServerLevel level) {
		List<WireConnection> spans = WireLedger.spans(level);

		Map<Long, Slot> slots = new HashMap<>();
		Set<Integer> stale = new LinkedHashSet<>();
		List<WireConnection> live = new ArrayList<>(spans.size());
		List<End[]> ends = new ArrayList<>(spans.size());
		for (WireConnection span : spans) {
			End start = resolve(level, slots, span.startBlockPos(), span.startBlockType(), span.startInsulatorId());
			End end = resolve(level, slots, span.endBlockPos(), span.endBlockType(), span.endInsulatorId());

			// every id that points at the wrong thing is cleared of all its spans, not just this one
			if (start.status() == Status.STALE) stale.add(span.startInsulatorId());
			if (end.status() == Status.STALE) stale.add(span.endInsulatorId());

			if (start.status() == Status.ACTIVE && end.status() == Status.ACTIVE) {
				live.add(span);
				ends.add(new End[] {start, end});
			}
		}

		if (!stale.isEmpty()) {
			int[] ids = stale.stream().mapToInt(Integer::intValue).toArray();
			WireLedger.removeAttachedTo(level, ids);

			// the spans just removed are not in the graph either
			for (int i = live.size() - 1; i >= 0; i--) {
				WireConnection span = live.get(i);
				if (stale.contains(span.startInsulatorId()) || stale.contains(span.endInsulatorId())) {
					live.remove(i);
					ends.remove(i);
				}
			}
		}

		// the nodes: one per fitting that a live span uses, in the order they are met
		Map<Integer, GridSolver.Node> nodes = new LinkedHashMap<>();
		Map<Long, InsulatorHost> hosts = new LinkedHashMap<>();
		Map<Long, Integer> firstNode = new HashMap<>();
		List<GridSolver.Edge> edges = new ArrayList<>(live.size());

		for (int i = 0; i < live.size(); i++) {
			WireConnection span = live.get(i);
			End start = ends.get(i)[0];
			End end = ends.get(i)[1];

			addNode(nodes, hosts, firstNode, span.startInsulatorId(), span.startBlockPos(), start);
			addNode(nodes, hosts, firstNode, span.endInsulatorId(), span.endBlockPos(), end);

			// a conductor this build does not know is carried without loss: there is no resistance to give it
			double length = Math.sqrt(span.startBlockPos().distSqr(span.endBlockPos()));
			ConductorSpec conductor = ConductorCatalog.byPath(span.wireType());
			double ohms = conductor == null ? 0.0 : conductor.resistanceOhms(length);
			double volts = conductor == null ? 0.0 : conductor.nominalVolts();
			boolean same = span.startBlockPos().equals(span.endBlockPos());
			boolean forward = FlowRules.allows(start.host().feeds(end.host()), end.host().passesThrough(),
					span.startPowerType(), span.endPowerType(), same);
			boolean backward = FlowRules.allows(end.host().feeds(start.host()), start.host().passesThrough(),
					span.endPowerType(), span.startPowerType(), same);

			edges.add(new GridSolver.Edge(span.startInsulatorId(), span.endInsulatorId(), ohms, volts, forward, backward));
		}

		// what a machine burns carrying power across itself (a transformer's no-load and load losses), and where the
		// power ends: the machines that draw it
		Map<Long, DoubleUnaryOperator> passages = new HashMap<>();
		Set<Long> loads = new HashSet<>();
		for (Map.Entry<Long, InsulatorHost> entry : hosts.entrySet()) {
			InsulatorHost host = entry.getValue();
			if (host.losesInPassing()) passages.put(entry.getKey(), through -> throughLoss(level, entry.getKey(), host, through));
			if (host.isLoad()) loads.add(entry.getKey());
		}

		List<GridSolver.Source> sources = sources(level, hosts, firstNode);
		List<GridSolver.Node> graph = new ArrayList<>(nodes.values());
		GridSolver.Solution solution = network(graph, edges, loads).solve(sources, passages);
		Map<Long, GridSolver.Reading> byPosition = GridSolver.byPosition(graph, solution.readings());

		deliver(level, hosts, byPosition);
	}

	/** The solver for this graph: the one already built if the graph reads exactly as it did, else a new one. */
	private GridSolver network(List<GridSolver.Node> nodes, List<GridSolver.Edge> edges, Set<Long> loads) {
		if (network == null || !nodes.equals(networkNodes) || !edges.equals(networkEdges) || !loads.equals(networkLoads)) {
			network = GridSolver.network(nodes, edges, loads);
			networkNodes = nodes;
			networkEdges = edges;
			networkLoads = loads;
		}

		return network;
	}

	/** A transformer that fails to say what it burns is a broken one, and passes nothing on. */
	private double throughLoss(ServerLevel level, long pos, InsulatorHost host, double throughKw) {
		try {
			return host.throughLossKw(throughKw);
		} catch (RuntimeException e) {
			faults.report(level, "the machine at " + BlockPos.of(pos).toShortString() + " failed to give its losses", e);
			return throughKw;
		}
	}

	private void addNode(Map<Integer, GridSolver.Node> nodes, Map<Long, InsulatorHost> hosts, Map<Long, Integer> firstNode,
			int id, BlockPos pos, End end) {
		if (nodes.containsKey(id)) return;

		long key = pos.asLong();
		nodes.put(id, new GridSolver.Node(id, key, end.host().busOf(end.index())));
		hosts.putIfAbsent(key, end.host());
		firstNode.putIfAbsent(key, id);
	}

	/** Every machine in the graph that is putting power on the network, with the disturbance it is carrying. */
	private List<GridSolver.Source> sources(ServerLevel level, Map<Long, InsulatorHost> hosts, Map<Long, Integer> firstNode) {
		long now = level.getGameTime();
		boolean advance = now != memoryTick;
		Map<Integer, PowerDeliveryEvent> next = advance ? new HashMap<>() : memory;

		List<GridSolver.Source> sources = new ArrayList<>();
		for (Map.Entry<Long, InsulatorHost> entry : hosts.entrySet()) {
			InsulatorHost host = entry.getValue();
			double power;
			WindSample wind;
			try {
				power = host.getGeneratedPower();
				wind = power > 0.0 ? host.windSample() : null;
			} catch (RuntimeException e) {
				faults.report(level, "the machine at " + BlockPos.of(entry.getKey()).toShortString() + " failed to give its output", e);
				continue;
			}

			if (!(power > 0.0)) continue;

			int node = firstNode.get(entry.getKey());
			PowerDeliveryEvent event = PowerDeliveryEvent.NONE;
			if (wind != null) {
				// one draw per game tick: a second calculation in the same tick (a switch moved) must not
				// count the disturbance down twice
				event = advance ? DisturbanceModel.next(memory.get(node), wind.meanSpeed(), wind.turbulence(), level.getRandom()::nextDouble)
						: memory.getOrDefault(node, PowerDeliveryEvent.NONE);
				next.put(node, event);
			}

			sources.add(new GridSolver.Source(node, power, event));
		}

		// a turbine that has left the graph forgets its event; one that is still in it keeps it
		memory = next;
		memoryTick = now;
		return sources;
	}

	private void deliver(ServerLevel level, Map<Long, InsulatorHost> hosts, Map<Long, GridSolver.Reading> byPosition) {
		Set<Long> now = new HashSet<>();
		for (Map.Entry<Long, GridSolver.Reading> entry : byPosition.entrySet()) {
			InsulatorHost host = hosts.get(entry.getKey());
			if (host == null) continue;

			GridSolver.Reading reading = entry.getValue();
			now.add(entry.getKey());
			try {
				host.deliverPower(reading.powerKw());
				host.deliverEvent(reading.event());
			} catch (RuntimeException e) {
				faults.report(level, "the machine at " + BlockPos.of(entry.getKey()).toShortString() + " failed to take its power", e);
				continue;
			}

			publish(level, entry.getKey(), reading.powerKw());
		}

		// positions that were powered and are no longer in the graph are told zero, once the chunk is there to be told
		Set<Long> next = new HashSet<>(now);
		for (long pos : powered) {
			if (now.contains(pos)) continue;

			BlockPos at = BlockPos.of(pos);
			if (!level.isLoaded(at)) {
				next.add(pos);
				continue;
			}

			if (level.getBlockEntity(at) instanceof InsulatorHost host) {
				try {
					host.deliverPower(0.0);
					host.deliverEvent(PowerDeliveryEvent.NONE);
				} catch (RuntimeException e) {
					faults.report(level, "the machine at " + at.toShortString() + " failed to take its power", e);
				}
			}

			if (sent.remove(pos) != null) sendNow(level, at, 0.0);
		}

		powered = next;
	}

	/** Tells the clients a reading, but only if it moved or it is time to say it again. */
	private void publish(ServerLevel level, long pos, double kw) {
		long now = level.getGameTime();
		Sent last = sent.get(pos);
		if (last != null && Math.abs(kw - last.kw()) <= SEND_TOLERANCE_KW && now - last.tick() < HEARTBEAT_TICKS) return;

		sent.put(pos, new Sent(kw, now));
		if (!level.players().isEmpty()) sendNow(level, BlockPos.of(pos), kw);
	}

	private static void sendNow(ServerLevel level, BlockPos pos, double kw) {
		Net.sendToTracking(level, pos, new PowerUpdatePayload(pos, kw));
	}

	private End resolve(ServerLevel level, Map<Long, Slot> slots, BlockPos pos, String type, int id) {
		Slot slot = slots.computeIfAbsent(pos.asLong(), key -> read(level, pos));
		if (!slot.loaded()) return End.DORMANT;

		InsulatorHost host = slot.host();
		if (host == null || !host.fittingType().equals(type)) return End.STALE;

		boolean assigned = false;
		for (int i = 0; i < slot.ids().length; i++) {
			if (slot.ids()[i] > 0) assigned = true;
			if (slot.ids()[i] == id) return new End(Status.ACTIVE, host, i);
		}

		// a machine that has not been given its ids yet is not wrong, just not ready
		return assigned ? End.STALE : End.DORMANT;
	}

	private static Slot read(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) return Slot.UNLOADED;

		BlockEntity entity = level.getBlockEntity(pos);
		if (entity instanceof InsulatorHost host && !entity.isRemoved()) return new Slot(true, host, host.getInsulatorIds());

		return new Slot(true, null, new int[0]);
	}
}
