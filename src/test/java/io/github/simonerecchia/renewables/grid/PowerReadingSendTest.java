package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** client-net.md 11.7: a reading goes to the clients when it is worth a packet, and at least once a second. */
class PowerReadingSendTest {
	/**
	 * A turbine on gusty wind: its output, and everything downstream, moves by a percent or so every tick.  It used to
	 * be sent every tick, for every position; now at most every half second.
	 */
	@Test
	void aReadingThatMovesEveryTickIsSentAtMostEveryHalfSecond() {
		double sentKw = 4000.0;
		long sentAt = 0;
		int packets = 0;
		for (long tick = 1; tick <= 200; tick++) {
			double kw = 4000.0 * (1.0 + 0.05 * Math.sin(tick));
			if (LevelGrid.worthSending(kw, sentKw, tick - sentAt)) {
				packets++;
				sentKw = kw;
				sentAt = tick;
			}
		}

		assertTrue(packets <= 200 / LevelGrid.MIN_SEND_INTERVAL_TICKS, packets + " packets in 200 ticks");
		assertTrue(packets >= 200 / LevelGrid.HEARTBEAT_TICKS, packets + " packets in 200 ticks");
	}

	@Test
	void powerComingOrGoingIsSentAtOnce() {
		assertTrue(LevelGrid.worthSending(0.0, 3000.0, 1));
		assertTrue(LevelGrid.worthSending(0.2, 0.0, 1));
	}

	@Test
	void aSmallChangeOnABigMachineWaitsForTheHeartbeat() {
		// 0.3 % of 4 MW is 12 kW: well over the absolute tolerance, under the relative one
		assertFalse(LevelGrid.worthSending(4012.0, 4000.0, 15));
		assertTrue(LevelGrid.worthSending(4012.0, 4000.0, LevelGrid.HEARTBEAT_TICKS));
		assertTrue(LevelGrid.worthSending(4000.0, 4000.0, LevelGrid.HEARTBEAT_TICKS));
	}

	@Test
	void aSmallMachineStillHasTheAbsoluteTolerance() {
		assertFalse(LevelGrid.worthSending(2.04, 2.0, 15));
		assertTrue(LevelGrid.worthSending(2.2, 2.0, 15));
	}
}
