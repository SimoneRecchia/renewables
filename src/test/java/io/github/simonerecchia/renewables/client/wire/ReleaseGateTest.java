package io.github.simonerecchia.renewables.client.wire;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReleaseGateTest {
	@Test
	void theFirstPressIsAdmitted() {
		assertTrue(new ReleaseGate().admit());
	}

	@Test
	void aHeldButtonDoesNotRepeatTheClick() {
		ReleaseGate gate = new ReleaseGate();
		assertTrue(gate.admit());

		// the button stays down through the ticks while the use action repeats
		for (int tick = 0; tick < 10; tick++) {
			gate.observe(true);
			assertFalse(gate.admit(), "a repeat of the held button");
		}
	}

	@Test
	void lettingGoAllowsTheNextPress() {
		ReleaseGate gate = new ReleaseGate();
		assertTrue(gate.admit());

		gate.observe(true);
		gate.observe(false);

		assertTrue(gate.admit());
		assertFalse(gate.admit());
	}

	@Test
	void theOtherHandsEchoOfOnePressIsSwallowed() {
		ReleaseGate gate = new ReleaseGate();
		assertTrue(gate.admit());
		assertFalse(gate.admit());
	}

	@Test
	void resetForgetsAHeldButton() {
		ReleaseGate gate = new ReleaseGate();
		gate.admit();
		gate.reset();

		assertTrue(gate.admit());
	}
}
