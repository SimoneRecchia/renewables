package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FePoolTest {
	@Test
	void aKilowattIsFiftyFeAndWholeFeOnly() {
		assertEquals(0, FePool.flow(0.0));
		assertEquals(0, FePool.flow(-5.0));
		assertEquals(0, FePool.flow(Double.NaN));
		assertEquals(2551, FePool.flow(51.03));
		assertEquals(50, FePool.flow(1.0));
	}

	@Test
	void whatIsStoredIsTheBufferAndTheFlowCappedAtTheCapacity() {
		assertEquals(2551, FePool.stored(0, 51.03));
		assertEquals(5551, FePool.stored(3000, 51.03));
		assertEquals(FePool.CAPACITY, FePool.stored(99_000, 51.03));
		assertEquals(FePool.CAPACITY, FePool.stored(0, 1_000_000.0));
	}

	/** grid.md 12.5: 51.03 kW is 2551 FE a tick; one neighbour that takes all of it takes 2000 and 40 kW leave the field. */
	@Test
	void theWorkedExampleOfTheSpecification() {
		FePool.Extraction took = FePool.extract(0, 51.03, FePool.MAX_PUSH);

		assertEquals(2000, took.taken());
		assertEquals(0, took.newBuffer());
		assertEquals(11.03, took.newPowerKw(), 1e-9);
		// and what is left is 551 FE worth
		assertEquals(551, FePool.stored(took.newBuffer(), took.newPowerKw()));
	}

	@Test
	void theBufferIsSpentBeforeTheFlowIsTouched() {
		FePool.Extraction took = FePool.extract(1500, 10.0, 2000);

		assertEquals(2000, took.taken());
		assertEquals(0, took.newBuffer());
		// 500 FE came out of the flow: 10 kW less 10 kW
		assertEquals(0.0, took.newPowerKw(), 1e-9);

		FePool.Extraction small = FePool.extract(1500, 10.0, 1000);
		assertEquals(500, small.newBuffer());
		assertEquals(10.0, small.newPowerKw(), 1e-9);
	}

	@Test
	void youCannotTakeMoreThanThereIs() {
		FePool.Extraction took = FePool.extract(100, 1.0, 5000);

		assertEquals(150, took.taken());
		assertEquals(0, took.newBuffer());
		assertEquals(0.0, took.newPowerKw(), 1e-9);
	}

	@Test
	void nothingIsTakenForNothingOrANegativeRequest() {
		assertEquals(0, FePool.extract(0, 0.0, 100).taken());
		assertEquals(0, FePool.extract(500, 5.0, -4).taken());
		assertEquals(500, FePool.extract(500, 5.0, -4).newBuffer());
	}

	@Test
	void unusedPowerIsBankedUpToTheCapacity() {
		assertEquals(551, FePool.bank(0, 11.03));
		assertEquals(1102, FePool.bank(551, 11.03));
		assertEquals(FePool.CAPACITY, FePool.bank(99_990, 20.0));
		assertEquals(70, FePool.bank(70, 0.0));
	}

	/** A box on a line that carries more than its neighbour takes fills up, and then gives the buffer away first. */
	@Test
	void aBufferFillsOnASurplusAndIsDrawnOnWhenTheLineGoesQuiet() {
		int buffer = 0;
		for (int tick = 0; tick < 10; tick++) buffer = FePool.bank(buffer, 40.0);
		assertEquals(20_000, buffer);

		// the line goes quiet: nothing is flowing, and the neighbour still gets 2000 a tick
		FePool.Extraction took = FePool.extract(buffer, 0.0, FePool.MAX_PUSH);
		assertEquals(2000, took.taken());
		assertEquals(18_000, took.newBuffer());
		assertEquals(0.0, took.newPowerKw(), 1e-9);
	}
}
