package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

class DisturbanceModelTest {
	private static final double EPS = 1e-9;

	private static DoubleSupplier always(double value) {
		return () -> value;
	}

	@Test
	void calmAirMakesNothingWorthMentioning() {
		PowerDeliveryEvent event = DisturbanceModel.draw(6.0, 0.10, always(0.5));

		assertEquals(0.025, event.surgeSeverity(), EPS);
		assertEquals(0, event.durationTicks());
		assertFalse(event.disconnecting());
		assertEquals(1.0, event.voltageFactor(), EPS);
	}

	@Test
	void aGustyDayIsASevereButNotDroppedOutEvent() {
		// 14 m/s, turbulence 0.2: gust factor 0.5, base = (0.2 - 0.14) 1.7 + 0.5 0.4 = 0.302
		PowerDeliveryEvent event = DisturbanceModel.draw(14.0, 0.20, always(0.0));

		assertEquals(0.302, event.surgeSeverity(), 1e-6);
		assertEquals(4, event.durationTicks());
		assertFalse(event.disconnecting());
	}

	@Test
	void aStormMayDropTheSupplyOut() {
		// 22 m/s, turbulence 0.3: chance = 4 x 0.04 + 0.06 x 0.7 = 0.202; a roll below it drops out
		PowerDeliveryEvent dropped = DisturbanceModel.draw(22.0, 0.30, always(0.1));
		assertTrue(dropped.disconnecting());
		assertTrue(dropped.durationTicks() >= 6 && dropped.durationTicks() <= 10);
		assertTrue(dropped.voltageFactor() >= 0.4 && dropped.voltageFactor() < 0.6);

		PowerDeliveryEvent held = DisturbanceModel.draw(22.0, 0.30, always(0.5));
		assertFalse(held.disconnecting());
		assertEquals(1.0, held.voltageFactor(), EPS);
	}

	@Test
	void theChanceOfADropOutIsCappedAtHalf() {
		// a hurricane: the chance would be far over one, but a roll of 0.6 still gets through
		PowerDeliveryEvent event = DisturbanceModel.draw(40.0, 0.5, always(0.6));
		assertFalse(event.disconnecting());
	}

	@Test
	void anEventIsHeldAndCountedDownNotRedrawnEveryTick() {
		DoubleSupplier loud = always(0.0);
		PowerDeliveryEvent first = DisturbanceModel.next(null, 22.0, 0.30, loud);
		assertTrue(first.disconnecting());
		int length = first.durationTicks();

		// from here the dice are not rolled: a roll that would never drop out changes nothing
		DoubleSupplier calm = () -> {
			throw new AssertionError("the dice must not be rolled while an event is running");
		};

		PowerDeliveryEvent event = first;
		for (int tick = 1; tick < length; tick++) {
			event = DisturbanceModel.next(event, 22.0, 0.30, calm);
			assertEquals(length - tick, event.durationTicks());
			assertEquals(first.surgeSeverity(), event.surgeSeverity(), EPS);
			assertEquals(tick < length, event.disconnecting() || event.durationTicks() == 0);
		}

		// the last count brings it to an end, with the drop-out and the sag cleared
		assertEquals(1, event.durationTicks());
		PowerDeliveryEvent over = DisturbanceModel.next(event, 22.0, 0.30, calm);
		assertEquals(0, over.durationTicks());
		assertFalse(over.disconnecting());
		assertEquals(1.0, over.voltageFactor(), EPS);
	}

	@Test
	void whenItIsOverTheDiceAreRolledAgain() {
		PowerDeliveryEvent ended = new PowerDeliveryEvent(0.5, 0, false, 1.0);
		PowerDeliveryEvent next = DisturbanceModel.next(ended, 6.0, 0.10, always(0.5));

		assertEquals(0.025, next.surgeSeverity(), EPS);
	}
}
