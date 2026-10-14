package io.github.simonerecchia.renewables.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.InverterSpec;
import io.github.simonerecchia.renewables.catalog.InverterCatalog;

import org.junit.jupiter.api.Test;

/** What an inverter puts out for what it is offered: the inverse of its own loss curve, and nothing below idle. */
class InverterOutputTest {
	/** Output plus the losses at that output is the input, all the way up the curve and past the nameplate. */
	@Test
	void whatComesOutPlusItsLossesIsWhatWentIn() {
		for (InverterSpec spec : InverterCatalog.all()) {
			for (double offered = 0.01; offered <= 1.3; offered += 0.01) {
				double dc = offered * spec.acPowerKw();
				double ac = spec.unclippedAcKw(dc);
				if (ac <= 0.0) continue;

				assertEquals(dc, ac + spec.lossKwAt(ac), 1e-9 * spec.acPowerKw(), spec.displayName() + " at " + offered);
			}
		}
	}

	/**
	 * The central machine idles on its no-load loss: offered nine tenths of that it makes nothing, where the old
	 * fixed-point guess put out a third of what came in; offered a kilowatt more than it, a little under that kilowatt.
	 */
	@Test
	void belowItsNoLoadLossTheBridgeDoesNotStart() {
		InverterSpec central = InverterCatalog.VC_2500;
		double idle = central.noLoadLossFraction() * central.acPowerKw();
		assertTrue(idle > 1.0, "a central inverter idles on kilowatts: " + idle);

		assertEquals(0.0, central.unclippedAcKw(idle * 0.9), 0.0);
		assertEquals(0.0, central.unclippedAcKw(idle), 0.0);

		double justOver = central.unclippedAcKw(idle + 1.0);
		assertTrue(justOver > 0.95 && justOver < 1.0, "one kilowatt over idle gives " + justOver);
	}

	/** At the top of the curve the inverse agrees with the forward efficiency the panel reads. */
	@Test
	void atNameplateTheEfficiencyIsTheCurves() {
		for (InverterSpec spec : InverterCatalog.all()) {
			double dc = spec.acPowerKw() / spec.efficiencyAt(1.0);
			assertEquals(spec.acPowerKw(), spec.unclippedAcKw(dc), 1e-6 * spec.acPowerKw(), spec.displayName());
		}
	}
}
