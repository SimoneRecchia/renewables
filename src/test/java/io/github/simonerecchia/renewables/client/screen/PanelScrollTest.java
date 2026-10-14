package io.github.simonerecchia.renewables.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The default 854x480 window at automatic scale is 240 GUI pixels tall, and the inverter's panel is 252: centred, it
 * lost six pixels at the top and six at the bottom, the border and the first line of text with them.
 */
class PanelScrollTest {
	private static final int SMALLEST_SCREEN = 240;
	private static final int INVERTER = 252;
	private static final int ARRAY = 244;

	@Test
	void aPanelThatFitsIsCentredAsBefore() {
		assertEquals((480 - INVERTER) / 2, new PanelScroll().top(480, INVERTER));
		assertEquals((SMALLEST_SCREEN - 208) / 2, new PanelScroll().top(SMALLEST_SCREEN, 208));
	}

	@Test
	void aPanelThatDoesNotFitStartsWithItsTopOnScreen() {
		assertEquals(0, new PanelScroll().top(SMALLEST_SCREEN, INVERTER));
		assertEquals(0, new PanelScroll().top(SMALLEST_SCREEN, ARRAY));
	}

	@Test
	void theWheelBringsTheBottomOnScreenAndNoFurther() {
		PanelScroll scroll = new PanelScroll();
		scroll.top(SMALLEST_SCREEN, INVERTER);

		for (int notch = 0; notch < 5; notch++) {
			scroll.wheel(-1.0, SMALLEST_SCREEN, INVERTER);
		}

		int top = scroll.top(SMALLEST_SCREEN, INVERTER);
		assertEquals(SMALLEST_SCREEN, top + INVERTER, "the bottom edge sits on the bottom of the screen");
	}

	@Test
	void theWheelBackBringsTheTopBackAndNoFurther() {
		PanelScroll scroll = new PanelScroll();
		scroll.top(SMALLEST_SCREEN, INVERTER);
		scroll.wheel(-1.0, SMALLEST_SCREEN, INVERTER);

		assertTrue(scroll.wheel(1.0, SMALLEST_SCREEN, INVERTER) > 0, "moved back down the screen");
		assertEquals(0, scroll.wheel(1.0, SMALLEST_SCREEN, INVERTER), "already at the top");
		assertEquals(0, scroll.top(SMALLEST_SCREEN, INVERTER));
	}

	@Test
	void everyRowOfThePanelCanBeBroughtOnScreen() {
		for (int panel : new int[] {ARRAY, INVERTER, 300}) {
			PanelScroll scroll = new PanelScroll();
			boolean[] seen = new boolean[panel];
			int top = scroll.top(SMALLEST_SCREEN, panel);
			for (int notch = 0; notch < 40; notch++) {
				for (int y = 0; y < panel; y++) {
					if (top + y >= 0 && top + y < SMALLEST_SCREEN) seen[y] = true;
				}
				top += scroll.wheel(-1.0, SMALLEST_SCREEN, panel);
			}

			for (int y = 0; y < panel; y++) {
				assertTrue(seen[y], "row " + y + " of a " + panel + " px panel");
			}
		}
	}

	@Test
	void aWindowMadeTallerLetsGoOfTheScroll() {
		PanelScroll scroll = new PanelScroll();
		scroll.top(SMALLEST_SCREEN, INVERTER);
		scroll.wheel(-1.0, SMALLEST_SCREEN, INVERTER);

		assertEquals((480 - INVERTER) / 2, scroll.top(480, INVERTER));
		assertEquals(0, scroll.scroll());
	}
}
