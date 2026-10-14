package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.catalog.TransformerCatalog;
import org.junit.jupiter.api.Test;

/** IEC 60076-7's temperatures and the protection on them, against figures worked out by hand. */
class TransformerThermalTest {
	/** Steps a minute at a time for this many minutes, a second a step; answers the minute it tripped, or -1. */
	private static int run(TransformerThermal thermal, double load, int minutes) {
		for (int minute = 0; minute < minutes; minute++) {
			for (int second = 0; second < 60; second++) {
				if (thermal.step(load, 1.0)) return minute;
			}
		}

		return -1;
	}

	/** At its rating in 20 °C a distribution unit settles at 20 + 55 + 23 = 98 °C, the hot spot it is designed for. */
	@Test
	void atItsRatingItSettlesAtTheDesignHotSpotAndRunsForEver() {
		TransformerThermal pad = new TransformerThermal(TransformerCatalog.MACHINE);
		assertEquals(-1, run(pad, 1.0, 24 * 60));

		assertEquals(98.0, pad.hotSpot(), 0.05);
		assertEquals(75.0, pad.topOil(), 0.05);
	}

	/**
	 * The example site's C90 puts 3 MW through the 2.5 MVA pad unit, 1.2 times its rating.  R = 20.35 / 1.811:
	 * top oil 20 + 55 ((1 + 1.44 R) / (1 + R))^0.8 = 92.2 °C, hot spot that and 23 x 1.2^1.6 = 123.0 °C.  Inside
	 * long-time emergency loading, so it runs.
	 */
	@Test
	void aFifthOverItsRatingRunsHotAndDoesNotTrip() {
		TransformerThermal pad = new TransformerThermal(TransformerCatalog.MACHINE);
		assertEquals(-1, run(pad, 1.2, 24 * 60));

		double r = 20.35 / 1.811;
		double oil = 20.0 + 55.0 * Math.pow((1.0 + 1.44 * r) / (1.0 + r), 0.8);
		assertEquals(oil, pad.topOil(), 0.05);
		assertEquals(oil + 23.0 * Math.pow(1.2, 1.6), pad.hotSpot(), 0.05);
		assertFalse(pad.tripped());
	}

	/**
	 * A C130's 4 MW through the same unit is 1.6 times its rating: the winding's gradient settles at 48.8 K within half
	 * an hour, and the hot spot reaches 140 °C when the oil, on its three-hour time constant, has risen 71 K of the
	 * 112 it is heading for - some three hours from cold.
	 */
	@Test
	void aSixtyPercentOverloadTripsTheWindingThermometerAfterHours() {
		TransformerThermal pad = new TransformerThermal(TransformerCatalog.MACHINE);
		int minute = run(pad, 1.6, 10 * 60);

		assertTrue(minute > 150 && minute < 220, "tripped at minute " + minute);
		assertEquals(TransformerThermal.Trip.HOT_SPOT, pad.trip());
	}

	/** The review's 26 MW through 2.5 MVA is ten times the rating, far past 1.8: the overcurrent relay, at once. */
	@Test
	void tenTimesItsRatingTripsTheOvercurrentRelayAtOnce() {
		TransformerThermal pad = new TransformerThermal(TransformerCatalog.MACHINE);

		assertTrue(pad.step(26_000.0 / 2_500.0, 1.0));
		assertEquals(TransformerThermal.Trip.OVERCURRENT, pad.trip());
		// the substation unit, a medium-power transformer, is allowed 1.5 and no more
		TransformerThermal grid = new TransformerThermal(TransformerCatalog.SUBSTATION);
		assertFalse(grid.step(1.45, 1.0));
		assertTrue(grid.step(1.55, 1.0));
	}

	/** Tripped, it carries nothing and cools; it cannot be put back until it is inside normal cyclic loading. */
	@Test
	void aTrippedUnitIsResetOnlyOnceItHasCooled() {
		TransformerThermal pad = new TransformerThermal(TransformerCatalog.MACHINE);
		run(pad, 1.6, 10 * 60);
		assertTrue(pad.tripped());
		assertFalse(pad.reset());

		// told it is still loaded, it heats no more: a tripped transformer is off the line
		double before = pad.hotSpot();
		run(pad, 1.6, 30);
		assertTrue(pad.hotSpot() < before);

		run(pad, 1.6, 60);
		assertTrue(pad.mayReset(), "hot spot " + pad.hotSpot() + ", top oil " + pad.topOil());
		assertTrue(pad.reset());
		assertFalse(pad.tripped());
	}

	/** The substation unit is ONAF: its hot spot overshoots on a step of load (k21 = 2) and settles at 20 + 52 + 26. */
	@Test
	void theSubstationUnitOvershootsAndSettlesAtItsDesignHotSpot() {
		TransformerThermal grid = new TransformerThermal(TransformerCatalog.SUBSTATION);
		double peak = 0.0;
		for (int minute = 0; minute < 24 * 60; minute++) {
			run(grid, 1.0, 1);
			peak = Math.max(peak, grid.hotSpot() - grid.topOil());
		}

		assertTrue(peak > 26.5, "gradient peaked at " + peak);
		assertEquals(98.0, grid.hotSpot(), 0.05);
		assertFalse(grid.tripped());
	}

	@Test
	void whatIsSavedIsWhatComesBack() {
		TransformerThermal pad = new TransformerThermal(TransformerCatalog.MACHINE);
		pad.step(30.0, 1.0);
		run(pad, 0.0, 5);

		TransformerThermal back = new TransformerThermal(TransformerCatalog.MACHINE);
		back.restore(pad.state(), pad.trip());
		assertEquals(pad.hotSpot(), back.hotSpot(), 0.0);
		assertEquals(TransformerThermal.Trip.OVERCURRENT, back.trip());

		back.restore(new double[] {Double.NaN, 1.0, 2.0}, null);
		assertEquals(TransformerThermal.Trip.NONE, back.trip());
		assertEquals(TransformerThermal.AMBIENT_C, back.topOil(), 0.0);
	}
}
