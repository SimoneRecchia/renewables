package io.github.simonerecchia.renewables.power;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.simonerecchia.renewables.api.power.DcCableSpec;
import io.github.simonerecchia.renewables.catalog.CableCatalog;

import org.junit.jupiter.api.Test;

/**
 * The copper loss of a run, from the cable's resistance at 20 °C corrected to the temperature the current heats it
 * to: R(T) = R20 (1 + 0.00393 (T - 20)), T = ambient + 60 K (I / Iz)^2.  Every figure worked out by hand.
 */
class DcCableLossTest {
	private static final DcCableSpec STRING = CableCatalog.STRING_6;
	private static final DcCableSpec TRUNK = CableCatalog.TRUNK_240;

	/** At its rating the conductor reaches the 120 °C its insulation is rated for, and 3.39 ohm/km becomes 4.722. */
	@Test
	void aCableAtItsRatingRunsAtTheInsulationsLimit() {
		assertEquals(120.0, STRING.conductorTemperature(60.0, 70.0, 0.0), 1e-9);
		assertEquals(4.72227, STRING.ohmsPerKmAt(120.0), 1e-5);
		assertEquals(3.39, STRING.ohmsPerKmAt(20.0), 1e-12);
	}

	/**
	 * A string of 22 HP-108-415 modules (695.4 V, 13.13 A at its maximum power point) down 32 blocks of string
	 * cable at 25 °C: the copper is at 27.11 °C and 3.4847 ohm/km, the 640 m loop is 2.2302 ohm, it drops 29.28 V
	 * and burns 4.21 % of the string.
	 */
	@Test
	void aLongStringRunBurnsFourPercent() {
		double conductor = STRING.conductorTemperature(25.0, 13.13, 0.0);
		assertEquals(27.111, conductor, 1e-3);
		assertEquals(2.23023, STRING.loopResistance(320.0, conductor), 1e-5);
		assertEquals(29.2829, STRING.voltageDrop(13.13, 320.0, conductor), 1e-4);
		assertEquals(0.042108, STRING.lossFraction(13.13, 695.42, 320.0, conductor), 1e-6);
	}

	/**
	 * Twenty strings of HN-144-580 (272.4 A at 767 V) down ten blocks of trunk at 30 °C: 37.41 °C in the copper,
	 * 0.08729 ohm/km, and 0.62 % lost - which is why a combiner box is worth having.
	 */
	@Test
	void aTrunkCarriesTwentyStringsForUnderOnePercent() {
		double conductor = TRUNK.conductorTemperature(30.0, 272.4, 0.0);
		assertEquals(37.412, conductor, 1e-3);
		assertEquals(0.0062003, TRUNK.lossFraction(272.4, 767.0, 100.0, conductor), 1e-7);
	}

	@Test
	void aBuriedRunCarriesLess() {
		assertEquals(70.0, STRING.ampacity(0.0), 1e-12);
		assertEquals(70.0 * 0.86, STRING.ampacity(0.5), 1e-12);
	}
}
