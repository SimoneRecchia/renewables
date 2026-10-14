package io.github.simonerecchia.renewables.client.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WireLodTest {
	@Test
	void strideGrowsWithDistanceAtTheSpecifiedBands() {
		assertEquals(1, WireLod.stride(0.0));
		assertEquals(1, WireLod.stride(31.99));
		assertEquals(2, WireLod.stride(32.0));
		assertEquals(2, WireLod.stride(63.99));
		assertEquals(4, WireLod.stride(64.0));
		assertEquals(4, WireLod.stride(127.99));
		assertEquals(8, WireLod.stride(128.0));
		assertEquals(8, WireLod.stride(1.0e6));
	}

	@Test
	void sidesFallFromTwelveToSixToFour() {
		assertEquals(12, WireLod.sides(0.0));
		assertEquals(12, WireLod.sides(23.99));
		assertEquals(6, WireLod.sides(24.0));
		assertEquals(6, WireLod.sides(95.99));
		assertEquals(4, WireLod.sides(96.0));
	}

	@Test
	void aBundleMergesOnlyPastSixtyFourBlocksAndOnlyIfItIsOne() {
		assertFalse(WireLod.mergeBundle(63.9, 4));
		assertTrue(WireLod.mergeBundle(64.0, 4));
		assertFalse(WireLod.mergeBundle(500.0, 1));
		assertEquals(0.026, WireLod.mergedRadius(0.026, 0.075, 1), 0.0);
		assertEquals(0.026 + 0.0375, WireLod.mergedRadius(0.026, 0.075, 4), 1.0e-12);
	}

	@Test
	void theWalkAlongAProfileAlwaysEndsOnTheLastPointAndNeverStandsStill() {
		for (int last : new int[] {1, 2, 3, 7, 64, 100, 640, 641}) {
			for (int stride : new int[] {1, 2, 4, 8}) {
				int at = 0;
				int steps = 0;
				while (at < last) {
					int next = WireLod.next(at, last, stride);
					assertTrue(next > at, "forward progress");
					assertTrue(next <= last, "never past the end");
					at = next;
					steps++;
				}

				assertEquals(last, at);
				assertTrue(steps <= last);
			}
		}
	}

	@Test
	void pointsOfAStridedWalkLieOnTheGridUntilTheTail() {
		int last = 100;
		List<Integer> visited = new ArrayList<>();
		int at = 0;
		while (at < last) {
			visited.add(at);
			at = WireLod.next(at, last, 8);
		}

		assertEquals(0, visited.get(0));
		// 0, 8, 16 ... 96 on the grid of eight, then a single step of four lands on the end
		assertEquals(8, visited.get(1));
		assertTrue(visited.contains(96));
		assertEquals(13, visited.size());
		assertEquals(100, WireLod.next(96, last, 8));
	}

	@Test
	void aWalkWithStrideOneVisitsEveryPoint() {
		int at = 0;
		int count = 0;
		while (at < 37) {
			at = WireLod.next(at, 37, 1);
			count++;
		}

		assertEquals(37, count);
	}

	@Test
	void mixedLightKeepsBlockAndSkyApart() {
		int dark = 0;
		int lit = (15 << 4) | (15 << 20);
		assertEquals(dark, WireLod.mixLight(dark, lit, 0.0f));
		assertEquals(lit, WireLod.mixLight(dark, lit, 1.0f));

		int half = WireLod.mixLight(dark, lit, 0.5f);
		assertEquals(8, (half >> 4) & 15);
		assertEquals(8, (half >> 20) & 15);

		int blockOnly = 10 << 4;
		int skyOnly = 6 << 20;
		int mixed = WireLod.mixLight(blockOnly, skyOnly, 0.5f);
		assertEquals(5, (mixed >> 4) & 15);
		assertEquals(3, (mixed >> 20) & 15);
	}
}
