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
 * The pitch the machine draws and the pitch it reports are the same curve: one is worked out on the client from
 * synced values and the other by the telemetry simulator, and a drift between the two would be a rotor that is
 * feathered on screen while the SCADA says it is working.
 */
class TelemetryConsistencyTest {
	private static Telemetry.Snapshot sample(TurbineSpec spec, boolean braked, double alignedWind) {
		return new TurbineTelemetrySimulator().sample(new TurbineTelemetrySimulator.Sample(
				1000.0, spec.ratedPowerKw(), spec.ratedPowerKw(), false,
				alignedWind, alignedWind, 90.0, 90.0, 0.1, 2.0,
				braked, false, false, false, false, false,
				15.0, 1013.25, false, false, 1000L, 7, 0.0,
				spec.ratedSpeed(), spec.stormOnsetSpeed(), spec.cutOutSpeed(), spec.gearboxRatio()));
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
}
