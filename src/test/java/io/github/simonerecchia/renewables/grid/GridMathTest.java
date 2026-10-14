package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

import org.junit.jupiter.api.Test;

class GridMathTest {
	private static final double EPS = 1e-4;

	/** The control values of grid.md 8.5. */
	@Test
	void efficiencyControlValues() {
		assertEquals(0.90, GridMath.efficiency(1, 10), EPS);
		assertEquals(0.70, GridMath.efficiency(1, 30), EPS);
		assertEquals(0.10, GridMath.efficiency(1, 100), EPS);
		assertEquals(0.7778, GridMath.efficiency(2, 30), EPS);
		assertEquals(0.4118, GridMath.efficiency(3, 100), EPS);
		assertEquals(0.9259, GridMath.efficiency(2, 10), EPS);
	}

	@Test
	void efficiencyNeverDropsBelowATenth() {
		assertEquals(0.10, GridMath.efficiency(1, 250), EPS);
		assertEquals(0.10, GridMath.efficiency(1, 1.0e6), EPS);
	}

	@Test
	void anAdjacentHopLosesNothing() {
		assertEquals(1.0, GridMath.efficiency(1, 0), EPS);
		assertEquals(1.0, GridMath.efficiency(5, 0), EPS);
	}

	@Test
	void moreCablesAlwaysLoseLess() {
		double previous = GridMath.efficiency(1, 60);
		for (int n = 2; n <= 8; n++) {
			double now = GridMath.efficiency(n, 60);
			assertTrue(now > previous, "n=" + n);
			previous = now;
		}
	}

	@Test
	void aDisturbanceWeakensAtEveryHopAndEnds() {
		PowerDeliveryEvent storm = new PowerDeliveryEvent(0.8, 3, true, 0.5);

		PowerDeliveryEvent one = GridMath.attenuate(storm, 0.9);
		assertEquals(0.8 * 0.9 * 0.95, one.surgeSeverity(), EPS);
		assertEquals(2, one.durationTicks());
		assertTrue(one.disconnecting());
		assertEquals(1.0 - 0.5 * 0.9, one.voltageFactor(), EPS);

		PowerDeliveryEvent two = GridMath.attenuate(one, 0.9);
		assertEquals(1, two.durationTicks());
		assertTrue(two.disconnecting());

		// the hop that takes the last tick takes the drop-out and the sag with it
		PowerDeliveryEvent three = GridMath.attenuate(two, 0.9);
		assertEquals(0, three.durationTicks());
		assertFalse(three.disconnecting());
		assertEquals(1.0, three.voltageFactor(), EPS);
	}

	@Test
	void nothingStaysNothing() {
		assertSame(PowerDeliveryEvent.NONE, GridMath.attenuate(PowerDeliveryEvent.NONE, 0.5));
	}

	@Test
	void mergingTakesTheWorstOfEach() {
		PowerDeliveryEvent a = new PowerDeliveryEvent(0.3, 5, false, 0.9);
		PowerDeliveryEvent b = new PowerDeliveryEvent(0.6, 2, true, 0.7);

		PowerDeliveryEvent merged = GridMath.merge(a, b);
		assertEquals(0.6, merged.surgeSeverity(), EPS);
		assertEquals(5, merged.durationTicks());
		assertTrue(merged.disconnecting());
		assertEquals(0.7, merged.voltageFactor(), EPS);
	}

	@Test
	void roleRulesKeepAnOutputFromTakingPowerIn() {
		assertTrue(FlowRules.rolesAllow("output", "input", false));
		assertTrue(FlowRules.rolesAllow("output", "bidirectional", false));
		assertTrue(FlowRules.rolesAllow("bidirectional", "bidirectional", false));
		assertTrue(FlowRules.rolesAllow("bidirectional", "input", false));

		assertFalse(FlowRules.rolesAllow("output", "output", false));
		assertFalse(FlowRules.rolesAllow("input", "bidirectional", false));
		assertFalse(FlowRules.rolesAllow("bidirectional", "output", false));
		assertFalse(FlowRules.rolesAllow("input", "output", false));
	}

	@Test
	void aBridgeThroughOneMachineIsTheOnlyInputToOutputCase() {
		assertTrue(FlowRules.rolesAllow("input", "output", true));
		assertFalse(FlowRules.rolesAllow("input", "output", false));
	}

	@Test
	void anUnknownRoleReadsAsBidirectional() {
		assertTrue(FlowRules.rolesAllow("something else", "bidirectional", false));
		assertTrue(FlowRules.rolesAllow("bidirectional", "something else", false));
	}

	@Test
	void aSwitchAcceptsFromAnyoneWhoseRolesAllow() {
		assertTrue(FlowRules.allows(false, true, "output", "bidirectional", false));
		assertFalse(FlowRules.allows(false, false, "output", "bidirectional", false));
		assertFalse(FlowRules.allows(true, false, "output", "output", false));
	}
}
