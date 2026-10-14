package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

import org.junit.jupiter.api.Test;

class GridMathTest {
	private static final double EPS = 1e-9;

	/**
	 * k = 1000 R / U^2 per kW.  A street bundle of 70 mm2 aluminium (0.443 ohm/km at 20 C) over ten blocks of 10 m
	 * is 0.0443 ohm a phase; at 400 V that is 1000 x 0.0443 / 160 000 = 2.76875e-4 per kW.
	 */
	@Test
	void theLossCoefficientIsResistanceOverVoltageSquared() {
		assertEquals(2.76875e-4, GridMath.lossCoefficient(0.0443, 400.0), 1e-12);
		// the same section at 33 kV: (400 / 33 000)^2 of it
		assertEquals(4.06795e-8, GridMath.lossCoefficient(0.0443, 33_000.0), 1e-12);
		assertEquals(0.0, GridMath.lossCoefficient(0.0, 400.0), 0.0);
		assertEquals(0.0, GridMath.lossCoefficient(0.0443, 0.0), 0.0);
	}

	/** 100 kW down that span loses k P^2 = 2.76875e-4 x 10 000 = 2.76875 kW. */
	@Test
	void aSpanLosesTheSquareOfWhatItCarries() {
		double k = GridMath.lossCoefficient(0.0443, 400.0);

		assertEquals(100.0 - 2.76875, GridMath.delivered(100.0, k), EPS);
		// twice the power, four times the loss
		assertEquals(200.0 - 4 * 2.76875, GridMath.delivered(200.0, k), EPS);
		assertEquals(1.0 - 0.0276875, GridMath.keptFraction(100.0, k), 1e-9);
	}

	/**
	 * The three conductors at the length a reel reaches, each at its own voltage: 40 blocks of street bundle loses
	 * a tenth of 100 kW, 90 blocks of the 33 kV line loses 1.086 kW of 3 MW, 160 blocks of the 400 kV quad bundle
	 * 0.49 kW of 60 MW.  Voltage, not distance, is what decides a loss.
	 */
	@Test
	void theVoltageDecidesTheLossNotTheDistance() {
		double street = GridMath.lossCoefficient(0.443 * 0.4, 400.0);
		double collector = GridMath.lossCoefficient(0.146 * 0.9, 33_000.0);
		double transmission = GridMath.lossCoefficient(0.0136 * 1.6, 400_000.0);

		assertEquals(11.075, 100.0 - GridMath.delivered(100.0, street), 1e-6);
		assertEquals(1.08595, 3_000.0 - GridMath.delivered(3_000.0, collector), 1e-5);
		assertEquals(0.4896, 60_000.0 - GridMath.delivered(60_000.0, transmission), 1e-6);
	}

	/**
	 * A resistive line fed at its nominal voltage delivers at most 1/(4k), reached at 1/(2k) sent: 40 blocks of street
	 * bundle (k = 1.1075e-3) take 451.47 kW and deliver 225.73, and nothing offered past that gets in.
	 */
	@Test
	void aLineDeliversNoMoreThanItsTransferLimit() {
		double k = GridMath.lossCoefficient(0.443 * 0.4, 400.0);

		assertEquals(200.325, GridMath.delivered(300.0, k), 1e-6);
		assertEquals(225.733634, GridMath.delivered(451.467269, k), 1e-6);
		assertEquals(225.733634, GridMath.delivered(1_000.0, k), 1e-6);
		assertEquals(225.733634, GridMath.delivered(1.0e9, k), 1e-6);
	}

	@Test
	void moreSentNeverDeliversLess() {
		double k = GridMath.lossCoefficient(0.443 * 0.4, 400.0);
		double previous = 0.0;
		for (double sent = 0.0; sent <= 2_000.0; sent += 5.0) {
			double now = GridMath.delivered(sent, k);
			assertTrue(now >= previous - 1e-12, "sent=" + sent);
			previous = now;
		}
	}

	@Test
	void aLosslessOrAnEmptyHopKeepsEverything() {
		assertEquals(50.0, GridMath.delivered(50.0, 0.0), 0.0);
		assertEquals(0.0, GridMath.delivered(0.0, 1.0), 0.0);
		assertEquals(1.0, GridMath.keptFraction(0.0, 1.0), 0.0);
		assertEquals(1.0, GridMath.keptFraction(50.0, 0.0), 0.0);
	}

	/** A machine that burns 11 kW carrying 100 passes on 89 % of it, and nothing of a flow that does not cover the loss. */
	@Test
	void aMachineInTheWayKeepsWhatItDoesNotBurn() {
		assertEquals(0.89, GridMath.keptThrough(100.0, 11.0), 1e-12);
		assertEquals(0.0, GridMath.keptThrough(1.0, 2.0), 0.0);
		assertEquals(0.0, GridMath.keptThrough(0.0, 2.0), 0.0);
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
