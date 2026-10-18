package io.github.simonerecchia.renewables.wind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.WorldConditions;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.catalog.TurbineCatalog;

import org.junit.jupiter.api.Test;

/** The datasheet numbers of the six models and the power curve they share, against the values the specification gives. */
class TurbineSpecTest {
	private static final double RHO = WorldConditions.REFERENCE_AIR_DENSITY;

	@Test
	void sixModelsInCatalogueOrder() {
		assertEquals(6, TurbineCatalog.all().size());
		assertEquals("SW-10", TurbineCatalog.all().get(0).displayName());
		assertEquals("C130-4.0", TurbineCatalog.all().get(5).displayName());
	}

	@Test
	void ratedWindOfEveryModel() {
		assertEquals(13.91, TurbineCatalog.SW_10.ratedSpeed(), 0.01);
		assertEquals(14.89, TurbineCatalog.C52_085.ratedSpeed(), 0.01);
		assertEquals(14.64, TurbineCatalog.C80_20.ratedSpeed(), 0.01);
		assertEquals(15.49, TurbineCatalog.C90_30.ratedSpeed(), 0.01);
		assertEquals(13.30, TurbineCatalog.C112_30.ratedSpeed(), 0.01);
		assertEquals(13.25, TurbineCatalog.C130_40.ratedSpeed(), 0.01);
	}

	@Test
	void drawingScalesOfEveryModel() {
		// the redrawn model's rotor is exactly 13 blocks across, so the C130 is 1:1 and the others are D / 130
		assertEquals(0.1777, TurbineCatalog.SW_10.renderScale(), 0.0001);
		assertEquals(0.4000, TurbineCatalog.C52_085.renderScale(), 0.0001);
		assertEquals(0.6154, TurbineCatalog.C80_20.renderScale(), 0.0001);
		assertEquals(0.6923, TurbineCatalog.C90_30.renderScale(), 0.0001);
		assertEquals(0.8615, TurbineCatalog.C112_30.renderScale(), 0.0001);
		assertEquals(1.0000, TurbineCatalog.C130_40.renderScale(), 0.0001);
		assertEquals(13.0, TurbineSpec.MODEL_ROTOR_DIAMETER_BLOCKS, 0.0);
		assertEquals(13.0, TurbineSpec.MODEL_TOWER_HEIGHT_BLOCKS, 0.0);

		// the head is drawn twice the rotor's scale, and the small machine's never under a quarter
		assertEquals(0.3554, TurbineCatalog.SW_10.nacelleRenderScale(), 0.0001);
		assertEquals(2.0 * TurbineCatalog.C90_30.renderScale(), TurbineCatalog.C90_30.nacelleRenderScale(), 1e-9);
		assertEquals(2.0, TurbineCatalog.C130_40.nacelleRenderScale(), 1e-9);
	}

	@Test
	void gearboxAndHubHeights() {
		// a 10 kW machine is direct drive: its alternator turns with the rotor
		assertEquals(1.0, TurbineCatalog.SW_10.gearboxRatio(), 0.0);
		// 1680 rpm at the rated rotor speed: the V80's 1:100.5 and the V90's 1:104.5 come out as they are built
		assertEquals(64.6, TurbineCatalog.C52_085.gearboxRatio(), 0.05);
		assertEquals(100.6, TurbineCatalog.C80_20.gearboxRatio(), 0.05);
		assertEquals(104.3, TurbineCatalog.C90_30.gearboxRatio(), 0.05);
		assertEquals(131.25, TurbineCatalog.C112_30.gearboxRatio(), 0.05);
		assertEquals(133.3, TurbineCatalog.C130_40.gearboxRatio(), 0.05);

		assertEquals(20.0, TurbineCatalog.SW_10.hubHeightM(2), 1e-9);
		assertEquals(40.0, TurbineCatalog.SW_10.hubHeightM(4), 1e-9);
		assertEquals(90.0, TurbineCatalog.C130_40.hubHeightM(9), 1e-9);
		assertEquals(130.0, TurbineCatalog.C130_40.hubHeightM(13), 1e-9);
		// a tower outside the range is read as the nearest height the model accepts
		assertEquals(130.0, TurbineCatalog.C130_40.hubHeightM(20), 1e-9);
	}

	@Test
	void towerRanges() {
		assertTrue(TurbineCatalog.C130_40.acceptsTowerHeight(9));
		assertTrue(TurbineCatalog.C130_40.acceptsTowerHeight(13));
		assertFalse(TurbineCatalog.C130_40.acceptsTowerHeight(8));
		assertFalse(TurbineCatalog.C130_40.acceptsTowerHeight(14));
		assertTrue(TurbineCatalog.SW_10.acceptsTowerHeight(2));
		assertFalse(TurbineCatalog.SW_10.acceptsTowerHeight(5));
	}

	@Test
	void c130PowerCurve() {
		TurbineSpec c130 = TurbineCatalog.C130_40;
		assertEquals(0.0, c130.powerWhileRunningKw(2.9, RHO), 1e-9);
		assertEquals(1929.0, c130.powerWhileRunningKw(8.0, RHO), 1.0);
		assertEquals(3284.0, c130.powerWhileRunningKw(10.0, RHO), 1.0);
		assertEquals(3979.0, c130.powerWhileRunningKw(14.0, RHO), 1.0);
		assertEquals(3999.9, c130.powerWhileRunningKw(22.0, RHO), 0.1);
		// storm control is a straight ramp from the onset (22.5) to the cut-out (25)
		assertEquals(3200.0, c130.powerWhileRunningKw(23.0, RHO), 1.0);
		assertEquals(1600.0, c130.powerWhileRunningKw(24.0, RHO), 1.0);
		assertEquals(0.0, c130.powerWhileRunningKw(25.0, RHO), 1e-9);
	}

	@Test
	void smallWindPowerCurve() {
		TurbineSpec sw10 = TurbineCatalog.SW_10;
		assertEquals(1.0, sw10.powerWhileRunningKw(5.0, RHO), 0.1);
		assertEquals(7.5, sw10.powerWhileRunningKw(10.0, RHO), 0.1);
		assertEquals(9.9, sw10.powerWhileRunningKw(14.0, RHO), 0.1);
		assertEquals(10.0, sw10.powerWhileRunningKw(24.0, RHO), 0.1);
		assertEquals(0.0, sw10.powerWhileRunningKw(25.0, RHO), 1e-9);
	}

	@Test
	void stormControlOnMachinesWithOnsetAtTwentyTwo() {
		// the fraction shed is linear between the onset and the cut-out: two thirds and one third at 23 and 24 m/s
		TurbineSpec c90 = TurbineCatalog.C90_30;
		assertEquals(2.0 / 3.0, c90.stormDerating(23.0), 1e-9);
		assertEquals(1.0 / 3.0, c90.stormDerating(24.0), 1e-9);
		assertEquals(1.0, c90.stormDerating(21.9), 1e-9);
		assertEquals(0.0, c90.stormDerating(25.0), 1e-9);
	}

	@Test
	void machineWithoutStormControlHasNoDerating() {
		// the SW-10's onset is its cut-out
		assertEquals(1.0, TurbineCatalog.SW_10.stormDerating(24.9), 1e-9);
	}

	@Test
	void thinnerAirGivesLessPower() {
		TurbineSpec c80 = TurbineCatalog.C80_20;
		assertTrue(c80.powerWhileRunningKw(9.0, 1.0) < c80.powerWhileRunningKw(9.0, 1.225));
	}

	@Test
	void drawnRotorAtRatedSpeedIsSevenPointTwoDegreesPerTick() {
		for (TurbineSpec spec : TurbineCatalog.all()) {
			double atRatedRpm = spec.ratedRotorRpm() * TurbineSpec.DEGREES_PER_TICK_PER_RPM;
			assertEquals(7.2, spec.drawnRotation(atRatedRpm), 1e-9, spec.displayName());
			assertEquals(3.6, spec.drawnRotation(atRatedRpm / 2.0), 1e-9, spec.displayName());
		}
	}

	/** At the rated rotor speed the utility machines' tips run at 70-86 m/s, as onshore machines do (V90 76, N131 86). */
	@Test
	void ratedTipSpeedsAreThoseOfRealMachines() {
		assertEquals(70.8, TurbineCatalog.C52_085.ratedTipSpeed(), 0.1);
		assertEquals(70.0, TurbineCatalog.C80_20.ratedTipSpeed(), 0.1);
		assertEquals(75.9, TurbineCatalog.C90_30.ratedTipSpeed(), 0.1);
		assertEquals(75.1, TurbineCatalog.C112_30.ratedTipSpeed(), 0.1);
		assertEquals(85.8, TurbineCatalog.C130_40.ratedTipSpeed(), 0.1);
	}

	/** In steady running the generator's torque never limits the curve: only a rotor still spinning up is held back. */
	@Test
	void theRatedTorqueNeverClipsTheSteadyCurve() {
		for (TurbineSpec spec : TurbineCatalog.all()) {
			for (double v = spec.cutInSpeed(); v < spec.stormOnsetSpeed(); v += 0.25) {
				double curve = spec.powerWhileRunningKw(v, RHO);
				assertTrue(curve <= spec.torqueCeilingKw(spec.rotorRpmAt(v)) + 1e-9, spec.displayName() + " at " + v);
			}
		}
	}
}
