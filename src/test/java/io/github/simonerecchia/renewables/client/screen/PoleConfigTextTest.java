package io.github.simonerecchia.renewables.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.net.packet.PoleConfigPayload;

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

	@Test
	void aNumberOutsideTheRangeIsToldApartFromOneInside() {
		// typed 5 in an offset, 270 in yaw, 60 in pitch: what goes is 1, 180 and 45, so the field has to say so
		assertFalse(PoleConfigText.within(5.0f, PoleConfigPayload.MAX_OFFSET));
		assertTrue(PoleConfigText.within(-1.0f, PoleConfigPayload.MAX_OFFSET));
		assertTrue(PoleConfigText.within(0.25f, PoleConfigPayload.MAX_OFFSET));

		assertFalse(PoleConfigText.withinDegrees(270.0f, PoleConfigPayload.MAX_YAW));
		assertTrue(PoleConfigText.withinDegrees(-180.0f, PoleConfigPayload.MAX_YAW));
		assertFalse(PoleConfigText.withinDegrees(60.0f, PoleConfigPayload.MAX_PITCH));
		assertTrue(PoleConfigText.withinDegrees(45.0f, PoleConfigPayload.MAX_PITCH));
		assertTrue(PoleConfigText.withinDegrees(-45.0f, PoleConfigPayload.MAX_PITCH));
	}

	@Test
	void theFieldsAndTheirLabelsStayOnTheNarrowestScreen() {
		// "Offset X:" and "Pitch (deg):" in the vanilla font, "±1" and "±180°" beside the fields (check_gui_fits' widths)
		int screen = 320;
		int field = 60;
		int leftLabel = 44;
		int leftRange = 15;
		int rightLabel = 56;
		int rightRange = 35;
		PoleConfigText.Layout layout = PoleConfigText.layout(screen, field, leftLabel, leftRange, rightLabel, rightRange);

		int leftLabelStart = layout.leftField() - PoleConfigText.LABEL_GAP - leftLabel;
		int leftRangeEnd = layout.leftField() + field + PoleConfigText.LABEL_GAP + leftRange;
		int rightLabelStart = layout.rightField() - PoleConfigText.LABEL_GAP - rightLabel;
		int rightRangeEnd = layout.rightField() + field + PoleConfigText.LABEL_GAP + rightRange;

		assertTrue(leftLabelStart >= 0, "the left labels start on screen: " + leftLabelStart);
		assertTrue(leftRangeEnd < rightLabelStart, "the columns do not overlap");
		assertTrue(rightRangeEnd <= screen, "the right ranges end on screen: " + rightRangeEnd);
		// centred: as much room either side
		assertTrue(Math.abs(leftLabelStart - (screen - rightRangeEnd)) <= 1);
	}

	@Test
	void aScreenTooNarrowForBothColumnsStartsAtItsLeftEdge() {
		PoleConfigText.Layout layout = PoleConfigText.layout(200, 60, 44, 15, 56, 35);
		assertEquals(PoleConfigText.LABEL_GAP + 44, layout.leftField());
	}
}
