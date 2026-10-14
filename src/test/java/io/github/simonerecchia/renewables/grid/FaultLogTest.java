package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A machine that throws every tick: the grid's log takes a line a minute, and never goes quiet for good. */
class FaultLogTest {
	@Test
	void aFailureEveryTickIsLoggedOnceAMinuteWithTheCountOfTheRest() {
		FaultLog log = new FaultLog();
		int logged = 0;
		for (long tick = 0; tick < 3 * FaultLog.INTERVAL_TICKS; tick++) {
			if (log.admit(tick)) {
				logged++;
				if (tick > 0) assertEquals(FaultLog.INTERVAL_TICKS - 1, log.take());
				else assertEquals(0, log.take());
			}
		}

		assertEquals(3, logged);
	}

	/** The old limit was five lines for the whole server, ever: a fault an hour later was never seen. */
	@Test
	void aFailureLongAfterIsStillLogged() {
		FaultLog log = new FaultLog();
		for (int i = 0; i < 10; i++) log.admit(i);

		assertFalse(log.admit(100));
		assertTrue(log.admit(72_000));
	}
}
