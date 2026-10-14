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
 * <h2>Cost</h2>
 * Everything that does not depend on the power - the clusters, the ways, and for each generator the order its flow
 * reaches the buses in and the share each way takes - is worked out once for a network ({@link #network}) and kept
 * by the caller for as long as the spans, the switches' contacts and the loads stay as they are; a tick then only
 * pushes the powers through ({@link #solve(Collection, Map)}), which is linear in the buses and ways each generator
 * reaches.
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

	/**
	 * What one solve gives.
	 *
	 * @param readings by node id, what passes it
	 * @param drawn    by the node of each generator given, what the network took from it (kW): all of its power when
	 *                 a load can be reached from it, none when none can, so a generator's budget is spent only on
	 *                 power that went somewhere
	 */
	record Solution(Map<Integer, Reading> readings, Map<Integer, Double> drawn) {
	}

	private final int nodeCount;
	private final int[] nodeIds;
	/** The position of every node's machine, packed. */
	private final long[] nodePosition;
	/**
	 * By switch position, its contacts: the way line side to load side, the way back (-1 where there is none: open, or
	 * a side nothing is strung to), and the two sides' clusters (-1 for a side with no node).
	 */
	private final Map<Long, int[]> contacts = new HashMap<>();
	private final int[] nodeCluster;
	private final Map<Integer, Integer> indexOfId = new HashMap<>();
	private final int clusterCount;
	private final long[] clusterPosition;
	private final boolean[] clusterIsLoad;
	/** Per cluster, its index among the loads, or -1: the reach of a bus is a set of loads, not of buses. */
	private final int[] loadIndex;
	/** The ways leaving cluster c are {@code wayTo/wayK[wayStart[c] .. wayStart[c + 1])}, one per destination bus. */
	private final int[] wayStart;
	private final int[] wayTo;
	private final double[] wayK;
	/** Per start cluster, what does not change from one tick to the next for a generator there. */
	private final Map<Integer, Plan> plans = new HashMap<>();

	// scratch, kept so that a tick allocates nothing it does not have to
	private final double[] inflow;
	/** The disturbance on its way into each bus, and the one that reached it, unboxed. */
	private final Disturbances carried;
	private final Disturbances reached;
	private final double[] totalSent;
	private final double[] totalArrived;
	private double[][] sentNow = new double[0][];
	private double[][] arrivedNow = new double[0][];
	private double[][] sentBefore = new double[0][];
	private double[][] arrivedBefore = new double[0][];

	private GridSolver(Collection<Node> nodes, Collection<Edge> edges, Set<Long> loads, Map<Long, Boolean> switches) {
		nodeCount = nodes.size();
		nodeIds = new int[nodeCount];
		nodeCluster = new int[nodeCount];
		nodePosition = new long[nodeCount];

		Map<Long, Map<Integer, Integer>> clusterOf = new HashMap<>();
		List<Long> positions = new ArrayList<>();
		int index = 0;
		for (Node node : nodes) {
			indexOfId.put(node.id(), index);
			nodeIds[index] = node.id();
			nodePosition[index] = node.position();

			Map<Integer, Integer> buses = clusterOf.computeIfAbsent(node.position(), key -> new HashMap<>());
			Integer cluster = buses.get(node.bus());
			if (cluster == null) {
				cluster = positions.size();
				buses.put(node.bus(), cluster);
				positions.add(node.position());
			}

			nodeCluster[index] = cluster;
			index++;
		}

		clusterCount = positions.size();
		clusterPosition = new long[clusterCount];
		clusterIsLoad = new boolean[clusterCount];
		loadIndex = new int[clusterCount];
		int loadCount = 0;
		for (int c = 0; c < clusterCount; c++) {
			clusterPosition[c] = positions.get(c);
			clusterIsLoad[c] = loads.contains(clusterPosition[c]);
			loadIndex[c] = clusterIsLoad[c] ? loadCount++ : -1;
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
		Map<Long, int[]> sides = new HashMap<>();
		for (Map.Entry<Long, Boolean> entry : switches.entrySet()) {
			Map<Integer, Integer> buses = clusterOf.getOrDefault(entry.getKey(), Map.of());
			int line = buses.getOrDefault(0, -1);
			int load = buses.getOrDefault(1, -1);
			sides.put(entry.getKey(), new int[] {line, load});
			if (!entry.getValue() || line < 0 || load < 0) continue;

			spans.get(line).computeIfAbsent(load, key -> new ArrayList<>()).add(0.0);
			spans.get(load).computeIfAbsent(line, key -> new ArrayList<>()).add(0.0);
		}

		wayStart = new int[clusterCount + 1];
		int ways = 0;
		for (int c = 0; c < clusterCount; c++) {
			wayStart[c] = ways;
			ways += spans.get(c).size();
		}

		wayStart[clusterCount] = ways;
		wayTo = new int[ways];
		wayK = new double[ways];
		int w = 0;
		for (int c = 0; c < clusterCount; c++) {
			for (Map.Entry<Integer, List<Double>> entry : spans.get(c).entrySet()) {
				wayTo[w] = entry.getKey();
				wayK[w] = circuitCoefficient(entry.getValue());
				w++;
			}
		}

		sides.forEach((pos, side) -> contacts.put(pos, new int[] {wayBetween(side[0], side[1]), wayBetween(side[1], side[0]), side[0], side[1]}));

		inflow = new double[clusterCount];
		carried = new Disturbances(clusterCount);
		reached = new Disturbances(clusterCount);
		totalSent = new double[ways];
		totalArrived = new double[clusterCount];
	}

	/** {@link #network(Collection, Collection, Set, Map)} with no switch in it. */
	static GridSolver network(Collection<Node> nodes, Collection<Edge> edges, Set<Long> loads) {
		return network(nodes, edges, loads, Map.of());
	}

	/**
	 * The network these nodes, spans, loads and switches make, ready to be solved for any set of generators.  Keep it
	 * while the four stay equal: the per-generator work it caches is most of the cost of a solve.
	 *
	 * @param switches by block position, every switch and whether it is closed; its nodes are on bus 0 (line side)
	 *                 and bus 1 (load side)
	 */
	static GridSolver network(Collection<Node> nodes, Collection<Edge> edges, Set<Long> loads, Map<Long, Boolean> switches) {
		return new GridSolver(nodes, edges, loads, switches);
	}

	/** The way from cluster {@code from} to cluster {@code to}, or -1. */
	private int wayBetween(int from, int to) {
		if (from < 0 || to < 0) return -1;

		for (int w = wayStart[from]; w < wayStart[from + 1]; w++) {
			if (wayTo[w] == to) return w;
		}

		return -1;
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
	 * Solves a network once, for callers that do not keep it.
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
		return network(nodes, edges, loads, switches).solve(sources, passages).readings();
	}

	/**
	 * Pushes these generators' power through the network.
	 *
	 * @param passages by block position, what the machine there burns (kW) for the flow (kW) crossing it; read every
	 *                 solve, since what a transformer burns is the machine's to say
	 */
	Solution solve(Collection<Source> sources, Map<Long, DoubleUnaryOperator> passages) {
		DoubleUnaryOperator[] burns = new DoubleUnaryOperator[clusterCount];
		if (!passages.isEmpty()) {
			for (int c = 0; c < clusterCount; c++) {
				burns[c] = passages.get(clusterPosition[c]);
			}
		}

		List<Source> live = new ArrayList<>();
		List<Plan> livePlans = new ArrayList<>();
		Map<Integer, Double> drawn = new LinkedHashMap<>();
		for (Source source : sources) {
			Integer start = indexOfId.get(source.node());
			if (start == null || !(source.powerKw() > 0.0)) continue;

			Plan plan = plans.computeIfAbsent(nodeCluster[start], this::plan);
			live.add(source);
			livePlans.add(plan);
			drawn.merge(source.node(), plan.reachesLoad() ? source.powerKw() : 0.0, Double::sum);
		}

		int count = live.size();
		sentNow = fit(sentNow, count);
		arrivedNow = fit(arrivedNow, count);
		sentBefore = fit(sentBefore, count);
		arrivedBefore = fit(arrivedBefore, count);
		for (int s = 0; s < count; s++) {
			Plan plan = livePlans.get(s);
			sentNow[s] = fit(sentNow[s], plan.stepWay().length);
			arrivedNow[s] = fit(arrivedNow[s], plan.order().length);
			sentBefore[s] = fit(sentBefore[s], plan.stepWay().length);
			arrivedBefore[s] = fit(arrivedBefore[s], plan.order().length);
		}

		double[] power = new double[clusterCount];
		reached.clear();
		boolean shared = false;
		for (int pass = 0; pass < PASSES; pass++) {
			Arrays.fill(power, 0.0);

			// a disturbance rides on the flow and does not change it, so only the pass that is kept carries one
			boolean last = pass == PASSES - 1 || count <= 1;
			for (int s = 0; s < count; s++) {
				push(livePlans.get(s), live.get(s), sentNow[s], arrivedNow[s], shared ? sentBefore[s] : null,
						shared ? arrivedBefore[s] : null, burns, power, last);
			}

			// one generator shares its lines with nobody, so its first pass is already the answer
			if (count <= 1) break;

			// what everyone put on each way and into each bus this pass is what the next one reads
			Arrays.fill(totalSent, 0.0);
			Arrays.fill(totalArrived, 0.0);
			for (int s = 0; s < count; s++) {
				Plan plan = livePlans.get(s);
				for (int k = 0; k < plan.stepWay().length; k++) {
					totalSent[plan.stepWay()[k]] += sentNow[s][k];
				}

				for (int i = 0; i < plan.order().length; i++) {
					totalArrived[plan.order()[i]] += arrivedNow[s][i];
				}
			}

			double[][] swap = sentBefore;
			sentBefore = sentNow;
			sentNow = swap;
			swap = arrivedBefore;
			arrivedBefore = arrivedNow;
			arrivedNow = swap;
			shared = true;
		}

		// a switch reads what crossed its contacts in the pass that is kept; with more than one generator the totals of
		// that pass are already in totalSent
		if (!contacts.isEmpty() && count <= 1) {
			Arrays.fill(totalSent, 0.0);
			if (count == 1) {
				Plan plan = livePlans.get(0);
				for (int k = 0; k < plan.stepWay().length; k++) {
					totalSent[plan.stepWay()[k]] += sentNow[0][k];
				}
			}
		}

		Map<Integer, Reading> readings = new LinkedHashMap<>();
		for (int i = 0; i < nodeCount; i++) {
			int c = nodeCluster[i];
			int[] ways = contacts.get(nodePosition[i]);
			readings.put(nodeIds[i], ways == null ? new Reading(power[c], reached.event(c)) : crossing(ways));
		}

		return new Solution(readings, drawn);
	}

	/**
	 * What a switch reads: the power across its contacts, line to load less load to line, with the disturbance of
	 * either side.  Nothing crosses an open switch, or one with a side that nothing is strung to.
	 */
	private Reading crossing(int[] contact) {
		if (contact[2] < 0 || contact[3] < 0) return Reading.NOTHING;

		double across = Math.abs(sentDown(contact[0]) - sentDown(contact[1]));
		return new Reading(across, across > 0.0 ? GridMath.merge(reached.event(contact[2]), reached.event(contact[3])) : PowerDeliveryEvent.NONE);
	}

	/** What the kept pass put down a way, all generators together; nothing down a way that is not there. */
	private double sentDown(int way) {
		return way < 0 ? 0.0 : totalSent[way];
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
	 * What does not change between ticks for a generator at one bus: the buses its flow reaches, in order, and for
	 * each of them the ways on with the share of the flow each takes.  The steps of {@code order[i]} are
	 * {@code stepWay/stepShare[stepStart[i] .. stepStart[i + 1])}; a bus with none sends nothing on.
	 */
	private record Plan(int[] order, boolean[] passesOn, int[] stepStart, int[] stepWay, int[] stepTo, double[] stepK,
			double[] stepShare, boolean reachesLoad) {
	}

	/** Orders the buses a generator reaches by a shortest-path search on the loss coefficient, and shares out its loads. */
	private Plan plan(int start) {
		double[] distance = new double[clusterCount];
		Arrays.fill(distance, Double.POSITIVE_INFINITY);
		int[] rank = new int[clusterCount];
		Arrays.fill(rank, -1);
		int[] order = new int[clusterCount];
		int visited = 0;

		// ties go to the lower index, so the same network always gives the same answer
		PriorityQueue<double[]> queue = new PriorityQueue<>((x, y) -> x[0] != y[0] ? Double.compare(x[0], y[0]) : Double.compare(x[1], y[1]));
		distance[start] = 0.0;
		queue.add(new double[] {0.0, start});
		while (!queue.isEmpty()) {
			double[] head = queue.poll();
			int u = (int) head[1];
			if (rank[u] >= 0 || head[0] > distance[u]) continue;

			rank[u] = visited;
			order[visited++] = u;
			// a load draws what reaches it and sends nothing on
			if (clusterIsLoad[u] && u != start) continue;

			for (int w = wayStart[u]; w < wayStart[u + 1]; w++) {
				int to = wayTo[w];
				double d = distance[u] + wayK[w];
				if (rank[to] < 0 && d < distance[to]) {
					distance[to] = d;
					queue.add(new double[] {d, to});
				}
			}
		}

		order = Arrays.copyOf(order, visited);

		// the loads beyond each bus, along ways that lead away from the generator
		BitSet[] reach = new BitSet[clusterCount];
		for (int i = visited - 1; i >= 0; i--) {
			int u = order[i];
			BitSet beyond = new BitSet();
			if (clusterIsLoad[u] && u != start) {
				beyond.set(loadIndex[u]);
			} else {
				for (int w = wayStart[u]; w < wayStart[u + 1]; w++) {
					if (rank[wayTo[w]] > rank[u]) beyond.or(reach[wayTo[w]]);
				}
			}

			reach[u] = beyond;
		}

		// every load beyond draws alike, so each way on takes the share of the loads it leads to
		boolean[] passesOn = new boolean[visited];
		int[] stepStart = new int[visited + 1];
		List<Integer> ways = new ArrayList<>();
		List<Double> shares = new ArrayList<>();
		for (int i = 0; i < visited; i++) {
			int u = order[i];
			stepStart[i] = ways.size();
			passesOn[i] = !reach[u].isEmpty() && !(clusterIsLoad[u] && u != start);
			if (!passesOn[i]) continue;

			int share = 0;
			for (int w = wayStart[u]; w < wayStart[u + 1]; w++) {
				if (rank[wayTo[w]] > rank[u]) share += reach[wayTo[w]].cardinality();
			}

			for (int w = wayStart[u]; w < wayStart[u + 1]; w++) {
				if (rank[wayTo[w]] <= rank[u]) continue;

				int beyond = reach[wayTo[w]].cardinality();
				if (beyond == 0) continue;

				ways.add(w);
				shares.add((double) beyond / share);
			}
		}

		stepStart[visited] = ways.size();
		int[] stepWay = ways.stream().mapToInt(Integer::intValue).toArray();
		int[] stepTo = new int[stepWay.length];
		double[] stepK = new double[stepWay.length];
		for (int k = 0; k < stepWay.length; k++) {
			stepTo[k] = wayTo[stepWay[k]];
			stepK[k] = wayK[stepWay[k]];
		}

		return new Plan(order, passesOn, stepStart, stepWay, stepTo, stepK, shares.stream().mapToDouble(Double::doubleValue).toArray(),
				!reach[start].isEmpty());
	}

	/**
	 * Pushes one generator's power out to its loads, adding what passes each bus to {@code power}, and records what it
	 * sent down each of its ways ({@code sent}, by step) and brought into each of its buses ({@code arrived}, by
	 * place in the order).  {@code sentBefore} / {@code arrivedBefore} are its own figures from the last pass, null
	 * on the first; with them, what the others put on a way is the last pass's total less this generator's part.
	 * Only a {@code kept} pass carries the disturbances, into {@link #reached}.
	 */
	private void push(Plan plan, Source source, double[] sent, double[] arrived, double[] sentBefore, double[] arrivedBefore,
			DoubleUnaryOperator[] burns, double[] power, boolean kept) {
		Arrays.fill(sent, 0.0);
		Arrays.fill(arrived, 0.0);

		int[] order = plan.order();
		int[] stepStart = plan.stepStart();
		int[] stepWay = plan.stepWay();
		int[] stepTo = plan.stepTo();
		double[] stepK = plan.stepK();
		double[] stepShare = plan.stepShare();
		boolean[] passesOn = plan.passesOn();
		inflow[order[0]] = source.powerKw();
		if (kept) carried.set(order[0], source.event());

		// every bus a flow is sent into comes later in the order, so each is read once and then cleared
		for (int i = 0; i < order.length; i++) {
			int u = order[i];
			double in = inflow[u];
			inflow[u] = 0.0;
			boolean disturbed = kept && carried.any[u];
			if (in > 0.0) {
				power[u] += in;
				arrived[i] = in;
				if (disturbed) reached.merge(u, carried, u);
				if (passesOn[i]) pushOn(plan, i, u, in, sent, arrivedBefore, sentBefore, burns, disturbed, stepStart, stepWay, stepTo, stepK, stepShare);
			}

			if (disturbed) carried.reset(u);
		}
	}

	/** Sends what reached bus {@code u}, {@code order[i]} of the plan, down its ways on. */
	private void pushOn(Plan plan, int i, int u, double in, double[] sent, double[] arrivedBefore, double[] sentBefore,
			DoubleUnaryOperator[] burns, boolean disturbed, int[] stepStart, int[] stepWay, int[] stepTo, double[] stepK, double[] stepShare) {
		// a transformer reads what goes in and passes on what is left after its core and windings
		double onward = in;
		DoubleUnaryOperator burn = burns[u];
		if (burn != null) {
			double through = in + (arrivedBefore == null ? 0.0 : Math.max(0.0, totalArrived[u] - arrivedBefore[i]));
			onward = in * GridMath.keptThrough(through, burn.applyAsDouble(through));
		}

		if (!(onward > 0.0)) return;

		for (int k = stepStart[i]; k < stepStart[i + 1]; k++) {
			double out = onward * stepShare[k];
			sent[k] = out;

			double others = sentBefore == null ? 0.0 : Math.max(0.0, totalSent[stepWay[k]] - sentBefore[k]);
			double eta = GridMath.keptFraction(out + others, stepK[k]);
			int to = stepTo[k];
			inflow[to] += out * eta;
			if (disturbed) carried.mergeAttenuated(to, u, eta);
		}
	}

	/**
	 * A disturbance per bus as plain arrays, with {@link GridMath#merge} and {@link GridMath#attenuate} worked in place,
	 * figure for figure, so a tick allocates nothing for them: every turbine draws one every tick.  A bus with nothing on
	 * it is {@link PowerDeliveryEvent#NONE}, whose figures every merge leaves as it found them.
	 */
	private static final class Disturbances {
		final boolean[] any;
		final double[] severity;
		final int[] duration;
		final boolean[] disconnecting;
		final double[] voltage;

		Disturbances(int size) {
			any = new boolean[size];
			severity = new double[size];
			duration = new int[size];
			disconnecting = new boolean[size];
			voltage = new double[size];
			clear();
		}

		void clear() {
			for (int c = 0; c < any.length; c++) {
				reset(c);
			}
		}

		void reset(int c) {
			any[c] = false;
			severity[c] = 0.0;
			duration[c] = 0;
			disconnecting[c] = false;
			voltage[c] = 1.0;
		}

		void set(int c, PowerDeliveryEvent event) {
			reset(c);
			if (event == PowerDeliveryEvent.NONE) return;

			any[c] = true;
			severity[c] = event.surgeSeverity();
			duration[c] = event.durationTicks();
			disconnecting[c] = event.disconnecting();
			voltage[c] = event.voltageFactor();
		}

		/** Bus {@code c} meets what {@code from} carries at {@code f}: the worse of each. */
		void merge(int c, Disturbances from, int f) {
			if (!from.any[f]) return;

			any[c] = true;
			severity[c] = Math.max(severity[c], from.severity[f]);
			duration[c] = Math.max(duration[c], from.duration[f]);
			disconnecting[c] |= from.disconnecting[f];
			voltage[c] = Math.min(voltage[c], from.voltage[f]);
		}

		/** Bus {@code c} meets what bus {@code f} carries after a hop of efficiency {@code eta}. */
		void mergeAttenuated(int c, int f, double eta) {
			double kept = Math.max(0.0, Math.min(1.0, eta));
			int left = Math.max(0, duration[f] - 1);
			double sag = left > 0 ? 1.0 - (1.0 - voltage[f]) * kept : 1.0;

			any[c] = true;
			severity[c] = Math.max(severity[c], Math.min(1.0, Math.max(0.0, severity[f] * kept * GridMath.DISTURBANCE_DECAY)));
			duration[c] = Math.max(duration[c], left);
			disconnecting[c] |= disconnecting[f] && left > 0;
			voltage[c] = Math.min(voltage[c], Math.min(1.0, Math.max(0.0, sag)));
		}

		PowerDeliveryEvent event(int c) {
			return any[c] ? new PowerDeliveryEvent(severity[c], duration[c], disconnecting[c], voltage[c]) : PowerDeliveryEvent.NONE;
		}
	}

	private static double[][] fit(double[][] buffer, int length) {
		return buffer.length >= length ? buffer : Arrays.copyOf(buffer, length);
	}

	private static double[] fit(double[] buffer, int length) {
		return buffer != null && buffer.length == length ? buffer : new double[length];
	}
}
