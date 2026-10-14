package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The power box's energy over its ticks, in the order the server runs them: the grid delivers at the end of a tick,
 * the box spends in its own tick, and a cable of another mod may pull before or after it.  What comes in is what
 * can go out, once.
 */
class KioskEnergyTest {
	/** 10 kW for a tick is 500 FE. */
	private static final double KW = 10.0;
	private static final int FE = 500;

	/**
	 * The review's probe: 10 kW for 100 ticks, nobody in the field, and a cable that pulls everything there is
	 * after the box's tick.  It used to take the banked flow and the same flow again - 100 000 FE out of 50 000.
	 */
	@Test
	void aPullerAfterTheBoxTakesWhatCameInAndNotTwice() {
		KioskEnergy energy = new KioskEnergy();
		long pulled = 0;
		for (int tick = 0; tick < 100; tick++) {
			energy.deliver(KW);
			energy.bankRest();
			// what is on offer after the box has banked is the buffer, and only the buffer
			assertEquals(energy.buffer(), energy.stored());
			pulled += energy.extract(Integer.MAX_VALUE, false);
		}

		assertEquals(100L * FE, pulled);
	}

	@Test
	void aPullerBeforeTheBoxTakesTheFlowAndNothingIsBankedTwice() {
		KioskEnergy energy = new KioskEnergy();
		long pulled = 0;
		for (int tick = 0; tick < 100; tick++) {
			energy.deliver(KW);
			pulled += energy.extract(Integer.MAX_VALUE, false);
			energy.bankRest();
		}

		assertEquals(100L * FE, pulled);
		assertEquals(0, energy.buffer());
	}

	/**
	 * Everything at once: a cable pulling 100 FE before the box, the field's consumers using 3 kW, the rest banked
	 * and a cable after the box pulling 150.  Every FE that came in is pulled, used or in the buffer, and no more.
	 */
	@Test
	void whatIsPulledUsedAndStoredIsWhatCameIn() {
		KioskEnergy energy = new KioskEnergy();
		long in = 0;
		long pulled = 0;
		long used = 0;
		for (int tick = 0; tick < 200; tick++) {
			energy.deliver(KW);
			in += FE;

			pulled += energy.extract(100, false);
			double field = Math.min(3.0, energy.flowKw());
			energy.spend(field);
			used += Math.round(field * FePool.FE_PER_KW);
			energy.bankRest();
			pulled += energy.extract(150, false);
		}

		assertEquals(in, pulled + used + energy.buffer());
		// 500 in, 100 + 150 + 150 out: 100 FE a tick stay behind
		assertEquals(200 * 100, energy.buffer());
	}

	/** A simulated pull changes nothing, and the field never spends more than the flow there is. */
	@Test
	void simulatingTakesNothingAndTheFieldCannotOverspend() {
		KioskEnergy energy = new KioskEnergy();
		energy.deliver(KW);
		assertEquals(FE, energy.extract(Integer.MAX_VALUE, true));
		assertEquals(FE, energy.stored());

		energy.spend(25.0);
		assertEquals(0.0, energy.flowKw(), 0.0);
		energy.bankRest();
		assertEquals(0, energy.buffer());
	}

	/** A flow left unspent when the next delivery comes is not stored: the delivery replaces it. */
	@Test
	void aDeliveryReplacesWhatWasLeftOfTheLast() {
		KioskEnergy energy = new KioskEnergy();
		energy.deliver(KW);
		energy.deliver(KW);
		assertEquals(FE, energy.stored());

		energy.deliver(-3.0);
		assertEquals(0, energy.stored());
	}

	@Test
	void theFeThatLeftIsCountedOnceAndThenForgotten() {
		KioskEnergy energy = new KioskEnergy();
		energy.deliver(KW);
		energy.extract(300, false);
		energy.extract(300, true);
		energy.spend(1.0);

		assertEquals(300, energy.takeOut());
		assertEquals(0, energy.takeOut());
	}
}
