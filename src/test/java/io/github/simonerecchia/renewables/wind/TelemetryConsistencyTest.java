package io.github.simonerecchia.renewables.wind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.Telemetry;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.api.power.TurbineTelemetry;
import io.github.simonerecchia.renewables.catalog.TurbineCatalog;
import io.github.simonerecchia.renewables.telemetry.TurbineTelemetrySimulator;

import org.junit.jupiter.api.Test;

/**
 * The telemetry agrees with the machine and with itself.  The pitch the machine draws and the pitch it reports are one
 * figure, worked out once by {@link TurbineDynamics#collectivePitch} and handed to the simulator (a drift between two
 * copies would be a rotor feathered on screen while the SCADA says it is working); the brakes, the cooling water and the
 * slow temperatures read the way a real machine's do.
 */
class TelemetryConsistencyTest {
	private static final TurbineSpec C130 = TurbineCatalog.C130_40;

	private static Telemetry.Snapshot sample(TurbineSpec spec, boolean braked, double alignedWind) {
		return new TurbineTelemetrySimulator().sample(sample(spec, 1000.0, braked, false, false, alignedWind, 2.0, false, 1000L));
	}

	/** One tick of a C130's readings, with the pitch the dynamics give for them. */
	private static TurbineTelemetrySimulator.Sample sample(TurbineSpec spec, double powerKw, boolean braked, boolean cutOut,
			boolean parked, double alignedWind, double degreesPerTick, boolean yawing, long gameTime) {
		double pitch = TurbineDynamics.collectivePitch(braked, alignedWind, spec.powerWhileRunningKw(alignedWind, 1.225), spec.ratedPowerKw(), spec);
		return new TurbineTelemetrySimulator.Sample(
				powerKw, spec.ratedPowerKw(), spec.ratedPowerKw(), false,
				alignedWind, alignedWind, 90.0, 90.0, 0.1, degreesPerTick, pitch,
				braked, cutOut, parked, false, false, yawing,
				15.0, 1013.25, false, false, gameTime, 7, 0.0, spec.gearboxRatio());
	}

	@Test
	void sixtyFourSignals() {
		assertEquals(64, TurbineTelemetry.kinds().size());
		assertEquals(64, sample(TurbineCatalog.C130_40, false, 10.0).values().size());
	}

	@Test
	void pitchMatchesTheSimulatorAcrossTheWholeWindRange() {
		for (TurbineSpec spec : TurbineCatalog.all()) {
			for (double wind = 0.0; wind <= 30.0; wind += 0.5) {
				double reported = sample(spec, false, wind).number(TurbineTelemetry.BLADE_PITCH_ANGLE);
				assertEquals(reported, TurbineDynamics.collectivePitch(false, wind, spec), 0.006, spec.displayName() + " at " + wind);
			}

			assertEquals(90.0, sample(spec, true, 12.0).number(TurbineTelemetry.BLADE_PITCH_ANGLE), 1e-9);
			assertEquals(90.0, TurbineDynamics.collectivePitch(true, 12.0, spec), 1e-9);
		}
	}

	@Test
	void rotorRpmInTheTelemetryIsTheRealSpeedInDegreesPerTickOverZeroPointThree() {
		TurbineSpec spec = TurbineCatalog.C130_40;
		double reported = sample(spec, false, 10.0).number(TurbineTelemetry.ROTOR_RPM);
		assertEquals(TurbineDynamics.rpmOf(2.0), reported, 0.006);
		assertTrue(reported > 0.0);
	}

	@Test
	void nacelleAndWindHeadingsAreReportedInTheSameCompass() {
		Telemetry.Snapshot snapshot = sample(TurbineCatalog.C130_40, false, 8.0);
		assertEquals(snapshot.number(TurbineTelemetry.WIND_DIR), snapshot.number(TurbineTelemetry.NACELLE_DIR), 1e-9);
	}

	/**
	 * In a gale the machine cuts out and idles, feathered, at 2.1 rpm: the review read 178.8 bar on the main brake beside a
	 * turning rotor and a gearbox pumping oil.  The parking brake is for a rotor that has been stopped and has run down.
	 */
	@Test
	void theMainBrakeHoldsOnlyAParkedRotorThatHasRunDown() {
		double idling = 2.1 * TurbineSpec.DEGREES_PER_TICK_PER_RPM;
		Telemetry.Snapshot cutOut = new TurbineTelemetrySimulator().sample(sample(C130, 0.0, true, true, false, 26.0, idling, false, 1000L));
		assertTrue(cutOut.number(TurbineTelemetry.HYDR_MAIN_BRAKES_PRESS) < 10.0);
		assertTrue(cutOut.number(TurbineTelemetry.ROTOR_RPM) > 2.0);
		assertEquals(90.0, cutOut.number(TurbineTelemetry.BLADE_PITCH_ANGLE), 1e-9);

		Telemetry.Snapshot slowing = new TurbineTelemetrySimulator().sample(sample(C130, 0.0, true, false, true, 8.0, 5.0 * TurbineSpec.DEGREES_PER_TICK_PER_RPM, false, 1000L));
		assertTrue(slowing.number(TurbineTelemetry.HYDR_MAIN_BRAKES_PRESS) < 10.0);

		Telemetry.Snapshot parked = new TurbineTelemetrySimulator().sample(sample(C130, 0.0, true, false, true, 8.0, 0.0, false, 1000L));
		assertTrue(parked.number(TurbineTelemetry.HYDR_MAIN_BRAKES_PRESS) > 170.0);
	}

	/** The yaw brakes clamp the nacelle at rest and are let down to a drag while it turns; it read the other way round. */
	@Test
	void theYawBrakesAreFullAtRestAndLetDownWhileYawing() {
		double atRest = new TurbineTelemetrySimulator().sample(sample(C130, 2000.0, false, false, false, 9.0, 3.0, false, 1000L))
				.number(TurbineTelemetry.YAW_HYDR_BRK_PRESS);
		double yawing = new TurbineTelemetrySimulator().sample(sample(C130, 2000.0, false, false, false, 9.0, 3.0, true, 1000L))
				.number(TurbineTelemetry.YAW_HYDR_BRK_PRESS);
		assertTrue(atRest > 100.0, "at rest " + atRest);
		assertTrue(yawing < 45.0, "yawing " + yawing);
		assertTrue(atRest < new TurbineTelemetrySimulator().sample(sample(C130, 2000.0, false, false, false, 9.0, 3.0, false, 1000L))
				.number(TurbineTelemetry.YAW_H_ACCU_PRESS), "a brake is fed from the accumulator and cannot beat it");
	}

	/** For about fourteen seconds after loading the outlet ran colder than the inlet, by up to 0.23 C. */
	@Test
	void theCoolingWaterNeverLeavesColderThanItCameIn() {
		TurbineTelemetrySimulator simulator = new TurbineTelemetrySimulator();
		for (long tick = 0; tick < 2000; tick++) {
			double power = tick < 1000 ? C130.ratedPowerKw() : 0.0;
			Telemetry.Snapshot t = simulator.sample(sample(C130, power, false, false, false, 12.0, 3.0, false, tick));
			assertTrue(t.number(TurbineTelemetry.GEN_CW_TEMP_OUTLET) > t.number(TurbineTelemetry.GEN_CW_TEMP_INLET), "tick " + tick);
		}
	}

	/** Worked out once a second, the slow temperatures come out where they would have every tick. */
	@Test
	void theSlowTemperaturesDoNotDependOnHowOftenTheyAreRead() {
		TurbineTelemetrySimulator everyTick = new TurbineTelemetrySimulator();
		TurbineTelemetrySimulator everySecond = new TurbineTelemetrySimulator();
		for (long tick = 0; tick <= 2000; tick++) {
			TurbineTelemetrySimulator.Sample s = sample(C130, 3000.0, false, false, false, 11.0, 3.0, false, tick);
			Telemetry.Snapshot fine = everyTick.sample(s);
			if (tick % 20 != 0) continue;

			Telemetry.Snapshot coarse = everySecond.sample(s);
			for (String tag : new String[] {TurbineTelemetry.GEARBOX_OIL_TEMP, TurbineTelemetry.GENERATOR_L1_TEMP,
					TurbineTelemetry.GEN_CW_TEMP_OUTLET, TurbineTelemetry.NACELLE_TEMP}) {
				assertEquals(fine.number(tag), coarse.number(tag), 1e-9, tag + " at " + tick);
			}
		}
	}

	/** What the machine reports as its pitch is the pitch it is at, curtailment included. */
	@Test
	void theReportedPitchIsTheMachinesOwn() {
		double held = TurbineDynamics.collectivePitch(false, 9.0, C130.powerWhileRunningKw(9.0, 1.225), 1000.0, C130);
		TurbineTelemetrySimulator.Sample s = sample(C130, 1000.0, false, false, false, 9.0, 2.0, false, 1000L);
		TurbineTelemetrySimulator.Sample curtailed = new TurbineTelemetrySimulator.Sample(s.activePowerKw(), s.ratedPowerKw(), 1000.0, true,
				s.windSpeed(), s.alignedWindSpeed(), s.windDirection(), s.nacelleDir(), s.turbulence(), s.rotorDegreesPerTick(), held,
				false, false, false, false, false, false, 15.0, 1013.25, false, false, 1000L, 7, 0.0, C130.gearboxRatio());
		// the snapshot rounds to the hundredth
		assertEquals(held, new TurbineTelemetrySimulator().sample(curtailed).number(TurbineTelemetry.BLADE_PITCH_ANGLE), 0.006);
		assertTrue(held > 0.0);
	}
}
