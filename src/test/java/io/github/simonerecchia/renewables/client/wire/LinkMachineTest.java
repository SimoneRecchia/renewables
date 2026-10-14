package io.github.simonerecchia.renewables.client.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.client.wire.LinkMachine.Click;
import io.github.simonerecchia.renewables.client.wire.LinkMachine.Step;
import org.junit.jupiter.api.Test;

class LinkMachineTest {
	@Test
	void theFirstClickStartsAndNothingIsSent() {
		LinkMachine<String> machine = new LinkMachine<>();
		assertFalse(machine.isPending());

		Click<String> click = machine.click("pole/3");

		assertEquals(Step.STARTED, click.step());
		assertNull(click.first());
		assertEquals("pole/3", machine.pending());
	}

	@Test
	void aSecondClickOnAnotherFittingCompletesAndClearsAtOnce() {
		LinkMachine<String> machine = new LinkMachine<>();
		machine.click("pole/3");

		Click<String> click = machine.click("cabin/0");

		assertEquals(Step.COMPLETED, click.step());
		assertEquals("pole/3", click.first());
		assertFalse(machine.isPending(), "the pending connection is gone whatever the server answers");
		assertNull(machine.pending());
	}

	@Test
	void aSecondClickOnTheSameFittingCancels() {
		LinkMachine<String> machine = new LinkMachine<>();
		machine.click("pole/3");

		Click<String> click = machine.click("pole/3");

		assertEquals(Step.CANCELLED, click.step());
		assertNull(click.first());
		assertFalse(machine.isPending());
	}

	@Test
	void thereIsNoChainingTheSecondFittingDoesNotBecomeTheNextFirst() {
		LinkMachine<String> machine = new LinkMachine<>();
		machine.click("a");
		machine.click("b");

		assertEquals(Step.STARTED, machine.click("c").step(), "a third click starts afresh");
		assertEquals("c", machine.pending());
	}

	@Test
	void afterACancelTheNextClickStartsAgain() {
		LinkMachine<String> machine = new LinkMachine<>();
		machine.click("a");
		machine.click("a");

		assertEquals(Step.STARTED, machine.click("a").step());
	}

	@Test
	void clearForgetsWhatWasPending() {
		LinkMachine<String> machine = new LinkMachine<>();
		machine.click("a");
		machine.clear();

		assertFalse(machine.isPending());
		assertEquals(Step.STARTED, machine.click("b").step());
	}

	@Test
	void sameFittingMeansEqualsNotTheSameObject() {
		record Key(int block, int index) {
		}

		LinkMachine<Key> machine = new LinkMachine<>();
		machine.click(new Key(7, 2));

		assertEquals(Step.CANCELLED, machine.click(new Key(7, 2)).step());
		machine.click(new Key(7, 2));
		assertEquals(Step.COMPLETED, machine.click(new Key(7, 3)).step(), "two fittings of one machine can be joined");
	}

	@Test
	void theGateLetsOneClickPerTickThrough() {
		ClickGate gate = new ClickGate();

		assertTrue(gate.admit(100L));
		assertFalse(gate.admit(100L), "the second hand's event for the same click");
		assertTrue(gate.admit(101L));
		assertFalse(gate.admit(101L));

		gate.reset();
		assertTrue(gate.admit(101L));
	}

	@Test
	void theGateAdmitsTheVeryFirstTickEvenIfItIsZero() {
		assertTrue(new ClickGate().admit(0L));
	}
}
