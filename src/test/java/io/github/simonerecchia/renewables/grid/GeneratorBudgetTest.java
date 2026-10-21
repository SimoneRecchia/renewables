package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * grid.md section 10, "no joule is spent twice", over whole server ticks: a generator, another mod's cable that pulls
 * from it and ticks before it, and the wire grid that reads it at the end of the tick.
 */
class GeneratorBudgetTest {
	private static final double EPS = 1e-6;
	private static final double JOULES_PER_KW = 125.0;
	private static final double PRODUCED_KW = 4000.0;

	private static final GeneratorBudget.Limits NO_LIMITS = new GeneratorBudget.Limits() {
		@Override
		public boolean enabled() {
			return true;
		}

		@Override
		public double fraction() {
			return 1.0;
		}

		@Override
		public double ceiling() {
			return Double.MAX_VALUE;
		}
	};

	/** What one run of the sequence handed out, in Joules. */
	private record Spent(double produced, double grid, double cable) {
	}

	/**
	 * {@code ticks} server ticks.  In each: the cable ticks first and pulls all it can, then the generator opens its
	 * budget, then the grid reads the generator and - when a load can be reached and the grid tells the budget - takes
	 * the whole of what it reads.
	 */
	private static Spent run(int ticks, boolean loadReached, boolean gridReportsDraw) {
		AtomicLong tick = new AtomicLong();
		GeneratorBudget budget = new GeneratorBudget(NO_LIMITS, tick::get);
		double produced = 0.0;
		double grid = 0.0;
		double cable = 0.0;

		for (int t = 1; t <= ticks; t++) {
			tick.set(t);
			cable += budget.claimJoules(Double.MAX_VALUE, false);

			budget.open(PRODUCED_KW, PRODUCED_KW);
			produced += PRODUCED_KW * JOULES_PER_KW;

			double net = budget.netKw(PRODUCED_KW);
			double drawn = loadReached ? net : 0.0;
			grid += drawn * JOULES_PER_KW;
			if (gridReportsDraw) budget.drawForGrid(drawn);
		}

		return new Spent(produced, grid, cable);
	}

	/** The review's measurement: 100 ticks at 4 MW, a pulling cable that ticks before the turbine. */
	@Test
	void whatTheGridTookCannotBePulledAgainBeforeTheNextTick() {
		Spent spent = run(100, true, true);

		assertTrue(spent.grid() + spent.cable() <= spent.produced() + EPS,
				"handed out " + (spent.grid() + spent.cable()) / spent.produced() + " times what was made");
		assertEquals(spent.produced(), spent.grid(), EPS);
		assertEquals(0.0, spent.cable(), EPS);
	}

	/** The same sequence without the grid reporting its draw is the bug: the residue goes to the grid and the cable both. */
	@Test
	void withoutTheDrawReportedTheSameEnergyIsSpentTwice() {
		Spent spent = run(100, true, false);

		assertEquals(1.99, (spent.grid() + spent.cable()) / spent.produced(), 1e-9);
	}

	/** A generator wired to a line with no load gives the grid nothing, so its energy is still there for the cable. */
	@Test
	void whatTheGridDidNotTakeIsStillOnOffer() {
		Spent spent = run(100, false, true);

		assertEquals(0.0, spent.grid(), EPS);
		// every tick but the last is pulled at the start of the next
		assertEquals(spent.produced() * 99 / 100, spent.cable(), EPS);
	}

	@Test
	void aCableThatTicksAfterTheGeneratorComesOffTheGridsShare() {
		AtomicLong tick = new AtomicLong(7);
		GeneratorBudget budget = new GeneratorBudget(NO_LIMITS, tick::get);
		budget.open(100.0, 100.0);

		assertEquals(2500.0, budget.claimJoules(2500.0, false), EPS);
		// 2500 J / 125 = 20 kW
		assertEquals(80.0, budget.netKw(100.0), EPS);

		budget.drawForGrid(80.0);
		assertEquals(0.0, budget.getAvailableJoules(), EPS);
		assertEquals(0.0, budget.claimJoules(1.0, false), EPS);
	}

	/** A switch moved: the grid is solved again in the same tick and must see the same generator. */
	@Test
	void aSecondReadingInTheSameTickIsTheSame() {
		AtomicLong tick = new AtomicLong(3);
		GeneratorBudget budget = new GeneratorBudget(NO_LIMITS, tick::get);
		budget.open(50.0, 100.0);

		double first = budget.netKw(50.0);
		budget.drawForGrid(first);

		assertEquals(first, budget.netKw(50.0), EPS);
	}

	/** A generator whose chunk stopped ticking froze its last output; it is not simulated, so it makes nothing. */
	@Test
	void aBudgetNotOpenedForTwoTicksOffersNothing() {
		AtomicLong tick = new AtomicLong(10);
		GeneratorBudget budget = new GeneratorBudget(NO_LIMITS, tick::get);
		budget.open(100.0, 100.0);

		// the next tick, before the generator has ticked, last tick's budget still counts
		tick.set(11);
		assertEquals(100.0, budget.netKw(100.0), EPS);
		assertEquals(12_500.0, budget.getAvailableJoules(), EPS);

		tick.set(12);
		assertEquals(0.0, budget.netKw(100.0), EPS);
		assertEquals(0.0, budget.getAvailableJoules(), EPS);
		assertEquals(0.0, budget.claimJoules(100.0, false), EPS);
	}

	@Test
	void aBudgetNeverOpenedOffersNothing() {
		GeneratorBudget budget = new GeneratorBudget(NO_LIMITS, () -> 0L);

		assertEquals(0.0, budget.netKw(100.0), EPS);
		assertEquals(0.0, budget.getAvailableJoules(), EPS);
	}

	/** A cable that looks before the first tick after a load reads the nameplate's maximum, not nought. */
	@Test
	void theMaximumIsThereBeforeTheFirstTick() {
		GeneratorBudget budget = new GeneratorBudget(NO_LIMITS, () -> 0L);
		assertEquals(0.0, budget.getMaxJoulesPerTick(), EPS);
		assertEquals(PRODUCED_KW * JOULES_PER_KW, budget.getMaxJoulesPerTick(PRODUCED_KW), EPS);
	}
}
