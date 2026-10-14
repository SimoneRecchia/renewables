package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PowerShareTest {
	private static final double EPS = 1e-9;
	private static final PowerDeliveryEvent NONE = PowerDeliveryEvent.NONE;

	private static PowerShare.Outcome share(double available, List<PowerShare.Demand> demands, PowerDeliveryEvent event,
			boolean tripped, double roll) {
		return PowerShare.share(available, demands, event, tripped, () -> roll);
	}

	private static PowerShare.Demand wants(double kw) {
		return new PowerShare.Demand(kw, kw);
	}

	/** grid.md 12.4: with exactly enough, a consumer gets 0.92 of its demand and sees a voltage factor of 0.92. */
	@Test
	void exactlyEnoughGivesNinetyTwoPercentAndAFactorOfNinetyTwo() {
		PowerShare.Outcome out = share(100.0, List.of(wants(100.0)), NONE, false, 0.99);
		PowerShare.Supply supply = out.supplies().get(0);

		assertEquals(92.0, supply.suppliedKw(), EPS);
		assertFalse(supply.meetsMinimum());
		assertEquals(0.92, supply.event().voltageFactor(), EPS);
		assertFalse(supply.event().disconnecting());
	}

	/** For a consumer to get all of its demand the box needs demand / 0.92 of it, which is 1.087 times. */
	@Test
	void aConsumerGetsAllItAsksForOnlyWithAboutNineteenPercentToSpare() {
		double just = 100.0 / PowerShare.DELIVERY;
		PowerShare.Supply supply = share(just, List.of(wants(100.0)), NONE, false, 0.99).supplies().get(0);

		assertEquals(100.0, supply.suppliedKw(), 1e-9);
		assertTrue(supply.meetsMinimum());
		assertEquals(NONE, supply.event());
	}

	@Test
	void aConsumerIsNeverGivenMoreThanATenthOverItsDemand() {
		PowerShare.Outcome out = share(10_000.0, List.of(wants(100.0)), NONE, false, 0.99);

		assertEquals(110.0 * 0.92, out.supplies().get(0).suppliedKw(), EPS);
		// what it did not take is not lost: the box banks it
		assertEquals(10_000.0 - 110.0, out.unusedKw(), 1e-6);
	}

	@Test
	void theShareIsInProportionToDemand() {
		PowerShare.Outcome out = share(150.0, List.of(wants(100.0), wants(50.0)), NONE, false, 0.99);

		assertEquals(100.0 * 0.92, out.supplies().get(0).suppliedKw(), EPS);
		assertEquals(50.0 * 0.92, out.supplies().get(1).suppliedKw(), EPS);
		assertEquals(0.0, out.unusedKw(), 1e-6);
	}

	@Test
	void demandIsTheLargerOfWhatIsAskedAndTheLeastItRunsOn() {
		// asks 10, will not run below 40: it is a 40 kW consumer as far as the sharing goes
		PowerShare.Outcome out = share(40.0, List.of(new PowerShare.Demand(10.0, 40.0), wants(0.0)), NONE, false, 0.99);

		assertEquals(40.0 * 0.92, out.supplies().get(0).suppliedKw(), EPS);
		assertEquals(0.0, out.supplies().get(1).suppliedKw(), EPS);
	}

	@Test
	void withNoDemandAtAllNobodyIsGivenAnythingAndNothingIsDrawn() {
		PowerShare.Outcome out = share(80.0, List.of(wants(0.0)), NONE, false, 0.99);

		assertEquals(0.0, out.supplies().get(0).suppliedKw(), EPS);
		assertEquals(80.0, out.unusedKw(), EPS);
	}

	@Test
	void garbageInIsZeroOut() {
		PowerShare.Outcome out = share(Double.NaN, List.of(new PowerShare.Demand(Double.NaN, -4.0)), NONE, false, 0.5);

		assertEquals(0.0, out.supplies().get(0).suppliedKw(), EPS);
		assertEquals(0.0, out.unusedKw(), EPS);
	}

	// ---- the breaker ----

	private static final PowerDeliveryEvent BAD_SURGE = new PowerDeliveryEvent(0.95, 9, false, 1.0);

	/** p = 0.1 + (0.95 - 0.85) * 0.4 = 0.14, and 0.1 more for a surge that has gone on over eight ticks. */
	@Test
	void aLongSevereSurgeTripsWithTheStatedChance() {
		List<PowerShare.Demand> one = List.of(wants(100.0));

		assertTrue(share(1000.0, one, BAD_SURGE, false, 0.2399).newTrip());
		assertFalse(share(1000.0, one, BAD_SURGE, false, 0.2401).newTrip());

		// a starved consumer (under half its minimum) adds five points
		assertTrue(share(10.0, one, BAD_SURGE, false, 0.2899).newTrip());
		assertFalse(share(10.0, one, BAD_SURGE, false, 0.2901).newTrip());

		PowerDeliveryEvent shortSurge = new PowerDeliveryEvent(0.95, 6, false, 1.0);
		assertTrue(share(1000.0, one, shortSurge, false, 0.1399).newTrip());
		assertFalse(share(1000.0, one, shortSurge, false, 0.1401).newTrip());
	}

	@Test
	void aSurgeThatIsNotSevereEnoughOrNotLongEnoughNeverRollsAtAll() {
		AtomicInteger rolled = new AtomicInteger();
		List<PowerShare.Demand> one = List.of(wants(100.0));

		PowerShare.share(1000.0, one, new PowerDeliveryEvent(0.85, 20, false, 1.0), false, () -> rolled.incrementAndGet());
		PowerShare.share(1000.0, one, new PowerDeliveryEvent(0.99, 4, false, 1.0), false, () -> rolled.incrementAndGet());
		PowerShare.share(1000.0, one, NONE, false, () -> rolled.incrementAndGet());

		assertEquals(0, rolled.get());
	}

	/** One roll a tick however many consumers there are, or the chance of a trip would grow with the field. */
	@Test
	void theDiceAreRolledOnceAndNotOncePerConsumer() {
		AtomicInteger rolled = new AtomicInteger();
		List<PowerShare.Demand> many = List.of(wants(10.0), wants(10.0), wants(10.0), wants(10.0));

		PowerShare.share(1000.0, many, BAD_SURGE, false, () -> {
			rolled.incrementAndGet();
			return 0.99;
		});

		assertEquals(1, rolled.get());
	}

	@Test
	void aTripCutsEveryConsumerAndTellsThemSo() {
		PowerShare.Outcome out = share(1000.0, List.of(wants(10.0), wants(10.0)), BAD_SURGE, false, 0.0);

		assertTrue(out.tripped());
		assertTrue(out.newTrip());
		for (PowerShare.Supply supply : out.supplies()) {
			assertEquals(0.0, supply.suppliedKw(), EPS);
			assertTrue(supply.event().disconnecting());
			assertEquals(0.0, supply.event().voltageFactor(), EPS);
			assertTrue(supply.event().durationTicks() >= 4);
			assertEquals(0.95, supply.event().surgeSeverity(), EPS);
		}
		// nothing was drawn, so everything that was available is unused
		assertEquals(1000.0, out.unusedKw(), EPS);
	}

	@Test
	void aBreakerThatIsAlreadyTrippedKeepsEverythingCutWithoutAnyEvent() {
		PowerShare.Outcome out = share(1000.0, List.of(wants(10.0)), NONE, true, 0.99);

		assertTrue(out.tripped());
		assertFalse(out.newTrip());
		assertEquals(0.0, out.supplies().get(0).suppliedKw(), EPS);
		assertTrue(out.supplies().get(0).event().disconnecting());
	}

	@Test
	void aDisconnectionOnTheLineCutsTheConsumersButDoesNotTripTheBreaker() {
		PowerDeliveryEvent drop = new PowerDeliveryEvent(0.1, 3, true, 1.0);
		PowerShare.Outcome out = share(1000.0, List.of(wants(10.0)), drop, false, 0.99);

		assertFalse(out.tripped());
		assertEquals(0.0, out.supplies().get(0).suppliedKw(), EPS);
		assertTrue(out.supplies().get(0).event().disconnecting());
		assertEquals(4, out.supplies().get(0).event().durationTicks());
	}

	@Test
	void aBrownoutScalesWhatIsGivenAndIsReportedAsAFactor() {
		PowerDeliveryEvent sag = new PowerDeliveryEvent(0.2, 10, false, 0.5);
		PowerShare.Supply supply = share(1000.0, List.of(wants(100.0)), sag, false, 0.99).supplies().get(0);

		assertEquals(110.0 * 0.92 * 0.5, supply.suppliedKw(), EPS);
		// 50.6 of 100 asked: the consumer is told the factor it actually got
		assertEquals(0.506, supply.event().voltageFactor(), 1e-9);
		assertFalse(supply.event().disconnecting());
	}
}
