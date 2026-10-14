package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * a pole one busbar with eight insulators on it.  A switch that is open puts its two sides on different buses at
 * the same position, and that is all it takes for power to stop.
 *
 * <h2>The walk</h2>
 * From a generator's cluster the flow F spreads along every edge that leaves the cluster in an allowed
 * direction.  The edges are grouped by the bus they arrive at, F is split equally between the groups that do not
 * lead back to a cluster already on this path, and each group delivers its share times the efficiency of its
 * cables ({@link GridMath#efficiency}).  A node reached along several paths of one generator keeps the largest
 * flow (the same power is not counted twice); nodes reached from several generators add.
 *
 * Meshes can have a great many simple paths, so a walk is bounded in depth and in number of cluster visits, past
 * which it stops spreading in that branch.  A tree or a line is never near either limit.
 */
final class GridSolver {
	static final int MAX_DEPTH = 64;
	static final int MAX_VISITS = 10_000;

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
	 * @param length the distance between the machines' block positions, in blocks
	 * @param aToB   whether power may go from {@code a} to {@code b}
	 * @param bToA   the other way
	 */
	record Edge(int a, int b, double length, boolean aToB, boolean bToA) {
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
	private final double[] edgeLength;
	private final Map<Integer, Integer> indexOfId = new HashMap<>();
	private final int[] nodeIds;

	private GridSolver(Collection<Node> nodes, Collection<Edge> edges) {
		nodeCount = nodes.size();
		nodeIds = new int[nodeCount];
		nodeCluster = new int[nodeCount];

		Map<Long, Map<Integer, Integer>> clusterOf = new HashMap<>();
		List<List<Integer>> members = new ArrayList<>();
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
			}

			members.get(cluster).add(index);
			nodeCluster[index] = cluster;
			index++;
		}

		clusterNodes = new int[members.size()][];
		for (int c = 0; c < members.size(); c++) {
			clusterNodes[c] = members.get(c).stream().mapToInt(Integer::intValue).toArray();
		}

		@SuppressWarnings("unchecked")
		List<int[]>[] lists = new List[nodeCount];
		for (int i = 0; i < nodeCount; i++) {
			lists[i] = new ArrayList<>(2);
		}

		outgoing = lists;
		edgeLength = new double[edges.size()];
		int e = 0;
		for (Edge edge : edges) {
			Integer a = indexOfId.get(edge.a());
			Integer b = indexOfId.get(edge.b());
			edgeLength[e] = edge.length();

			if (a != null && b != null) {
				// {edge, destination node}: the walk only ever needs to know where an allowed direction leads
				if (edge.aToB()) outgoing[a].add(new int[] {e, b});
				if (edge.bToA()) outgoing[b].add(new int[] {e, a});
			}

			e++;
		}
	}

	/**
	 * Solves a network.
	 *
	 * @return for every node that was given, what reaches it; a node nothing reaches reads zero
	 */
	static Map<Integer, Reading> solve(Collection<Node> nodes, Collection<Edge> edges, Collection<Source> sources) {
		GridSolver solver = new GridSolver(nodes, edges);
		double[] power = new double[solver.nodeCount];
		PowerDeliveryEvent[] events = new PowerDeliveryEvent[solver.nodeCount];
		java.util.Arrays.fill(events, PowerDeliveryEvent.NONE);

		for (Source source : sources) {
			Integer start = solver.indexOfId.get(source.node());
			if (start == null || !(source.powerKw() > 0.0)) continue;

			Walk walk = solver.new Walk();
			walk.visit(solver.nodeCluster[start], source.powerKw(), source.event(), 0);

			// one generator's paths agree on the maximum; different generators add
			for (int i = 0; i < solver.nodeCount; i++) {
				if (walk.best[i] <= 0.0) continue;

				power[i] += walk.best[i];
				events[i] = GridMath.merge(events[i], walk.events[i]);
			}
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

	/** One generator's walk: the best flow and the disturbance each node has seen, and the cluster path in progress. */
	private final class Walk {
		final double[] best = new double[nodeCount];
		final PowerDeliveryEvent[] events = new PowerDeliveryEvent[nodeCount];
		private final boolean[] onPath = new boolean[clusterNodes.length];
		private int visits;

		Walk() {
			java.util.Arrays.fill(events, PowerDeliveryEvent.NONE);
		}

		/** Arrives at a cluster with flow {@code flow}, and fans out from it. */
		void visit(int cluster, double flow, PowerDeliveryEvent event, int depth) {
			if (++visits > MAX_VISITS) return;

			for (int node : clusterNodes[cluster]) {
				best[node] = Math.max(best[node], flow);
				events[node] = GridMath.merge(events[node], event);
			}

			if (depth >= MAX_DEPTH) return;

			// the groups of edges that arrive at one and the same bus, in the order they were found
			Map<Integer, double[]> groups = new LinkedHashMap<>();
			onPath[cluster] = true;
			for (int node : clusterNodes[cluster]) {
				for (int[] edge : outgoing[node]) {
					int destination = nodeCluster[edge[1]];
					if (destination == cluster || onPath[destination]) continue;

					double[] group = groups.computeIfAbsent(destination, key -> new double[2]);
					group[0] += 1.0;
					group[1] += edgeLength[edge[0]];
				}
			}

			if (!groups.isEmpty()) {
				// an equal share to every way on, whatever is or is not at the end of it
				double share = flow / groups.size();
				for (Map.Entry<Integer, double[]> entry : groups.entrySet()) {
					double cables = entry.getValue()[0];
					double eta = GridMath.efficiency((int) cables, entry.getValue()[1] / cables);
					double delivered = share * eta;
					if (!(delivered > 0.0)) continue;

					visit(entry.getKey(), delivered, GridMath.attenuate(event, eta), depth + 1);
				}
			}

			onPath[cluster] = false;
		}
	}
}
