package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * The solver against the rules of grid.md section 8, on small hand-built networks, with the I²R loss of real
 * conductors and the flow going to the loads: every expected figure below is {@code P - k P^2} worked out by hand,
 * k = 1000 R / U^2 per kW, and every load (a box) draws alike.
 *
 * The machine rules (who may feed whom) are the table of section 8.2a, written out here, so that these tests check
 * the whole decision - machine rule and role rule together - and not just the flow.
 */
class GridSolverTest {
	private static final double EPS = 1e-6;
	/** The nameplate used throughout the manual tests: 78.75 kW at rated wind. */
	private static final double X = 78.75;
	/** Metres a block stands for, as WorldConditions has it. */
	private static final double METRES_PER_BLOCK = 10.0;

	/** A conductor as the solver sees it: ohms a km at 20 C, and the line-to-line volts it is worked at. */
	private record Line(double ohmsPerKm, double volts) {
	}

	/** 4 x 70 mm2 aerial bundled cable at 400 V: 10 blocks is k = 2.76875e-4 per kW. */
	private static final Line LV = new Line(0.443, 400.0);
	/** Aster 228 at 33 kV: 10 blocks is k = 1.34068e-8 per kW. */
	private static final Line MV = new Line(0.146, 33_000.0);

	/** The table of section 8.2a: which machine kind may feed which. */
	private static final Map<String, Set<String>> FEEDS = Map.of(
			"turbine", Set.of("cabin", "turbine", "transformer"),
			// not a real machine: a generator that may feed anything, so a test of the flow is not a test of the table
			"source", Set.of("cabin", "source", "pole", "box", "switch", "turbine"),
			"cabin", Set.of("pole", "tower", "transformer", "ground"),
			"pole", Set.of("pole", "box", "ground"),
			"box", Set.of(),
			"switch", Set.of("turbine", "cabin", "pole", "box", "tower", "transformer", "ground", "switch"));

	/** A small world: machines at positions on a line, fittings with ids, spans between them. */
	private static final class World {
		final Map<Long, String> kindAt = new HashMap<>();
		final Map<Integer, Long> positionOf = new HashMap<>();
		final Map<Integer, Integer> busOf = new HashMap<>();
		final Map<Integer, String> roleOf = new HashMap<>();
		final List<GridSolver.Node> nodes = new ArrayList<>();
		final List<GridSolver.Edge> edges = new ArrayList<>();
		final List<GridSolver.Source> sources = new ArrayList<>();
		final Map<Long, DoubleUnaryOperator> passages = new HashMap<>();
		/** Block positions are plain numbers here: x along a line, z across it, y zero. */
		final Map<Long, int[]> coordinates = new HashMap<>();

		void machine(long pos, String kind, int x) {
			machine(pos, kind, x, 0);
		}

		void machine(long pos, String kind, int x, int z) {
			kindAt.put(pos, kind);
			coordinates.put(pos, new int[] {x, 0, z});
		}

		void fitting(int id, long pos, String role) {
			fitting(id, pos, role, 0);
		}

		void fitting(int id, long pos, String role, int bus) {
			positionOf.put(id, pos);
			busOf.put(id, bus);
			roleOf.put(id, role);
		}

		void span(int a, int b) {
			span(a, b, LV);
		}

		void span(int a, int b, Line line) {
			for (int id : new int[] {a, b}) {
				if (nodes.stream().noneMatch(n -> n.id() == id)) nodes.add(new GridSolver.Node(id, positionOf.get(id), busOf.get(id)));
			}

			long pa = positionOf.get(a);
			long pb = positionOf.get(b);
			int[] ca = coordinates.get(pa);
			int[] cb = coordinates.get(pb);
			double length = Math.sqrt(Math.pow(ca[0] - cb[0], 2) + Math.pow(ca[1] - cb[1], 2) + Math.pow(ca[2] - cb[2], 2));
			boolean same = pa == pb;
			String ka = kindAt.get(pa);
			String kb = kindAt.get(pb);

			boolean forward = FlowRules.allows(FEEDS.get(ka).contains(kb), kb.equals("switch"), roleOf.get(a), roleOf.get(b), same);
			boolean backward = FlowRules.allows(FEEDS.get(kb).contains(ka), ka.equals("switch"), roleOf.get(b), roleOf.get(a), same);
			double ohms = line.ohmsPerKm() * length * METRES_PER_BLOCK / 1000.0;
			edges.add(new GridSolver.Edge(a, b, ohms, line.volts(), forward, backward));
		}

		void generator(int node, double kw) {
			sources.add(new GridSolver.Source(node, kw, PowerDeliveryEvent.NONE));
		}

		Set<Long> loads() {
			Set<Long> loads = new HashSet<>();
			kindAt.forEach((pos, kind) -> {
				if (kind.equals("box")) loads.add(pos);
			});
			return loads;
		}

		Map<Long, GridSolver.Reading> solve() {
			return GridSolver.byPosition(nodes, GridSolver.solve(nodes, edges, sources, passages, loads()));
		}

		/** What all the loads together receive. */
		double delivered(Map<Long, GridSolver.Reading> out) {
			return loads().stream().mapToDouble(pos -> out.getOrDefault(pos, GridSolver.Reading.NOTHING).powerKw()).sum();
		}
	}

	/**
	 * Turbine T (0), cabin C (10), pole P1 (30), power box B (40): the chain of manual test 1, with the turbine on the
	 * 33 kV collector and the cabin feeding a street bundle at 400 V.
	 */
	private static World baseChain(boolean turbineToInput) {
		World w = new World();
		w.machine(1, "turbine", 0);
		w.machine(2, "cabin", 10);
		w.machine(3, "pole", 30);
		w.machine(4, "box", 40);

		w.fitting(10, 1, "output");
		w.fitting(20, 2, "input");
		w.fitting(21, 2, "output");
		w.fitting(30, 3, "bidirectional");
		w.fitting(31, 3, "bidirectional");
		w.fitting(40, 4, "bidirectional");

		w.span(10, turbineToInput ? 20 : 21, MV);
		w.span(21, 30);
		w.span(31, 40);
		w.generator(10, X);
		return w;
	}

	// ---- manual test 1 ----

	@Test
	void theBaseChainCarriesTheNumbersOfTheManualTest() {
		Map<Long, GridSolver.Reading> out = baseChain(true).solve();

		// 100 m of 33 kV line costs 0.08 W; 200 m of 400 V bundle 3.43 kW, and the last 100 m 1.57 kW
		assertEquals(X, out.get(1L).powerKw(), EPS);
		assertEquals(78.749917, out.get(2L).powerKw(), EPS);
		assertEquals(75.315809, out.get(3L).powerKw(), EPS);
		assertEquals(73.745243, out.get(4L).powerKw(), EPS);
	}

	// ---- manual test 2 ----

	@Test
	void aTurbineWiredToTheCabinsOutputDeliversNothing() {
		Map<Long, GridSolver.Reading> out = baseChain(false).solve();

		assertEquals(X, out.get(1L).powerKw(), EPS);
		assertEquals(0.0, out.get(2L).powerKw(), EPS);
		assertEquals(0.0, out.get(3L).powerKw(), EPS);
		assertEquals(0.0, out.get(4L).powerKw(), EPS);
	}

	/** A line with nothing drawing at its end carries no current: the generator reads what it makes, the rest nothing. */
	@Test
	void aLineWithNoLoadCarriesNothing() {
		World w = baseChain(true);
		w.kindAt.put(4L, "pole");

		Map<Long, GridSolver.Reading> out = w.solve();
		assertEquals(X, out.get(1L).powerKw(), EPS);
		assertEquals(0.0, out.get(2L).powerKw(), EPS);
		assertEquals(0.0, out.get(3L).powerKw(), EPS);
		assertEquals(0.0, out.get(4L).powerKw(), EPS);
	}

	// ---- manual test 3: the phases of one circuit are not cables in parallel ----

	/** Strings {@code count} spans between the pole and the box of the base chain, on fittings of their own. */
	private static World chainWithSpans(int count) {
		World w = baseChain(true);
		for (int i = 1; i < count; i++) {
			w.fitting(31 + i, 3, "bidirectional");
			w.fitting(40 + i, 4, "bidirectional");
			w.span(31 + i, 40 + i);
		}

		return w;
	}

	/**
	 * Three spans between two machines are the three phases of one circuit, and lose what one circuit loses; it takes
	 * a second circuit, four to six spans, to halve the resistance (0.785 kW where one lost 1.571).
	 */
	@Test
	void threePhasesAreOneCircuitAndASecondCircuitHalvesTheLoss() {
		assertEquals(73.745243, chainWithSpans(2).solve().get(4L).powerKw(), EPS);
		assertEquals(73.745243, chainWithSpans(3).solve().get(4L).powerKw(), EPS);
		assertEquals(74.530526, chainWithSpans(4).solve().get(4L).powerKw(), EPS);
		assertEquals(74.530526, chainWithSpans(6).solve().get(4L).powerKw(), EPS);
	}

	@Test
	void circuitsAreCountedInThrees() {
		assertEquals(1.0, GridSolver.circuitCoefficient(List.of(1.0)), 1e-12);
		assertEquals(1.0, GridSolver.circuitCoefficient(List.of(1.0, 1.0, 1.0)), 1e-12);
		assertEquals(0.5, GridSolver.circuitCoefficient(List.of(1.0, 1.0, 1.0, 1.0, 1.0, 1.0)), 1e-12);
		assertEquals(0.0, GridSolver.circuitCoefficient(List.of(1.0, 0.0)), 0.0);
	}

	// ---- manual test 6 ----

	/**
	 * T (0) - switch S (5) - cabin C (10) - pole P - box B, the last two beside the cabin: the switch's line side takes
	 * the turbine, its load side feeds the cabin.
	 */
	private static World switched(boolean open) {
		World w = new World();
		w.machine(1, "turbine", 0);
		w.machine(2, "switch", 5);
		w.machine(3, "cabin", 10);
		w.machine(4, "box", 10);
		w.machine(5, "pole", 10);

		w.fitting(10, 1, "output");
		// line side: bus 0; load side: bus 0 when closed, bus 1 when open
		w.fitting(20, 2, "bidirectional", 0);
		w.fitting(21, 2, "bidirectional", open ? 1 : 0);
		w.fitting(30, 3, "input");
		w.fitting(31, 3, "output");
		w.fitting(40, 4, "bidirectional");
		w.fitting(50, 5, "bidirectional");
		w.fitting(51, 5, "bidirectional");
		w.span(10, 20, MV);
		w.span(21, 30, MV);
		w.span(31, 50);
		w.span(51, 40);
		w.generator(10, X);
		return w;
	}

	@Test
	void aClosedSwitchLetsPowerThrough() {
		Map<Long, GridSolver.Reading> out = switched(false).solve();

		// two 50 m hops of 33 kV line: 41.6 mW each; the pole and the box stand beside the cabin
		assertEquals(78.749958, out.get(2L).powerKw(), EPS);
		assertEquals(78.749917, out.get(3L).powerKw(), EPS);
		assertEquals(78.749917, out.get(4L).powerKw(), EPS);
	}

	@Test
	void anOpenSwitchStopsEverythingAndCarriesNothingItself() {
		Map<Long, GridSolver.Reading> out = switched(true).solve();

		// with the load side cut off there is no load to draw a current through the line side either
		assertEquals(0.0, out.get(4L).powerKw(), EPS);
		assertEquals(0.0, out.get(3L).powerKw(), EPS);
		assertEquals(0.0, out.get(2L).powerKw(), EPS);
		assertEquals(X, out.get(1L).powerKw(), EPS);
	}

	/** Opening a switch on one branch sends the whole flow down the other, which now has the only load. */
	@Test
	void anOpenSwitchSendsThePowerToTheOtherBranch() {
		for (boolean open : new boolean[] {false, true}) {
			World w = new World();
			w.machine(1, "source", 0);
			w.machine(2, "pole", 0);
			w.machine(3, "switch", 10);
			w.machine(4, "box", 20);
			w.machine(5, "box", 0, 10);

			w.fitting(10, 1, "output");
			w.fitting(20, 2, "bidirectional");
			w.fitting(21, 2, "bidirectional");
			w.fitting(22, 2, "bidirectional");
			w.fitting(30, 3, "bidirectional", 0);
			w.fitting(31, 3, "bidirectional", open ? 1 : 0);
			w.fitting(40, 4, "bidirectional");
			w.fitting(50, 5, "bidirectional");
			w.span(10, 20);
			w.span(21, 30);
			w.span(31, 40);
			w.span(22, 50);
			w.generator(10, 100.0);

			Map<Long, GridSolver.Reading> out = w.solve();
			if (open) {
				assertEquals(0.0, out.get(4L).powerKw(), EPS);
				assertEquals(97.23125, out.get(5L).powerKw(), EPS);
			} else {
				assertEquals(49.3078125, out.get(3L).powerKw(), EPS);
				assertEquals(48.634657, out.get(4L).powerKw(), EPS);
				assertEquals(49.3078125, out.get(5L).powerKw(), EPS);
			}
		}
	}

	// ---- the rules of 8.4 ----

	@Test
	void fittingsOfOneMachineShareABusAndPowerCrossesFree() {
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "pole", 20);
		w.machine(3, "box", 40);

		w.fitting(10, 1, "output");
		w.fitting(20, 2, "bidirectional");
		w.fitting(21, 2, "bidirectional");
		w.fitting(22, 2, "bidirectional");
		w.fitting(30, 3, "bidirectional");
		w.span(10, 20);
		w.span(22, 30);
		w.generator(10, 100.0);

		// the middle fitting (21) is on the pole's bus but has no span: it takes no part, so it is not a node
		Map<Long, GridSolver.Reading> out = w.solve();
		// 200 m of bundle each (k = 5.5375e-4): 100 - 5.5375, then 94.4625 - 4.9412
		assertEquals(94.4625, out.get(2L).powerKw(), EPS);
		assertEquals(89.521298, out.get(3L).powerKw(), EPS);
	}

	/** Two loads draw alike, so each branch takes half, less its own loss; a branch that leads to no load takes nothing. */
	@Test
	void powerGoesToTheLoadsAndADeadBranchTakesNothing() {
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "pole", 0);
		w.machine(3, "box", 10);
		w.machine(4, "box", 20);
		w.machine(5, "pole", 0, 30);

		w.fitting(10, 1, "output");
		for (int id : new int[] {20, 21, 22, 23}) w.fitting(id, 2, "bidirectional");
		w.fitting(30, 3, "bidirectional");
		w.fitting(40, 4, "bidirectional");
		w.fitting(50, 5, "bidirectional");
		w.span(10, 20);
		w.span(21, 30);
		w.span(22, 40);
		w.span(23, 50);
		w.generator(10, 100.0);

		Map<Long, GridSolver.Reading> out = w.solve();
		// the first pole is next to the generator (no loss); 50 - 2500 k over 100 m and over 200 m
		assertEquals(100.0, out.get(2L).powerKw(), EPS);
		assertEquals(49.3078125, out.get(3L).powerKw(), EPS);
		assertEquals(48.615625, out.get(4L).powerKw(), EPS);
		assertEquals(0.0, out.get(5L).powerKw(), EPS);
	}

	/**
	 * A diamond, T to A, A to B and C, B and C to D: the load at D is reached both ways, so each takes half, and D
	 * receives the sum of the two - the power is neither counted twice nor lost where the paths meet.
	 */
	@Test
	void aMeshSumsWhereThePathsMeetAndNeverGoesBack() {
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "pole", 0);
		w.machine(3, "pole", 10);
		w.machine(4, "pole", 0, 10);
		w.machine(5, "box", 10, 10);

		w.fitting(10, 1, "output");
		for (int id : new int[] {20, 21, 22}) w.fitting(id, 2, "bidirectional");
		w.fitting(30, 3, "bidirectional");
		w.fitting(31, 3, "bidirectional");
		w.fitting(40, 4, "bidirectional");
		w.fitting(41, 4, "bidirectional");
		w.fitting(50, 5, "bidirectional");
		w.fitting(51, 5, "bidirectional");
		w.span(10, 20);
		w.span(21, 30);
		w.span(22, 40);
		w.span(31, 50);
		w.span(41, 51);
		w.generator(10, 100.0);

		Map<Long, GridSolver.Reading> out = w.solve();
		assertEquals(49.3078125, out.get(3L).powerKw(), EPS);
		assertEquals(49.3078125, out.get(4L).powerKw(), EPS);
		assertEquals(2 * 48.634657, out.get(5L).powerKw(), EPS);
		// nothing is delivered back to the start, though D is wired to both of its neighbours
		assertEquals(100.0, out.get(2L).powerKw(), EPS);
	}

	@Test
	void differentGeneratorsAdd() {
		World w = new World();
		w.machine(1, "turbine", 0);
		w.machine(2, "turbine", 0, 1);
		w.machine(3, "turbine", 0, 2);
		w.machine(4, "cabin", 10, 1);
		w.machine(5, "box", 10, 1);
		w.machine(6, "pole", 10, 1);

		w.fitting(10, 1, "output");
		w.fitting(11, 2, "output");
		w.fitting(12, 3, "output");
		w.fitting(40, 4, "input");
		w.fitting(41, 4, "output");
		w.fitting(50, 5, "bidirectional");
		w.fitting(60, 6, "bidirectional");
		w.fitting(61, 6, "bidirectional");
		w.span(10, 40);
		w.span(11, 40);
		w.span(12, 40);
		w.span(41, 60);
		w.span(61, 50);
		w.generator(10, 100.0);
		w.generator(11, 50.0);
		w.generator(12, 30.0);

		// each contributes its own power, worn down by its own span (the outer two are 100.5 m, sqrt(10^2 + 1)):
		// no two of them share one
		Map<Long, GridSolver.Reading> out = w.solve();
		double outer = Math.sqrt(101.0);
		double k = 1000.0 * 0.443 * outer * 10.0 / 1000.0 / (400.0 * 400.0);
		double expected = (100.0 - k * 1e4) + (50.0 - 2500.0 * 2.76875e-4) + (30.0 - k * 900.0);
		assertEquals(expected, out.get(4L).powerKw(), EPS);
		assertEquals(expected, out.get(5L).powerKw(), EPS);
	}

	/**
	 * Two generators down one span lose what their sum loses, k (P1 + P2)^2, which is more than k P1^2 + k P2^2:
	 * 150 kW through 100 m of bundle loses 6.2297 kW, where 100 and 50 on spans of their own would lose 3.4609.
	 */
	@Test
	void generatorsSharingASpanLoseWhatTheirSumLoses() {
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "source", 0);
		w.machine(3, "pole", 0);
		w.machine(4, "box", 10);

		w.fitting(10, 1, "output");
		w.fitting(20, 2, "output");
		w.fitting(30, 3, "bidirectional");
		w.fitting(31, 3, "bidirectional");
		w.fitting(32, 3, "bidirectional");
		w.fitting(40, 4, "bidirectional");
		w.span(10, 30);
		w.span(20, 31);
		w.span(32, 40);
		w.generator(10, 100.0);
		w.generator(20, 50.0);

		Map<Long, GridSolver.Reading> out = w.solve();
		assertEquals(150.0, out.get(3L).powerKw(), EPS);
		assertEquals(143.7703125, out.get(4L).powerKw(), EPS);
	}

	/** A transformer reads what goes in and passes on what is left: 1 kW of core and 0.001 P^2 of windings. */
	@Test
	void aTransformerPassesOnWhatItDoesNotBurn() {
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "pole", 0);
		w.machine(3, "box", 0);
		w.fitting(10, 1, "output");
		w.fitting(20, 2, "bidirectional");
		w.fitting(21, 2, "bidirectional");
		w.fitting(30, 3, "bidirectional");
		w.span(10, 20);
		w.span(21, 30);
		w.passages.put(2L, kw -> 1.0 + 0.001 * kw * kw);
		w.generator(10, 100.0);

		Map<Long, GridSolver.Reading> out = w.solve();
		assertEquals(100.0, out.get(2L).powerKw(), EPS);
		assertEquals(89.0, out.get(3L).powerKw(), EPS);
	}

	/** Two generators through one transformer: its core is burned once and its windings carry their sum. */
	@Test
	void generatorsThroughOneTransformerShareItsCore() {
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "source", 0);
		w.machine(3, "pole", 0);
		w.machine(4, "box", 0);
		w.fitting(10, 1, "output");
		w.fitting(11, 2, "output");
		w.fitting(30, 3, "bidirectional");
		w.fitting(31, 3, "bidirectional");
		w.fitting(32, 3, "bidirectional");
		w.fitting(40, 4, "bidirectional");
		w.span(10, 30);
		w.span(11, 31);
		w.span(32, 40);
		w.passages.put(3L, kw -> 1.0 + 0.001 * kw * kw);
		w.generator(10, 60.0);
		w.generator(11, 40.0);

		// 100 kW through: 1 + 10 = 11 kW, not (1 + 3.6) + (1 + 1.6) as two machines alone would have it
		Map<Long, GridSolver.Reading> out = w.solve();
		assertEquals(89.0, out.get(4L).powerKw(), EPS);
	}

	@Test
	void aGeneratorWithNoSpanIsNotInTheGraphAndASourceWithoutAnodeIsIgnored() {
		World w = new World();
		w.machine(1, "turbine", 0);
		w.fitting(10, 1, "output");
		w.generator(10, 100.0);

		assertTrue(w.solve().isEmpty());
	}

	@Test
	void aDeadCableCarriesNothingAndNothingIsDivertedIntoIt() {
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "source", 10);
		w.machine(3, "pole", 0);
		w.machine(4, "box", 10);

		w.fitting(10, 1, "output");
		w.fitting(20, 2, "output");
		w.fitting(30, 3, "bidirectional");
		w.fitting(31, 3, "bidirectional");
		w.fitting(40, 4, "bidirectional");
		w.span(10, 20);
		w.span(10, 30);
		w.span(31, 40);
		w.generator(10, 100.0);

		// turbine to turbine is output to output: that way is not a way on, so the whole power goes to the pole
		Map<Long, GridSolver.Reading> out = w.solve();
		assertEquals(100.0, out.get(3L).powerKw(), EPS);
		assertEquals(0.0, out.get(2L).powerKw(), EPS);
		assertEquals(97.23125, out.get(4L).powerKw(), EPS);
	}

	/**
	 * Five kilometres of street bundle is 2.215 ohm a phase, k = 0.01384 per kW: it can take 36.1 kW and deliver
	 * 18.06, and a 100 kW generator gets no more across it than that.
	 */
	@Test
	void aLongRunDeliversNoMoreThanItsTransferLimit() {
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "box", 500);
		w.fitting(10, 1, "output");
		w.fitting(20, 2, "bidirectional");
		w.span(10, 20);
		w.generator(10, 100.0);

		assertEquals(18.058691, w.solve().get(2L).powerKw(), EPS);
	}

	@Test
	void anEventAttenuatesAlongTheLineAndReachesTheEnd() {
		World w = baseChain(true);
		w.sources.clear();
		w.sources.add(new GridSolver.Source(10, X, new PowerDeliveryEvent(0.9, 6, true, 0.5)));

		Map<Long, GridSolver.Reading> out = w.solve();
		assertEquals(0.9, out.get(1L).event().surgeSeverity(), EPS);
		assertEquals(5, out.get(2L).event().durationTicks());
		assertEquals(4, out.get(3L).event().durationTicks());
		assertEquals(3, out.get(4L).event().durationTicks());
		assertTrue(out.get(4L).event().disconnecting());
		assertTrue(out.get(4L).event().surgeSeverity() < out.get(2L).event().surgeSeverity());
	}

	// ---- conservation ----

	/** A grid of poles, {@code side} by {@code side}, {@code spacing} blocks apart, with the generator by the corner pole. */
	private static World lattice(int side, int spacing, double kw) {
		World w = new World();
		w.machine(1, "source", 0, 0);
		w.fitting(1, 1, "output");

		long pos = 10;
		int next = 100;
		int[][] ids = new int[side][side];
		for (int r = 0; r < side; r++) {
			for (int c = 0; c < side; c++) {
				w.machine(pos, "pole", spacing * c, spacing * r);
				ids[r][c] = next;
				for (int f = 0; f < 5; f++) w.fitting(next + f, pos, "bidirectional");
				next += 5;
				pos++;
			}
		}

		w.span(1, ids[0][0]);
		for (int r = 0; r < side; r++) {
			for (int c = 0; c < side; c++) {
				if (c + 1 < side) w.span(ids[r][c] + 1, ids[r][c + 1] + 2);
				if (r + 1 < side) w.span(ids[r][c] + 3, ids[r + 1][c] + 0);
			}
		}

		w.generator(1, kw);
		return w;
	}

	/**
	 * The mesh the review measured: the DFS this replaced spent its whole budget in the first branch, so on a 5 x 5
	 * grid the pole at (1, 0) read 1.4 kW against the 405 at (0, 1).  Every bus is now visited once, the two read
	 * the same, and what the load at the far corner receives plus every span's loss is the generator's output.
	 */
	@Test
	void aMeshIsSolvedWholeAndSymmetrically() {
		int side = 6;
		World w = lattice(side, 10, 100.0);
		w.kindAt.put(10L + side * side - 1, "box");

		Map<Long, GridSolver.Reading> out = w.solve();
		double right = out.get(11L).powerKw();
		double down = out.get(10L + side).powerKw();
		assertEquals(right, down, 1e-9);
		assertEquals(50.0, right, 1.0);

		double delivered = w.delivered(out);
		assertTrue(delivered < 100.0 && delivered > 90.0, "delivered " + delivered);
	}

	/** With every span lossless, the loads together receive exactly what the generators make: nothing made, nothing lost. */
	@Test
	void aLosslessNetworkDeliversExactlyWhatIsGenerated() {
		// every pole on one spot, so every span is of no length; three loads, and a second generator in the middle
		World mesh = lattice(14, 0, 1000.0);
		for (long pos : new long[] {10L + 13, 10L + 14 * 13, 10L + 14 * 14 - 1}) mesh.kindAt.put(pos, "box");
		mesh.machine(2, "source", 0, 0);
		mesh.fitting(2, 2, "output");
		mesh.span(2, 100 + 5 * (14 * 7 + 7) + 4);
		mesh.generator(2, 500.0);

		assertEquals(1500.0, mesh.delivered(mesh.solve()), 1e-9);
	}

	@Test
	void whatTheLoadsReceiveNeverExceedsWhatIsGenerated() {
		World mesh = lattice(8, 10, 300.0);
		for (long pos : new long[] {10L + 7, 10L + 8 * 7, 10L + 8 * 8 - 1, 10L + 8 * 3 + 4}) mesh.kindAt.put(pos, "box");
		mesh.machine(2, "source", 40, 40);
		mesh.fitting(2, 2, "output");
		mesh.span(2, 100 + 5 * (8 * 4 + 4) + 4);
		mesh.generator(2, 200.0);

		double delivered = mesh.delivered(mesh.solve());
		assertTrue(delivered <= 500.0, "delivered " + delivered);
		assertTrue(delivered > 0.0);
	}
}
