package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.simonerecchia.renewables.api.power.TickBudget;

import org.junit.jupiter.api.Test;

/** grid.md section 10: the budget a generator offers, and what is left for the wire grid. */
class EnergyBudgetsTest {
	private static final double EPS = 1e-9;
	private static final double NO_CEILING = Double.MAX_VALUE;

	@Test
	void aTurbineAtRatedPowerOffersItsWholeOutputByDefault() {
		// 78.75 kW at 125 J per kW: 9843.75 J per tick; fraction 1, no ceiling
		assertEquals(9843.75, EnergyBudgets.offeredJoules(true, 78.75, 78.75, 1.0, NO_CEILING), EPS);
	}

	@Test
	void whatAnotherModTakesComesOffTheWireGrid() {
		double offered = EnergyBudgets.offeredJoules(true, 78.75, 78.75, 1.0, NO_CEILING);
		TickBudget budget = new TickBudget();
		budget.open(offered);

		budget.claim(2500.0, false);

		// 2500 J / 125 = 20 kW
		assertEquals(58.75, EnergyBudgets.netKw(78.75, budget.claimed()), EPS);
	}

	@Test
	void nothingIsOfferedWhenExternalEnergyIsOff() {
		assertEquals(0.0, EnergyBudgets.offeredJoules(false, 78.75, 78.75, 1.0, NO_CEILING), EPS);
		assertEquals(0.0, EnergyBudgets.maxJoulesPerTick(false, 78.75, 1.0, NO_CEILING), EPS);
	}

	@Test
	void theFractionIsAShareOfTheNameplateNotOfTheOutput() {
		// half the nameplate: at full output only half is offered; at a quarter output all of it is
		assertEquals(78.75 * 125.0 * 0.5, EnergyBudgets.offeredJoules(true, 78.75, 78.75, 0.5, NO_CEILING), EPS);
		assertEquals(20.0 * 125.0, EnergyBudgets.offeredJoules(true, 20.0, 78.75, 0.5, NO_CEILING), EPS);
	}

	@Test
	void theCeilingIsAppliedAfterTheFraction() {
		assertEquals(2000.0, EnergyBudgets.offeredJoules(true, 78.75, 78.75, 1.0, 2000.0), EPS);
		assertEquals(2000.0, EnergyBudgets.maxJoulesPerTick(true, 78.75, 1.0, 2000.0), EPS);
		assertEquals(1000.0, EnergyBudgets.maxJoulesPerTick(true, 78.75, 1.0, 1000.0), EPS);
	}

	@Test
	void aZeroFractionOffersNothing() {
		assertEquals(0.0, EnergyBudgets.offeredJoules(true, 78.75, 78.75, 0.0, NO_CEILING), EPS);
	}

	@Test
	void theInverterIsHeldToTheSameLimits() {
		// the inverter's offer used to be its whole AC output, whatever the configuration said
		assertEquals(500.0, EnergyBudgets.offeredJoules(true, 100.0, 100.0, 1.0, 500.0), EPS);
		assertEquals(100.0 * 125.0 * 0.2, EnergyBudgets.offeredJoules(true, 100.0, 100.0, 0.2, NO_CEILING), EPS);
	}

	@Test
	void theWireGridNeverGoesNegative() {
		assertEquals(0.0, EnergyBudgets.netKw(10.0, 10.0 * 125.0 * 3.0), EPS);
		assertEquals(10.0, EnergyBudgets.netKw(10.0, -5.0), EPS);
	}

	@Test
	void aBudgetCannotBeOverdrawnAndSimulatingTakesNothing() {
		TickBudget budget = new TickBudget();
		budget.open(1000.0);

		assertEquals(600.0, budget.claim(600.0, true), EPS);
		assertEquals(1000.0, budget.available(), EPS);
		assertEquals(600.0, budget.claim(600.0, false), EPS);
		assertEquals(400.0, budget.claim(600.0, false), EPS);
		assertEquals(0.0, budget.claim(1.0, false), EPS);
	}
}
