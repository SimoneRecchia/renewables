package io.github.simonerecchia.renewables.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.InverterSpec;
import io.github.simonerecchia.renewables.api.power.PvArraySpec;
import io.github.simonerecchia.renewables.api.power.StringCurve;
import io.github.simonerecchia.renewables.catalog.InverterCatalog;
import io.github.simonerecchia.renewables.catalog.PvCatalog;

import org.junit.jupiter.api.Test;

/** Where a tracker holds strings that do not share a knee, and what each then gives. */
class StringCurveTest {
	@Test
	void theCurveIsTheMaximumAtTheKneeAndNothingPastOpenCircuit() {
		assertEquals(1.0, StringCurve.powerFraction(500.0, 500.0, 600.0), 1e-12);
		assertEquals(0.5, StringCurve.powerFraction(250.0, 500.0, 600.0), 1e-12);
		assertEquals(0.0, StringCurve.powerFraction(600.0, 500.0, 600.0), 0.0);
		assertEquals(0.0, StringCurve.powerFraction(900.0, 500.0, 600.0), 0.0);
		// past the knee it falls, and never above the maximum
		for (double v = 500.0; v <= 600.0; v += 5.0) {
			assertTrue(StringCurve.powerFraction(v, 500.0, 600.0) <= 1.0 + 1e-12);
		}
	}

	/**
	 * One central tracker, a thin-film rack at 1121 V and a flat table at 460 V on the same bus: the bus sits at the
	 * rack's knee and the table, held at twice its open-circuit voltage, gives nothing - the old code paid it in full.
	 */
	@Test
	void onOneBusALowStringIsPushedPastItsOpenCircuit() {
		InverterSpec central = InverterCatalog.VC_2500;
		PvArraySpec rack = PvCatalog.TR_530;
		PvArraySpec table = PvCatalog.FT_430;
		double[] vmp = {rack.stringVoltage(25.0), table.stringVoltage(25.0)};
		double[] voc = {rack.stringOpenCircuitVoltage(25.0), table.stringOpenCircuitVoltage(25.0)};
		double[] power = {rack.dcPowerKw(), table.dcPowerKw()};

		double bus = StringCurve.bestBusVoltage(vmp, voc, power, central.mpptMinVolts(), central.mpptMaxVolts());
		assertEquals(vmp[0], bus, 1e-9);
		assertEquals(1.0, StringCurve.powerFraction(bus, vmp[0], voc[0]), 1e-12);
		assertEquals(0.0, StringCurve.powerFraction(bus, vmp[1], voc[1]), 0.0);
	}

	/** Two strings a little apart: the bus settles between their knees only if that beats either knee. */
	@Test
	void aSharedBusMakesTheMostItCan() {
		double[] vmp = {700.0, 760.0};
		double[] voc = {840.0, 910.0};
		double[] power = {10.0, 10.0};
		double bus = StringCurve.bestBusVoltage(vmp, voc, power, 500.0, 1500.0);
		double best = StringCurve.totalAt(bus, vmp, voc, power);
		for (double v = 500.0; v <= 1500.0; v += 1.0) {
			assertTrue(StringCurve.totalAt(v, vmp, voc, power) <= best + 1e-3, "at " + v);
		}

		assertTrue(best < 20.0);
	}

	/** On a tracker of its own a string past the window is held at the window's edge, and gives what it gives there. */
	@Test
	void aStringOnItsOwnTrackerIsHeldAtTheWindowsEdge() {
		assertEquals(1000.0, StringCurve.ownTrackerVolts(1050.0, 200.0, 1000.0), 0.0);
		assertEquals(1000.0 / 1050.0, StringCurve.powerFraction(1000.0, 1050.0, 1250.0), 1e-12);
		assertEquals(650.0, StringCurve.ownTrackerVolts(650.0, 200.0, 1000.0), 0.0);
		// below the window and past its own open circuit: nothing
		assertEquals(0.0, StringCurve.powerFraction(StringCurve.ownTrackerVolts(400.0, 500.0, 1500.0), 400.0, 480.0), 0.0);
	}
}
