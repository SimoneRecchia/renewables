package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;

/**
 * Carries power from the generators down the spans to the loads, and says what passes every fitting.
 *
 * Pure arithmetic over plain numbers - no world, no block entity - so every rule of the network can be tested with
 * a handful of nodes.  The caller ({@link LevelGrid}) turns the world into {@link Node}s and {@link Edge}s, and
 * what the machines are allowed to feed is already folded into each edge's two direction flags.
 *
 * <h2>The model</h2>
 * A node is a fitting; an edge is a span.  Every node belongs to a <em>bus</em>, the pair (block position, bus
 * index), and the nodes of one bus form a <em>cluster</em>: power crosses a cluster for free, which is what makes
 * a pole one busbar with eight insulators on it - unless the machine there burns something carrying it, which a
 * transformer does ({@code passages}).  A switch has two buses at its position, its line side (0) and its load side
 * (1): closed, its contacts join them with a way of no resistance; open, nothing does, and that is all it takes for
 * power to stop.  What a switch reads is the power crossing its contacts, not what passes one of its sides: its
 * terminals may be a junction for a line that never goes through it.
 *
 * <h2>Where power goes</h2>
 * Power flows to <em>loads</em> (the power boxes) and nowhere else: a line with no load at its end carries no
 * current, and a branch that leads to none takes nothing.  Every load is taken to draw alike, so a generator's
 * output is shared equally between the loads it can reach.  From the generator the flow is pushed outwards over
 * the spans in the order a shortest-path search (on the loss coefficient) first reaches each bus, which makes the
 * network a one-way graph away from that generator; at each bus what arrives is the sum of everything that came in,
 * and what leaves is split between the ways on in proportion to the loads still reachable through each.  On a
 * radial network - a line, a tree - that is exactly the current equal loads would draw; on a mesh it is a fair,
 * symmetric approximation of Kirchhoff, and it is still conservative: what reaches the loads plus what was lost on
 * the way is never more than what was generated.
 *
 * <h2>Losses</h2>
 * Each way loses its I²R ({@link GridMath#keptFraction}).  Up to three spans between the same two buses are one
 * three-phase circuit, so stringing the three phases loses no less than stringing one; every further three is a
 * circuit in parallel ({@link #circuitCoefficient}).  A loss goes with the square of the load, so generators sharing a
 * line lose that of their sum: each works out a line's loss from its own flow plus what the others put on it in
 * the previous pass, and the solve runs {@link #PASSES} times.  A single generator is exact in one.
 *
 * Each bus is visited once per generator, so the cost is a shortest-path search per generator and nothing in a mesh
 * can exhaust a budget or a depth limit.
 */
final class GridSolver {
	/** Passes over the generators; the second and third bring in what the others put on each shared line. */
	static final int PASSES = 3;

	/**
	 * A fitting that a span is attached to.
	 *
	 * @param id       its insulator id
	 * @param position the machine's block position, packed
	 * @param bus      the bus within the machine (always 0 except on an open switch)
	 */
	record Node(int id, long position, int bus) {
	}

	/**
	 * A span between two nodes: one phase conductor.
	 *
	 * @param ohms  that conductor's resistance over the whole span, bundle and all
	 * @param volts the line-to-line voltage the span is worked at
	 * @param aToB  whether power may go from {@code a} to {@code b}
	 * @param bToA  the other way
	 */
	record Edge(int a, int b, double ohms, double volts, boolean aToB, boolean bToA) {
	}

	/** A generator's entry into the network: the node it feeds, what it makes (kW) and the disturbance riding on it. */
	record Source(int node, double powerKw, PowerDeliveryEvent event) {
	}

	/** What reaches a fitting. */
	record Reading(double powerKw, PowerDeliveryEvent event) {
		static final Reading NOTHING = new Reading(0.0, PowerDeliveryEvent.NONE);
	}

	/** The spans from one bus to another, in one allowed direction, as one equivalent line. */
	private record Way(int to, double k) {
	}

	private final int[] nodeCluster;
	private final int nodeCount;
	private final int clusterCount;
	/** Per cluster, the ways leaving it, one per destination bus. */
	private final Way[][] ways;
	/** Per cluster, what the machine there burns for a through flow, or null if it burns nothing. */
	private final DoubleUnaryOperator[] clusterLoss;
	private final boolean[] clusterIsLoad;
	private final Map<Integer, Integer> indexOfId = new HashMap<>();
	private final int[] nodeIds;
	/** The position of every node's machine, packed. */
	private final long[] nodePosition;
	/** By switch position, its line-side and load-side clusters (-1 where that side has no node). */
	private final Map<Long, int[]> switchSides = new HashMap<>();

	private GridSolver(Collection<Node> nodes, Collection<Edge> edges, Map<Long, DoubleUnaryOperator> passages, Set<Long> loads,
			Map<Long, Boolean> switches) {
		nodeCount = nodes.size();
		nodeIds = new int[nodeCount];
		nodeCluster = new int[nodeCount];
		nodePosition = new long[nodeCount];

		Map<Long, Map<Integer, Integer>> clusterOf = new HashMap<>();
		List<Long> clusterPosition = new ArrayList<>();
		int index = 0;
		for (Node node : nodes) {
			indexOfId.put(node.id(), index);
			nodeIds[index] = node.id();
			nodePosition[index] = node.position();

			Map<Integer, Integer> buses = clusterOf.computeIfAbsent(node.position(), key -> new HashMap<>());
			Integer cluster = buses.get(node.bus());
			if (cluster == null) {
				cluster = clusterPosition.size();
				buses.put(node.bus(), cluster);
				clusterPosition.add(node.position());
			}

			nodeCluster[index] = cluster;
			index++;
		}

		clusterCount = clusterPosition.size();
		clusterLoss = new DoubleUnaryOperator[clusterCount];
		clusterIsLoad = new boolean[clusterCount];
		for (int c = 0; c < clusterCount; c++) {
			clusterLoss[c] = passages.get(clusterPosition.get(c));
			clusterIsLoad[c] = loads.contains(clusterPosition.get(c));
		}

		// the spans of each direction between two buses, gathered so they can be seen as circuits
		List<Map<Integer, List<Double>>> spans = new ArrayList<>(clusterCount);
		for (int c = 0; c < clusterCount; c++) {
			spans.add(new LinkedHashMap<>());
		}

		for (Edge edge : edges) {
			Integer a = indexOfId.get(edge.a());
			Integer b = indexOfId.get(edge.b());
			if (a == null || b == null) continue;

			int from = nodeCluster[a];
			int to = nodeCluster[b];
			if (from == to) continue;

			double k = GridMath.lossCoefficient(edge.ohms(), edge.volts());
			if (edge.aToB()) spans.get(from).computeIfAbsent(to, key -> new ArrayList<>()).add(k);
			if (edge.bToA()) spans.get(to).computeIfAbsent(from, key -> new ArrayList<>()).add(k);
		}

		// a closed switch's contacts: a way of no resistance each way between its two sides
		for (Map.Entry<Long, Boolean> entry : switches.entrySet()) {
			Map<Integer, Integer> buses = clusterOf.getOrDefault(entry.getKey(), Map.of());
			int line = buses.getOrDefault(0, -1);
			int load = buses.getOrDefault(1, -1);
			switchSides.put(entry.getKey(), new int[] {line, load});
			if (!entry.getValue() || line < 0 || load < 0) continue;

			spans.get(line).computeIfAbsent(load, key -> new ArrayList<>()).add(0.0);
			spans.get(load).computeIfAbsent(line, key -> new ArrayList<>()).add(0.0);
		}

		ways = new Way[clusterCount][];
		for (int c = 0; c < clusterCount; c++) {
			ways[c] = spans.get(c).entrySet().stream()
					.map(entry -> new Way(entry.getKey(), circuitCoefficient(entry.getValue())))
					.toArray(Way[]::new);
		}
	}

	/**
	 * The loss coefficient of the spans strung between two buses.  Every three of them are one three-phase circuit -
	 * three phases are not three cables in parallel, all three are needed to carry the power - and the circuits are in
	 * parallel: {@code k = n / (ceil(n/3) sum(1/k_i))}.  One span stands for a whole circuit, as the mod draws a line;
	 * a lossless span makes the whole group lossless.
	 */
	static double circuitCoefficient(List<Double> coefficients) {
		double conductance = 0.0;
		for (double k : coefficients) {
			if (!(k > 0.0)) return 0.0;

			conductance += 1.0 / k;
		}

		int spans = coefficients.size();
		int circuits = (spans + 2) / 3;
		return spans / (circuits * conductance);
	}

	/** Solves a network in which nothing but the spans loses power and every machine in {@code loads} draws. */
	static Map<Integer, Reading> solve(Collection<Node> nodes, Collection<Edge> edges, Collection<Source> sources, Set<Long> loads) {
		return solve(nodes, edges, sources, Map.of(), loads, Map.of());
	}

	/** Solves a network with no switches in it. */
	static Map<Integer, Reading> solve(Collection<Node> nodes, Collection<Edge> edges, Collection<Source> sources,
			Map<Long, DoubleUnaryOperator> passages, Set<Long> loads) {
		return solve(nodes, edges, sources, passages, loads, Map.of());
	}

	/**
	 * Solves a network.
	 *
	 * @param passages by block position, what the machine there burns (kW) for the flow (kW) crossing it
	 * @param loads    the block positions of the machines that draw power: where the flow ends
	 * @param switches by block position, every switch and whether it is closed; its nodes are on bus 0 (line side)
	 *                 and bus 1 (load side)
	 * @return for every node that was given, the power passing it (at a generator, what it makes; at a load, what it
	 * receives; at a switch, what crosses its contacts); a node no flow passes reads zero
	 */
	static Map<Integer, Reading> solve(Collection<Node> nodes, Collection<Edge> edges, Collection<Source> sources,
			Map<Long, DoubleUnaryOperator> passages, Set<Long> loads, Map<Long, Boolean> switches) {
		GridSolver solver = new GridSolver(nodes, edges, passages, loads, switches);

		List<Plan> plans = new ArrayList<>();
		for (Source source : sources) {
			Integer start = solver.indexOfId.get(source.node());
			if (start == null || !(source.powerKw() > 0.0)) continue;

			plans.add(solver.plan(solver.nodeCluster[start], source));
		}

		double[] power = new double[solver.clusterCount];
		PowerDeliveryEvent[] events = new PowerDeliveryEvent[solver.clusterCount];
		Flows[] previous = new Flows[plans.size()];
		Flows total = null;
		Flows[] current = new Flows[0];

		for (int pass = 0; pass < PASSES; pass++) {
			power = new double[solver.clusterCount];
			Arrays.fill(events, PowerDeliveryEvent.NONE);
			current = new Flows[plans.size()];

			for (int s = 0; s < plans.size(); s++) {
				current[s] = solver.push(plans.get(s), previous[s], total, power, events);
			}

			// one generator shares its lines with nobody, so its first pass is already the answer
			if (plans.size() <= 1) break;

			previous = current;
			total = Flows.sum(current, solver.clusterCount);
		}

		Flows last = Flows.sum(current, solver.clusterCount);
		Map<Integer, Reading> result = new LinkedHashMap<>();
		for (int i = 0; i < solver.nodeCount; i++) {
			int c = solver.nodeCluster[i];
			int[] sides = solver.switchSides.get(solver.nodePosition[i]);
			result.put(solver.nodeIds[i], sides == null ? new Reading(power[c], events[c]) : solver.crossing(sides, last, events));
		}

		return result;
	}

	/**
	 * What a switch reads: the power across its contacts, line to load less load to line, with the disturbance of
	 * either side.  Nothing crosses an open switch, or one with a side that nothing is strung to.
	 */
	private Reading crossing(int[] sides, Flows flows, PowerDeliveryEvent[] events) {
		int line = sides[0];
		int load = sides[1];
		if (line < 0 || load < 0) return Reading.NOTHING;

		double across = Math.abs(flows.sent(way(line, load)) - flows.sent(way(load, line)));
		return new Reading(across, across > 0.0 ? GridMath.merge(events[line], events[load]) : PowerDeliveryEvent.NONE);
	}

	/** A way's key in {@link Flows#sent}: the two clusters packed. */
	private static long way(int from, int to) {
		return ((long) from << 32) | (to & 0xFFFFFFFFL);
	}

	/**
	 * Folds node readings into one per machine: the largest power of its nodes (a machine on two buses reads the
	 * better of them) and the worst of their disturbances.
	 */
	static Map<Long, Reading> byPosition(Collection<Node> nodes, Map<Integer, Reading> readings) {
		Map<Long, Reading> result = new LinkedHashMap<>();
		for (Node node : nodes) {
			Reading reading = readings.getOrDefault(node.id(), Reading.NOTHING);
			result.merge(node.position(), reading, (x, y) -> new Reading(Math.max(x.powerKw(), y.powerKw()),
					GridMath.merge(x.event(), y.event())));
		}

		return result;
	}

	// ---- one generator ----

	/**
	 * What does not change between passes for one generator: the order its flow reaches each bus in, which way every
	 * span is used, and which loads lie beyond each bus.
	 */
	private record Plan(Source source, int start, int[] order, int[] rank, BitSet[] reach) {
	}

	/** Orders the buses a generator reaches by a shortest-path search on the loss coefficient, and finds its loads. */
	private Plan plan(int start, Source source) {
		double[] distance = new double[clusterCount];
		Arrays.fill(distance, Double.POSITIVE_INFINITY);
		int[] rank = new int[clusterCount];
		Arrays.fill(rank, -1);
		List<Integer> order = new ArrayList<>();

		// ties go to the lower index, so the same network always gives the same answer
		PriorityQueue<double[]> queue = new PriorityQueue<>((x, y) -> x[0] != y[0] ? Double.compare(x[0], y[0]) : Double.compare(x[1], y[1]));
		distance[start] = 0.0;
		queue.add(new double[] {0.0, start});
		while (!queue.isEmpty()) {
			double[] head = queue.poll();
			int u = (int) head[1];
			if (rank[u] >= 0 || head[0] > distance[u]) continue;

			rank[u] = order.size();
			order.add(u);
			// a load draws what reaches it and sends nothing on
			if (clusterIsLoad[u] && u != start) continue;

			for (Way way : ways[u]) {
				double d = distance[u] + way.k();
				if (rank[way.to()] < 0 && d < distance[way.to()]) {
					distance[way.to()] = d;
					queue.add(new double[] {d, way.to()});
				}
			}
		}

		// the loads beyond each bus, along ways that lead away from the generator
		int[] sequence = order.stream().mapToInt(Integer::intValue).toArray();
		BitSet[] reach = new BitSet[clusterCount];
		for (int i = sequence.length - 1; i >= 0; i--) {
			int u = sequence[i];
			BitSet beyond = new BitSet();
			if (clusterIsLoad[u] && u != start) {
				beyond.set(u);
			} else {
				for (Way way : ways[u]) {
					if (downstream(rank, u, way.to())) beyond.or(reach[way.to()]);
				}
			}

			reach[u] = beyond;
		}

		return new Plan(source, start, sequence, rank, reach);
	}

	private static boolean downstream(int[] rank, int from, int to) {
		return rank[to] > rank[from];
	}

	/** Pushes one generator's power out to its loads, adding what passes each bus to {@code power}. */
	private Flows push(Plan plan, Flows ownBefore, Flows allBefore, double[] power, PowerDeliveryEvent[] events) {
		Flows flows = new Flows(clusterCount);
		double[] inflow = new double[clusterCount];
		PowerDeliveryEvent[] carried = new PowerDeliveryEvent[clusterCount];
		Arrays.fill(carried, PowerDeliveryEvent.NONE);
		inflow[plan.start()] = plan.source().powerKw();
		carried[plan.start()] = plan.source().event();

		for (int u : plan.order()) {
			double arrived = inflow[u];
			if (!(arrived > 0.0)) continue;

			power[u] += arrived;
			events[u] = GridMath.merge(events[u], carried[u]);
			flows.arrived[u] = arrived;

			BitSet loadsBeyond = plan.reach()[u];
			if (loadsBeyond.isEmpty() || (clusterIsLoad[u] && u != plan.start())) continue;

			// a transformer reads what goes in and passes on what is left after its core and windings
			double onward = arrived;
			DoubleUnaryOperator burns = clusterLoss[u];
			if (burns != null) {
				double through = arrived + others(ownBefore, allBefore, u);
				onward = arrived * GridMath.keptThrough(through, burns.applyAsDouble(through));
			}

			if (!(onward > 0.0)) continue;

			// every load beyond draws alike, so each way on takes the share of the loads it leads to
			int share = 0;
			for (Way way : ways[u]) {
				if (downstream(plan.rank(), u, way.to())) share += plan.reach()[way.to()].cardinality();
			}

			for (Way way : ways[u]) {
				if (!downstream(plan.rank(), u, way.to())) continue;

				int beyond = plan.reach()[way.to()].cardinality();
				if (beyond == 0) continue;

				double sent = onward * beyond / share;
				long key = way(u, way.to());
				flows.sent.put(key, sent);

				double eta = GridMath.keptFraction(sent + others(ownBefore, allBefore, key), way.k());
				inflow[way.to()] += sent * eta;
				carried[way.to()] = GridMath.merge(carried[way.to()], GridMath.attenuate(carried[u], eta));
			}
		}

		return flows;
	}

	/** What the other generators sent down a way last pass. */
	private static double others(Flows ownBefore, Flows allBefore, long way) {
		if (allBefore == null || ownBefore == null) return 0.0;

		return Math.max(0.0, allBefore.sent(way) - ownBefore.sent(way));
	}

	/** What the other generators brought into a bus last pass. */
	private static double others(Flows ownBefore, Flows allBefore, int cluster) {
		if (allBefore == null || ownBefore == null) return 0.0;

		return Math.max(0.0, allBefore.arrived[cluster] - ownBefore.arrived[cluster]);
	}

	/** What one generator (or all of them, summed) put down each way and into each bus in one pass. */
	private static final class Flows {
		/** By way, packed {@code from << 32 | to} over cluster indices. */
		final Map<Long, Double> sent = new HashMap<>();
		/** By cluster. */
		final double[] arrived;

		Flows(int clusters) {
			arrived = new double[clusters];
		}

		double sent(long way) {
			return sent.getOrDefault(way, 0.0);
		}

		static Flows sum(Flows[] each, int clusters) {
			Flows total = new Flows(clusters);
			for (Flows flows : each) {
				flows.sent.forEach((way, kw) -> total.sent.merge(way, kw, Double::sum));
				for (int c = 0; c < clusters; c++) {
					total.arrived[c] += flows.arrived[c];
				}
			}

			return total;
		}
	}
}
