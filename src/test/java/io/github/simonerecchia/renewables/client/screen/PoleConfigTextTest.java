package io.github.simonerecchia.renewables.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class PoleConfigTextTest {
	@Test
	void degreesAlwaysUseAPointEvenInALanguageThatWritesACommaAndReadBack() {
		Locale previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.ITALY);
			String text = PoleConfigText.degrees((float) Math.toRadians(12.5));

			assertEquals("12.50", text);
			assertEquals(12.5f, PoleConfigText.parse(text));
		} finally {
			Locale.setDefault(previous);
		}
	}

	@Test
	void offsetsAreShortPlainNumbersNeverWithAnExponent() {
		assertEquals("0", PoleConfigText.offset(0.0f));
		assertEquals("0", PoleConfigText.offset(-0.0f));
		assertEquals("0.5", PoleConfigText.offset(0.5f));
		assertEquals("-1", PoleConfigText.offset(-1.0f));
		assertEquals("0.0001", PoleConfigText.offset(1.0e-4f));
		assertEquals("0", PoleConfigText.offset(1.0e-6f));
		assertEquals("0.3333", PoleConfigText.offset(1.0f / 3.0f));
		assertEquals("0", PoleConfigText.offset(Float.NaN));
	}

	@Test
	void plainDecimalsParseWithEitherSeparator() {
		assertEquals(1.0f, PoleConfigText.parse("1"));
		assertEquals(-0.25f, PoleConfigText.parse("-0.25"));
		assertEquals(0.5f, PoleConfigText.parse("0,5"));
		assertEquals(0.5f, PoleConfigText.parse(" .5 "));
		assertEquals(10.0f, PoleConfigText.parse("1e1"));
		assertEquals(3.0f, PoleConfigText.parse("+3."));
	}

	@Test
	void nothingElseParsesSoTheFieldKeepsItsLastGoodValue() {
		for (String bad : new String[] {"", "-", ".", "abc", "1.2.3", "NaN", "Infinity", "-Infinity", "0x10", "1f", "1d", "1e", "1e999", "--1"}) {
			assertNull(PoleConfigText.parse(bad), bad);
		}
	}

	@Test
	void onlyNumberCharactersMayBeTyped() {
		assertTrue(PoleConfigText.allowed("-12.5e3"));
		assertTrue(PoleConfigText.allowed(""));
		assertFalse(PoleConfigText.allowed("NaN"));
		assertFalse(PoleConfigText.allowed("1 2"));
	}

	@Test
	void clampKeepsAValueWithinPlusOrMinusTheLimit() {
		assertEquals(1.0f, PoleConfigText.clamp(7.0f, 1.0f));
		assertEquals(-1.0f, PoleConfigText.clamp(-7.0f, 1.0f));
		assertEquals(0.25f, PoleConfigText.clamp(0.25f, 1.0f));
	}
}
