package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;
import io.github.simonerecchia.renewables.catalog.ConductorCatalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Lengths of ground-laid conductor that touch carry power from one to the next ({@link LevelGrid#joinRuns}).  The
 * example plant lays a run of five blocks out of the arrival substation and strings a span to each end of it; the
 * five used to be five nodes with nothing between them, and the kiosk beyond read nothing.
 */
class LaidRunTest {
	private static final double P = 100.0;

	/** A machine as the grid sees it: one fitting, and for a run the conductor it is and the lengths it joins. */
	private static final class Host implements InsulatorHost {
		final int id;
		@Nullable final ConductorSpec laid;
		final List<BlockPos> joined = new ArrayList<>();
		final boolean load;

		Host(int id, @Nullable ConductorSpec laid, boolean load) {
			this.id = id;
			this.laid = laid;
			this.load = load;
		}

		@Override
		public int[] getInsulatorIds() {
			return new int[] {id};
		}

		@Override
		public Vec3 getWirePosition(int index) {
			return Vec3.ZERO;
		}

		@Override
		public String fittingType() {
			return laid != null ? FittingTypes.GROUND_CONDUCTOR : FittingTypes.POWER_BOX;
		}

		@Override
		public boolean feeds(InsulatorHost other) {
			return laid == null || other.laidConductor() == null || other.laidConductor() == laid || other.isLoad();
		}

		@Override
		public boolean isLoad() {
			return load;
		}

		@Nullable
		@Override
		public ConductorSpec laidConductor() {
			return laid;
		}

		@Override
		public List<BlockPos> laidNeighbours() {
			return joined;
		}

		@Override
		public double getCurrentPower() {
			return 0.0;
		}
	}

	/** A generator at x = 110, runs from {@code from} to {@code to} along x, a kiosk at x = 125, spans to both ends. */
	private static final class Site {
		final Map<Long, LevelGrid.Slot> world = new HashMap<>();
		final Map<Integer, GridSolver.Node> nodes = new LinkedHashMap<>();
		final Map<Long, InsulatorHost> hosts = new LinkedHashMap<>();
		final Map<Long, Integer> firstNode = new HashMap<>();
		final List<GridSolver.Edge> edges = new ArrayList<>();
		final ConductorSpec conductor;
		final long kiosk = at(125).asLong();

		Site(ConductorSpec conductor, int from, int to) {
			this.conductor = conductor;
			for (int x = from; x <= to; x++) {
				Host run = new Host(x, conductor, false);
				if (x > from) run.joined.add(at(x - 1));
				if (x < to) run.joined.add(at(x + 1));
				put(at(x), run);
			}

			Host generator = new Host(1, null, false);
			put(at(110), generator);
			put(at(125), new Host(200, null, true));

			span(at(110), at(from));
			span(at(to), at(125));
		}

		static BlockPos at(int x) {
			return new BlockPos(x, 64, 0);
		}

		void put(BlockPos pos, Host host) {
			world.put(pos.asLong(), new LevelGrid.Slot(true, host, host.getInsulatorIds()));
		}

		/** A span, as LevelGrid makes one: both ends in the graph, the conductor's resistance over its length. */
		void span(BlockPos a, BlockPos b) {
			for (BlockPos pos : new BlockPos[] {a, b}) {
				InsulatorHost host = world.get(pos.asLong()).host();
				int id = host.getInsulatorIds()[0];
				nodes.putIfAbsent(id, new GridSolver.Node(id, pos.asLong(), 0));
				hosts.putIfAbsent(pos.asLong(), host);
				firstNode.putIfAbsent(pos.asLong(), id);
			}

			double length = Math.sqrt(a.distSqr(b));
			edges.add(new GridSolver.Edge(world.get(a.asLong()).host().getInsulatorIds()[0], world.get(b.asLong()).host().getInsulatorIds()[0],
					conductor.resistanceOhms(length), conductor.nominalVolts(), true, true));
		}

		double kioskReads(boolean join) {
			if (join) {
				LevelGrid.joinRuns(key -> world.getOrDefault(key, new LevelGrid.Slot(true, null, new int[0])), nodes, hosts, firstNode, edges);
			}

			Map<Integer, GridSolver.Reading> readings = GridSolver.solve(nodes.values(), edges,
					List.of(new GridSolver.Source(1, P, PowerDeliveryEvent.NONE)), Map.of(), Set.of(kiosk));
			return GridSolver.byPosition(nodes.values(), readings).getOrDefault(kiosk, GridSolver.Reading.NOTHING).powerKw();
		}
	}

	@Test
	void aRunOfFiveBlocksCarriesThePowerFromEndToEnd() {
		assertEquals(0.0, new Site(ConductorCatalog.AAAC_228, 116, 120).kioskReads(false), 0.0);

		double reads = new Site(ConductorCatalog.AAAC_228, 116, 120).kioskReads(true);
		assertTrue(reads > 0.999 * P && reads < P, "kiosk reads " + reads);
	}

	/**
	 * And with the loss of its length: four blocks of street bundle between the ends lose what a span of four blocks
	 * of it does, to the second order of a loss that is itself small.
	 */
	@Test
	void aRunLosesWhatItsLengthOfConductorLoses() {
		ConductorSpec bundle = ConductorCatalog.ABC_70;
		double laid = new Site(bundle, 116, 120).kioskReads(true);

		Site strung = new Site(bundle, 116, 116);
		strung.put(Site.at(120), new Host(120, bundle, false));
		strung.edges.removeIf(edge -> edge.b() == 200);
		strung.span(Site.at(116), Site.at(120));
		strung.span(Site.at(120), Site.at(125));
		double spanned = strung.kioskReads(false);

		assertTrue(spanned < P - 1.0, "spanned " + spanned);
		assertEquals(spanned, laid, 0.01);
	}

	/** Only the same conductor joins, and only where both lengths say so: a stale state or another conductor does not. */
	@Test
	void onlyLengthsOfOneConductorThatBothJoinAreOne() {
		Site other = new Site(ConductorCatalog.ABC_70, 116, 120);
		other.put(Site.at(118), new Host(118, ConductorCatalog.AAAC_228, false));
		assertEquals(0.0, other.kioskReads(true), 0.0);

		Site stale = new Site(ConductorCatalog.ABC_70, 116, 120);
		Host lonely = new Host(118, ConductorCatalog.ABC_70, false);
		lonely.joined.add(Site.at(119));
		stale.put(Site.at(118), lonely);
		assertEquals(0.0, stale.kioskReads(true), 0.0);
	}

	/** A length in a chunk that is not loaded is left out, and the stretch beyond it with it, as a span's end would be. */
	@Test
	void aLengthThatIsNotLoadedCutsTheRun() {
		Site site = new Site(ConductorCatalog.AAAC_228, 116, 120);
		site.world.put(Site.at(118).asLong(), new LevelGrid.Slot(false, null, new int[0]));
		assertEquals(0.0, site.kioskReads(true), 0.0);
	}

	/** A run that forks feeds both kiosks it leads to, each half. */
	@Test
	void aForkInARunFeedsBothWays() {
		Site site = new Site(ConductorCatalog.AAAC_228, 116, 120);
		Host fork = (Host) site.world.get(Site.at(118).asLong()).host();
		BlockPos branch = new BlockPos(118, 64, 1);
		fork.joined.add(branch);
		Host leg = new Host(300, ConductorCatalog.AAAC_228, false);
		leg.joined.add(Site.at(118));
		site.put(branch, leg);

		Host second = new Host(400, null, true);
		BlockPos secondAt = new BlockPos(118, 64, 6);
		site.put(secondAt, second);
		site.span(branch, secondAt);

		site.kioskReads(true);
		Map<Integer, GridSolver.Reading> readings = GridSolver.solve(site.nodes.values(), site.edges,
				List.of(new GridSolver.Source(1, P, PowerDeliveryEvent.NONE)), Map.of(), Set.of(site.kiosk, secondAt.asLong()));
		Map<Long, GridSolver.Reading> out = GridSolver.byPosition(site.nodes.values(), readings);
		assertEquals(50.0, out.get(site.kiosk).powerKw(), 0.01);
		assertEquals(50.0, out.get(secondAt.asLong()).powerKw(), 0.01);
	}
}
