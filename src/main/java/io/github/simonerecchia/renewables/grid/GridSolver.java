package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * Carries power from the generators down the spans, and says what reaches every fitting.
 *
 * Pure arithmetic over plain numbers - no world, no block entity - so every rule of the network can be tested with
 * a handful of nodes.  The caller ({@link LevelGrid}) turns the world into {@link Node}s and {@link Edge}s, and
 * what the machines are allowed to feed is already folded into each edge's two direction flags.
 *
 * <h2>The model</h2>
 * A node is a fitting; an edge is a span.  Every node belongs to a <em>bus</em>, the pair (block position, bus
 * index), and the nodes of one bus form a <em>cluster</em>: power crosses a cluster for free, which is what makes
 * a pole one busbar with eight insulators on it - unless the machine at that position burns something carrying
 * it, which a transformer does ({@link #solve(Collection, Collection, Collection, Map) passages}).  A switch that is
 * open puts its two sides on different buses at the same position, and that is all it takes for power to stop.
 *
 * <h2>The walk</h2>
 * From a generator's cluster the flow F spreads along every edge that leaves the cluster in an allowed
 * direction.  The edges are grouped by the bus they arrive at, F is split equally between the groups that do not
 * lead back to a cluster already on this path, and each group delivers its share less its I²R loss
 * ({@link GridMath#keptFraction}).  The spans of one group are in parallel, so the power divides between them as
 * their conductances do and the group's coefficient is {@code 1 / sum(1/k)}.  A node reached along several paths
 * of one generator keeps the largest flow (the same power is not counted twice); nodes reached from several
 * generators add.
 *
 * <h2>Generators sharing a line</h2>
 * A loss goes with the square of the load, so two generators down one line lose more together than each would
 * alone.  Each walk therefore works out a line's loss from its own flow plus what every other generator sent down
 * that line in the previous pass, and the solve runs {@link #PASSES} times.  A single generator is exact in one.
 *
 * Meshes can have a great many simple paths, so a walk is bounded in depth and in number of cluster visits, past
 * which it stops spreading in that branch.  A tree or a line is never near either limit.
 */
final class GridSolver {
	static final int MAX_DEPTH = 64;
	static final int MAX_VISITS = 10_000;
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
	 * A span between two nodes.
	 *
	 * @param ohms  one phase's resistance over the whole span, bundle and all
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

	private final int[] nodeCluster;
	private final int[][] clusterNodes;
	private final int nodeCount;
	/** Per node, the edges leaving it in an allowed direction. */
	private final List<int[]>[] outgoing;
	/** Per edge, k in {@code loss = k P^2}. */
	private final double[] edgeCoefficient;
	/** Per cluster, what the machine there burns for a through flow, or null if it burns nothing. */
	private final DoubleUnaryOperator[] clusterLoss;
	private final Map<Integer, Integer> indexOfId = new HashMap<>();
	private final int[] nodeIds;

	private GridSolver(Collection<Node> nodes, Collection<Edge> edges, Map<Long, DoubleUnaryOperator> passages) {
		nodeCount = nodes.size();
		nodeIds = new int[nodeCount];
		nodeCluster = new int[nodeCount];

		Map<Long, Map<Integer, Integer>> clusterOf = new HashMap<>();
		List<List<Integer>> members = new ArrayList<>();
		List<Long> clusterPosition = new ArrayList<>();
		int index = 0;
		for (Node node : nodes) {
			indexOfId.put(node.id(), index);
			nodeIds[index] = node.id();

			Map<Integer, Integer> buses = clusterOf.computeIfAbsent(node.position(), key -> new HashMap<>());
			Integer cluster = buses.get(node.bus());
			if (cluster == null) {
				cluster = members.size();
				buses.put(node.bus(), cluster);
				members.add(new ArrayList<>());
				clusterPosition.add(node.position());
			}

			members.get(cluster).add(index);
			nodeCluster[index] = cluster;
			index++;
		}

		clusterNodes = new int[members.size()][];
		clusterLoss = new DoubleUnaryOperator[members.size()];
		for (int c = 0; c < members.size(); c++) {
			clusterNodes[c] = members.get(c).stream().mapToInt(Integer::intValue).toArray();
			clusterLoss[c] = passages.get(clusterPosition.get(c));
		}

		@SuppressWarnings("unchecked")
		List<int[]>[] lists = new List[nodeCount];
		for (int i = 0; i < nodeCount; i++) {
			lists[i] = new ArrayList<>(2);
		}

		outgoing = lists;
		edgeCoefficient = new double[edges.size()];
		int e = 0;
		for (Edge edge : edges) {
			Integer a = indexOfId.get(edge.a());
			Integer b = indexOfId.get(edge.b());
			edgeCoefficient[e] = GridMath.lossCoefficient(edge.ohms(), edge.volts());

			if (a != null && b != null) {
				// {edge, destination node}: the walk only ever needs to know where an allowed direction leads
				if (edge.aToB()) outgoing[a].add(new int[] {e, b});
				if (edge.bToA()) outgoing[b].add(new int[] {e, a});
			}

			e++;
		}
	}

	/** Solves a network in which nothing but the spans loses power. */
	static Map<Integer, Reading> solve(Collection<Node> nodes, Collection<Edge> edges, Collection<Source> sources) {
		return solve(nodes, edges, sources, Map.of());
	}

	/**
	 * Solves a network.
	 *
	 * @param passages by block position, what the machine there burns (kW) for the flow (kW) crossing it
	 * @return for every node that was given, what reaches it; a node nothing reaches reads zero
	 */
	static Map<Integer, Reading> solve(Collection<Node> nodes, Collection<Edge> edges, Collection<Source> sources,
			Map<Long, DoubleUnaryOperator> passages) {
		GridSolver solver = new GridSolver(nodes, edges, passages);

		List<Integer> starts = new ArrayList<>();
		List<Source> live = new ArrayList<>();
		for (Source source : sources) {
			Integer start = solver.indexOfId.get(source.node());
			if (start == null || !(source.powerKw() > 0.0)) continue;

			starts.add(start);
			live.add(source);
		}

		double[] power = new double[solver.nodeCount];
		PowerDeliveryEvent[] events = new PowerDeliveryEvent[solver.nodeCount];
		Flows[] previous = new Flows[live.size()];
		Flows total = null;

		for (int pass = 0; pass < PASSES; pass++) {
			power = new double[solver.nodeCount];
			events = new PowerDeliveryEvent[solver.nodeCount];
			Arrays.fill(events, PowerDeliveryEvent.NONE);
			Flows[] current = new Flows[live.size()];

			for (int s = 0; s < live.size(); s++) {
				Source source = live.get(s);
				Walk walk = solver.new Walk(previous[s], total);
				walk.visit(solver.nodeCluster[starts.get(s)], source.powerKw(), source.event(), 0);
				current[s] = walk.flows;

				// one generator's paths agree on the maximum; different generators add
				for (int i = 0; i < solver.nodeCount; i++) {
					if (walk.best[i] <= 0.0) continue;

					power[i] += walk.best[i];
					events[i] = GridMath.merge(events[i], walk.events[i]);
				}
			}

			// one generator shares its lines with nobody, so its first pass is already the answer
			if (live.size() <= 1) break;

			previous = current;
			total = Flows.sum(current, solver.clusterNodes.length);
		}

		Map<Integer, Reading> result = new LinkedHashMap<>();
		for (int i = 0; i < solver.nodeCount; i++) {
			result.put(solver.nodeIds[i], new Reading(power[i], events[i]));
		}

		return result;
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

	/** What one generator (or all of them, summed) put down each way and into each cluster in one pass. */
	private static final class Flows {
		/** By way, packed {@code from << 32 | to} over cluster indices: the largest flow sent along it. */
		final Map<Long, Double> sent = new HashMap<>();
		/** By cluster: the largest flow that arrived there. */
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

	/** One generator's walk: the best flow and the disturbance each node has seen, and the cluster path in progress. */
	private final class Walk {
		final double[] best = new double[nodeCount];
		final PowerDeliveryEvent[] events = new PowerDeliveryEvent[nodeCount];
		final Flows flows = new Flows(clusterNodes.length);
		private final boolean[] onPath = new boolean[clusterNodes.length];
		/** This generator's own flows last pass, and everybody's: the difference is what the others add. */
		private final Flows ownBefore;
		private final Flows allBefore;
		private int visits;

		Walk(Flows ownBefore, Flows allBefore) {
			this.ownBefore = ownBefore;
			this.allBefore = allBefore;
			Arrays.fill(events, PowerDeliveryEvent.NONE);
		}

		/** What the other generators sent down a way last pass. */
		private double othersOn(long way) {
			if (allBefore == null || ownBefore == null) return 0.0;

			return Math.max(0.0, allBefore.sent(way) - ownBefore.sent(way));
		}

		/** What the other generators brought into a cluster last pass. */
		private double othersInto(int cluster) {
			if (allBefore == null || ownBefore == null) return 0.0;

			return Math.max(0.0, allBefore.arrived[cluster] - ownBefore.arrived[cluster]);
		}

		/** Arrives at a cluster with flow {@code flow}, and fans out from it. */
		void visit(int cluster, double flow, PowerDeliveryEvent event, int depth) {
			if (++visits > MAX_VISITS) return;

			for (int node : clusterNodes[cluster]) {
				best[node] = Math.max(best[node], flow);
				events[node] = GridMath.merge(events[node], event);
			}

			flows.arrived[cluster] = Math.max(flows.arrived[cluster], flow);
			if (depth >= MAX_DEPTH) return;

			// a transformer reads what goes in and passes on what is left after its core and windings
			double onward = flow;
			DoubleUnaryOperator burns = clusterLoss[cluster];
			if (burns != null) {
				double through = flow + othersInto(cluster);
				onward = flow * GridMath.keptThrough(through, burns.applyAsDouble(through));
			}

			if (!(onward > 0.0)) return;

			// the groups of edges that arrive at one and the same bus, in the order they were found: the sum of
			// their conductances 1/k, or infinity when one of them is lossless and takes the whole flow
			Map<Integer, double[]> groups = new LinkedHashMap<>();
			onPath[cluster] = true;
			for (int node : clusterNodes[cluster]) {
				for (int[] edge : outgoing[node]) {
					int destination = nodeCluster[edge[1]];
					if (destination == cluster || onPath[destination]) continue;

					double[] group = groups.computeIfAbsent(destination, key -> new double[1]);
					double k = edgeCoefficient[edge[0]];
					group[0] += k > 0.0 ? 1.0 / k : Double.POSITIVE_INFINITY;
				}
			}

			if (!groups.isEmpty()) {
				// an equal share to every way on, whatever is or is not at the end of it
				double share = onward / groups.size();
				for (Map.Entry<Integer, double[]> entry : groups.entrySet()) {
					int destination = entry.getKey();
					double conductance = entry.getValue()[0];
					double k = Double.isInfinite(conductance) ? 0.0 : 1.0 / conductance;

					long way = ((long) cluster << 32) | (destination & 0xFFFFFFFFL);
					flows.sent.merge(way, share, Math::max);

					double eta = GridMath.keptFraction(share + othersOn(way), k);
					double delivered = share * eta;
					if (!(delivered > 0.0)) continue;

					visit(destination, delivered, GridMath.attenuate(event, eta), depth + 1);
				}
			}

			onPath[cluster] = false;
		}
	}
}
