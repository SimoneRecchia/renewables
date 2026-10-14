package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.catalog.SwitchgearCatalog;
import org.junit.jupiter.api.Test;

class SwitchLogicTest {
	private static final double RATED = SwitchgearCatalog.MV_BREAKER.ratedKw();

	@Test
	void loadIsTwoPercentOfTheRating() {
		assertEquals(26_000.0, RATED, 1e-9);
		// 520 kW exactly is not yet load; above it is
		assertFalse(SwitchLogic.underLoad(520.0, RATED));
		assertTrue(SwitchLogic.underLoad(520.1, RATED));
		assertFalse(SwitchLogic.underLoad(0.0, RATED));
	}

	@Test
	void aBreakerTripsAboveItsRatingAndNotAt() {
		assertFalse(SwitchLogic.overRated(26_000.0, RATED));
		assertTrue(SwitchLogic.overRated(26_000.5, RATED));
	}

	@Test
	void aDisconnectorWillNotOpenUnderLoadButABreakerWill() {
		assertFalse(SwitchLogic.mayOpenByHand(false, 5_000.0, RATED));
		assertTrue(SwitchLogic.mayOpenByHand(false, 100.0, RATED));
		assertTrue(SwitchLogic.mayOpenByHand(true, 5_000.0, RATED));
		assertTrue(SwitchLogic.mayOpenByHand(true, 50_000.0, RATED));
	}

	@Test
	void theTripIsLookedForOnceASecond() {
		assertTrue(SwitchLogic.isTripTick(0));
		assertFalse(SwitchLogic.isTripTick(19));
		assertTrue(SwitchLogic.isTripTick(20));
		assertTrue(SwitchLogic.isTripTick(24_000));
	}

	/** Reading the level rather than the change re-closed a switch opened by hand at the next block placed beside it. */
	@Test
	void onlyAChangeInTheSignalIsAnEdge() {
		assertEquals(1, SwitchLogic.edge(false, true));
		assertEquals(-1, SwitchLogic.edge(true, false));
		assertEquals(0, SwitchLogic.edge(false, false));
		assertEquals(0, SwitchLogic.edge(true, true));
	}
}
