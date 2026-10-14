package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The solver against the numbers of grid.md section 19 and the rules of section 8, on small hand-built networks.
 *
 * The machine rules (who may feed whom) are the table of section 8.2a, written out here, so that these tests check
 * the whole decision - machine rule and role rule together - and not just the walk.
 */
class GridSolverTest {
	private static final double EPS = 1e-6;
	/** The nameplate used throughout the manual tests: 78.75 kW at rated wind. */
	private static final double X = 78.75;

	/** The table of section 8.2a: which machine kind may feed which. */
	private static final Map<String, Set<String>> FEEDS = Map.of(
			"turbine", Set.of("cabin", "turbine", "transformer"),
			// not a real machine: a generator that may feed anything, so a test of the walk is not a test of the table
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
		/** Block positions are plain numbers here: x along a line, with y and z both zero. */
		final Map<Long, int[]> coordinates = new HashMap<>();

		void machine(long pos, String kind, int x) {
			kindAt.put(pos, kind);
			coordinates.put(pos, new int[] {x, 0, 0});
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
			edges.add(new GridSolver.Edge(a, b, length, forward, backward));
		}

		void generator(int node, double kw) {
			sources.add(new GridSolver.Source(node, kw, PowerDeliveryEvent.NONE));
		}

		Map<Long, GridSolver.Reading> solve() {
			return GridSolver.byPosition(nodes, GridSolver.solve(nodes, edges, sources));
		}
	}

	/** Turbine T (0), cabin C (10), pole P1 (30), power box B (40): the chain of manual test 1. */
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

		w.span(10, turbineToInput ? 20 : 21);
		w.span(21, 30);
		w.span(31, 40);
		w.generator(10, X);
		return w;
	}

	// ---- manual test 1 ----

	@Test
	void theBaseChainCarriesTheNumbersOfTheManualTest() {
		Map<Long, GridSolver.Reading> out = baseChain(true).solve();

		assertEquals(X, out.get(1L).powerKw(), EPS);
		assertEquals(0.9 * X, out.get(2L).powerKw(), EPS);
		assertEquals(0.72 * X, out.get(3L).powerKw(), EPS);
		assertEquals(0.648 * X, out.get(4L).powerKw(), EPS);

		// the figures a player reads off the wrench
		assertEquals(70.875, out.get(2L).powerKw(), 0.01);
		assertEquals(56.70, out.get(3L).powerKw(), 0.01);
		assertEquals(51.03, out.get(4L).powerKw(), 0.01);
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

	// ---- manual test 3 ----

	@Test
	void aSecondCableBetweenTheSamePolesLosesLess() {
		World w = baseChain(true);
		// another cable between other fittings of the pole and the box, 10 blocks like the first
		w.fitting(32, 3, "bidirectional");
		w.fitting(41, 4, "bidirectional");
		w.span(32, 41);

		Map<Long, GridSolver.Reading> out = w.solve();

		assertEquals(0.72 * X, out.get(3L).powerKw(), EPS);
		assertEquals(0.72 * X * 0.92592593, out.get(4L).powerKw(), 1e-4);
		assertEquals(0.6667 * X, out.get(4L).powerKw(), 0.01 * X / 100);
	}

	// ---- manual test 6 ----

	/** T (0) - switch S (5) - cabin C (10): the switch's line side takes the turbine, its load side feeds the cabin. */
	private static World switched(boolean open) {
		World w = new World();
		w.machine(1, "turbine", 0);
		w.machine(2, "switch", 5);
		w.machine(3, "cabin", 10);

		w.fitting(10, 1, "output");
		// line side: bus 0; load side: bus 0 when closed, bus 1 when open
		w.fitting(20, 2, "bidirectional", 0);
		w.fitting(21, 2, "bidirectional", open ? 1 : 0);
		w.fitting(30, 3, "input");
		w.span(10, 20);
		w.span(21, 30);
		w.generator(10, X);
		return w;
	}

	@Test
	void aClosedSwitchLetsPowerThrough() {
		Map<Long, GridSolver.Reading> out = switched(false).solve();

		assertEquals(X * GridMath.efficiency(1, 5) * GridMath.efficiency(1, 5), out.get(3L).powerKw(), EPS);
		assertTrue(out.get(3L).powerKw() > 0.0);
	}

	@Test
	void anOpenSwitchStopsEverythingBelowItAndLeavesAboveUnchanged() {
		Map<Long, GridSolver.Reading> out = switched(true).solve();

		assertEquals(0.0, out.get(3L).powerKw(), EPS);
		// the switch itself reads what is on its supply side
		assertEquals(X * GridMath.efficiency(1, 5), out.get(2L).powerKw(), EPS);
		assertEquals(X, out.get(1L).powerKw(), EPS);
	}

	// ---- the rules of 8.4 ----

	@Test
	void fittingsOfOneMachineShareABusAndPowerCrossesFree() {
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "pole", 20);
		w.machine(3, "pole", 40);

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
		assertEquals(80.0, out.get(2L).powerKw(), EPS);
		assertEquals(80.0 * 0.8, out.get(3L).powerKw(), EPS);
	}

	@Test
	void powerSplitsEquallyBetweenTheWaysOnWhateverTheyLeadTo() {
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "pole", 0);
		w.machine(3, "pole", 10);
		w.machine(4, "pole", 20);

		w.fitting(10, 1, "output");
		w.fitting(20, 2, "bidirectional");
		w.fitting(21, 2, "bidirectional");
		w.fitting(22, 2, "bidirectional");
		w.fitting(30, 3, "bidirectional");
		w.fitting(40, 4, "bidirectional");
		w.span(10, 20);
		w.span(21, 30);
		w.span(22, 40);
		w.generator(10, 100.0);

		Map<Long, GridSolver.Reading> out = w.solve();
		// the first pole is next to the turbine (no loss); each of the two ways takes half, less its own loss
		assertEquals(100.0, out.get(2L).powerKw(), EPS);
		assertEquals(50.0 * 0.9, out.get(3L).powerKw(), EPS);
		assertEquals(50.0 * 0.8, out.get(4L).powerKw(), EPS);
	}

	@Test
	void twoCablesToTheSameBusAreOneWayNotTwo() {
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "pole", 0);
		w.machine(3, "pole", 10);
		w.machine(4, "pole", 10);

		w.fitting(10, 1, "output");
		w.fitting(20, 2, "bidirectional");
		w.fitting(21, 2, "bidirectional");
		w.fitting(22, 2, "bidirectional");
		w.fitting(30, 3, "bidirectional");
		w.fitting(31, 3, "bidirectional");
		w.fitting(40, 4, "bidirectional");
		w.span(10, 20);
		// two cables from machine 2 to machine 3, and one to machine 4
		w.span(21, 30);
		w.span(22, 31);
		w.fitting(23, 2, "bidirectional");
		w.span(23, 40);
		w.generator(10, 100.0);

		Map<Long, GridSolver.Reading> out = w.solve();
		// two ways (machine 3 by two cables, machine 4 by one): half each; the pair of cables loses less
		assertEquals(50.0 * GridMath.efficiency(2, 10), out.get(3L).powerKw(), EPS);
		assertEquals(50.0 * GridMath.efficiency(1, 10), out.get(4L).powerKw(), EPS);
	}

	@Test
	void aMeshDoesNotCountTheSamePowerTwiceAndNeverGoesBack() {
		// a diamond: T -> A, A -> B, A -> C, B -> D, C -> D
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "pole", 0);
		w.machine(3, "pole", 10);
		w.machine(4, "pole", 10);
		w.machine(5, "pole", 20);

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
		// D is reached by two paths of 45 each (50 x 0.9 x 0.9, from either side); it keeps the larger, not the sum
		double viaOne = 100.0 * 0.5 * 0.9 * 0.9;
		assertEquals(viaOne, out.get(5L).powerKw(), EPS);
		assertEquals(50.0 * 0.9, out.get(3L).powerKw(), EPS);
		assertEquals(50.0 * 0.9, out.get(4L).powerKw(), EPS);
		// nothing is delivered back to the start, though D is wired to both of its neighbours
		assertEquals(100.0, out.get(2L).powerKw(), EPS);
	}

	@Test
	void differentGeneratorsAdd() {
		World w = new World();
		w.machine(1, "turbine", 0);
		w.machine(2, "turbine", 0);
		w.machine(3, "turbine", 0);
		w.machine(4, "cabin", 10);

		w.fitting(10, 1, "output");
		w.fitting(11, 2, "output");
		w.fitting(12, 3, "output");
		w.fitting(40, 4, "input");
		w.span(10, 40);
		w.span(11, 40);
		w.span(12, 40);
		w.generator(10, 100.0);
		w.generator(11, 50.0);
		w.generator(12, 30.0);

		// each contributes its own power, already worn down by its own cable: no lines of the same generator are parallel
		Map<Long, GridSolver.Reading> out = w.solve();
		assertEquals((100.0 + 50.0 + 30.0) * 0.9, out.get(4L).powerKw(), EPS);
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
		w.machine(4, "pole", 10);

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
		assertEquals(90.0, out.get(4L).powerKw(), EPS);
	}

	@Test
	void aLongRunStillDeliversATenth() {
		World w = new World();
		w.machine(1, "source", 0);
		w.machine(2, "pole", 500);
		w.fitting(10, 1, "output");
		w.fitting(20, 2, "bidirectional");
		w.span(10, 20);
		w.generator(10, 100.0);

		assertEquals(10.0, w.solve().get(2L).powerKw(), EPS);
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

	@Test
	void theWalkOfAHugeMeshStopsAtItsLimit() {
		// a lattice of poles that has far more simple paths than the budget allows: the walk must end and give an answer
		World w = new World();
		w.machine(1, "source", 0);
		w.fitting(1, 1, "output");

		int side = 14;
		int next = 100;
		int[][] ids = new int[side][side];
		long pos = 10;
		for (int r = 0; r < side; r++) {
			for (int c = 0; c < side; c++) {
				w.machine(pos, "pole", 0);
				ids[r][c] = next;
				for (int k = 0; k < 4; k++) w.fitting(next + k, pos, "bidirectional");
				next += 4;
				pos++;
			}
		}

		long[][] position = new long[side][side];
		pos = 10;
		for (int r = 0; r < side; r++) {
			for (int c = 0; c < side; c++) position[r][c] = pos++;
		}

		w.span(1, ids[0][0]);
		for (int r = 0; r < side; r++) {
			for (int c = 0; c < side; c++) {
				if (c + 1 < side) w.span(ids[r][c] + 1, ids[r][c + 1] + 2);
				if (r + 1 < side) w.span(ids[r][c] + 3, ids[r + 1][c] + 0);
			}
		}

		w.generator(1, 1000.0);
		Map<Long, GridSolver.Reading> out = w.solve();
		assertEquals(1000.0, out.get(10L).powerKw(), EPS);
		assertTrue(out.get(10L + side * side - 1).powerKw() >= 0.0);
	}
}
