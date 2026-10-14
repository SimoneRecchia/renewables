package io.github.simonerecchia.renewables.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.InverterSpec;
import io.github.simonerecchia.renewables.catalog.InverterCatalog;

import org.junit.jupiter.api.Test;

/** The efficiency curve every inverter is given, against the two figures its datasheet prints. */
class InverterEfficiencyTest {
	@Test
	void theCurveReproducesTheDatasheetPeakAndEuropeanFigures() {
		for (InverterSpec spec : InverterCatalog.all()) {
			assertEquals(spec.ratedEuropeanEfficiency(), spec.europeanEfficiency(), 1e-6, spec.displayName());

			// the peak is where the datasheet puts it, and nowhere on the curve is higher
			assertEquals(spec.peakEfficiency(), spec.efficiencyAt(spec.peakLoadFraction()), 1e-9, spec.displayName());
			for (double load = 0.01; load <= 1.0; load += 0.01) {
				assertTrue(spec.efficiencyAt(load) <= spec.peakEfficiency() + 1e-12, spec.displayName() + " at " + load);
			}
		}
	}

	@Test
	void theThreeLossesAreNeverNegative() {
		for (InverterSpec spec : InverterCatalog.all()) {
			for (double p : spec.lossCoefficients()) {
				assertTrue(p >= 0.0, spec.displayName());
			}
		}
	}

	/**
	 * An SG110CX is still 96 % efficient at a twentieth of its rating (98.7 % peak, 98.5 % European); the two-term
	 * curve the mod used before put it at 93.8, because it had to load all the switching loss onto the no-load term.
	 * The small machine's own 98.1 % European figure is what holds it lower.
	 */
	@Test
	void aLightlyLoadedMachineIsStillEfficient() {
		assertEquals(0.9634, InverterCatalog.VX_110.efficiencyAt(0.05), 1e-4);
		for (InverterSpec spec : InverterCatalog.all()) {
			assertTrue(spec.efficiencyAt(0.05) > 0.92, spec.displayName() + " " + spec.efficiencyAt(0.05));
			assertTrue(spec.cecEfficiency() > spec.europeanEfficiency() - 0.003, spec.displayName());
		}
	}

	/** A machine past its derating onset gives up a quarter of its rating by 60 °C: the SG350HX's 320 kVA at 40 °C. */
	@Test
	void theDeratingFollowsTheDatasheet() {
		assertEquals(322.7, InverterCatalog.VX_350.thermalLimitKw(40.0), 0.1);
		assertEquals(293.3, InverterCatalog.VX_350.thermalLimitKw(50.0), 0.1);
		assertEquals(100.8, InverterCatalog.VX_110.thermalLimitKw(50.0), 0.1);
		assertEquals(0.75 * 2500.0, InverterCatalog.VC_2500.thermalLimitKw(60.0), 1e-9);
	}

	/** At a power factor of 0.8 the bridge's kVA rating leaves 0.8 of it for active power. */
	@Test
	void aPowerFactorUnderOneTakesItsShareOfTheRating() {
		InverterSpec spec = InverterCatalog.VX_110;
		assertEquals(spec.apparentPowerKva(), spec.activePowerCeilingKw(1.0), 1e-9);
		assertEquals(spec.apparentPowerKva() * 0.8, spec.activePowerCeilingKw(0.8), 1e-9);
		// and no further than the machine's own limit
		assertEquals(spec.apparentPowerKva() * spec.minPowerFactor(), spec.activePowerCeilingKw(0.1), 1e-9);
	}
}
