package io.github.simonerecchia.renewables.wind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.catalog.TurbineCatalog;

import org.junit.jupiter.api.Test;

/** The box the nacelle fills and the height a pile of tower stands to. */
class TurbineGeometryTest {
	@Test
	void utilityNacellesFillTheWholeFootprintAndAreAsTallAsTheirScale() {
		// the base saturates at one block; the height is 0.95 of the scale
		assertEquals(0.5, TurbineGeometry.nacelleHalfWidth(TurbineCatalog.C52_085), 1e-9);
		assertEquals(0.379, TurbineGeometry.nacelleHeight(TurbineCatalog.C52_085), 0.001);
		assertEquals(0.583, TurbineGeometry.nacelleHeight(TurbineCatalog.C80_20), 0.001);
		assertEquals(0.656, TurbineGeometry.nacelleHeight(TurbineCatalog.C90_30), 0.001);
		assertEquals(0.816, TurbineGeometry.nacelleHeight(TurbineCatalog.C112_30), 0.001);
		assertEquals(0.947, TurbineGeometry.nacelleHeight(TurbineCatalog.C130_40), 0.001);
	}

	@Test
	void smallWindNacelleIsTheOneThatDoesNotFillTheBlock() {
		assertEquals(0.787, 2.0 * TurbineGeometry.nacelleHalfWidth(TurbineCatalog.SW_10), 0.001);
		assertEquals(0.2375, TurbineGeometry.nacelleHeight(TurbineCatalog.SW_10), 0.0001);
	}

	@Test
	void nothingIsBiggerThanTheBlock() {
		TurbineCatalog.all().forEach(spec -> {
			assertTrue(TurbineGeometry.nacelleHalfWidth(spec) <= 0.5);
			assertTrue(TurbineGeometry.nacelleHeight(spec) <= 1.0);
		});
	}

	@Test
	void towerFoldsPastTwentyWhenSetAndPastSixteenWhenWalkedOn() {
		assertFalse(TurbineGeometry.collapsesWhenPlaced(20));
		assertTrue(TurbineGeometry.collapsesWhenPlaced(21));
		assertFalse(TurbineGeometry.collapsesWhenWalkedOn(16));
		assertTrue(TurbineGeometry.collapsesWhenWalkedOn(17));
		// a pile of seventeen to twenty stands until somebody steps on it
		assertFalse(TurbineGeometry.collapsesWhenPlaced(17));
		assertTrue(TurbineGeometry.collapsesWhenWalkedOn(20));
	}

	@Test
	void noModelAcceptsMoreThanTheSafeHeight() {
		TurbineCatalog.all().forEach(spec -> assertTrue(spec.maxTowerSegments() <= TurbineGeometry.SAFE_SEGMENTS));
	}

	@Test
	void scanReachesPastTheTallestPileThatCanBeBuilt() {
		assertTrue(TurbineGeometry.SCAN_LIMIT > TurbineGeometry.MAX_SEGMENTS);
	}
}
