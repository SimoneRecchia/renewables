package io.github.simonerecchia.renewables.power;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.simonerecchia.renewables.api.power.PvModuleSpec;
import io.github.simonerecchia.renewables.catalog.PvCatalog;

import org.junit.jupiter.api.Test;

/** A module's maximum power point moves with temperature the way its own power coefficient says. */
class PvModuleTemperatureTest {
	/**
	 * Vmp x Imp at a hot cell is the nameplate derated by gamma, to the half percent the two linear coefficients leave
	 * at 75 °C.  With Vmp on the Voc coefficient the product ran 4-7 % over the power there, which is what the string
	 * voltage, the drop down the run and the MPPT window all read.
	 */
	@Test
	void theMaximumPowerPointMultipliesOutToTheDeratedPower() {
		for (var array : PvCatalog.all()) {
			PvModuleSpec module = array.module();
			for (double cell = -10.0; cell <= 75.0; cell += 5.0) {
				double product = module.vmpAt(cell) * module.impAt(PvModuleSpec.STC_IRRADIANCE, cell);
				double derated = module.vmp() * module.imp() * module.temperatureDerate(cell);
				assertEquals(derated, product, derated * 0.01, module.displayName() + " at " + cell + " °C");
			}
		}
	}

	/** The 700 W TOPCon module's Vmp falls by gamma - alpha, -0.33 %/K: 448 V for a string of thirteen at 70 °C, not 470. */
	@Test
	void aHotStringSitsLowerThanItsOpenCircuitCoefficientSays() {
		var hx700 = PvCatalog.HX_700;
		assertEquals(-0.0033, hx700.module().vmpCoefficient(), 1e-9);
		assertEquals(13 * 40.50 * (1.0 - 0.0033 * 45.0), hx700.stringVoltage(70.0), 1e-9);
	}
}
