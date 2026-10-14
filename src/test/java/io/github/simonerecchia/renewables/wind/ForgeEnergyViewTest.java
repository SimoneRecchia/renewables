package io.github.simonerecchia.renewables.wind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.IEnergyBudget;
import io.github.simonerecchia.renewables.api.power.TickBudget;
import io.github.simonerecchia.renewables.compat.energy.EnergyBridge;
import io.github.simonerecchia.renewables.grid.EnergyBudgets;

import net.minecraftforge.energy.IEnergyStorage;
import org.junit.jupiter.api.Test;

/** What a turbine's budget looks like through Forge Energy, and that a joule claimed there is a joule the grid does not get. */
class ForgeEnergyViewTest {
	/** A budget with a ceiling, as a generator presents it to the bridge. */
	private record Source(TickBudget budget, double maxPerTick) implements IEnergyBudget {
		@Override
		public double getAvailableJoules() {
			return budget.available();
		}

		@Override
		public double getMaxJoulesPerTick() {
			return maxPerTick;
		}

		@Override
		public double claimJoules(double joules, boolean simulate) {
			return budget.claim(joules, simulate);
		}
	}

	private static IEnergyStorage view(TickBudget budget) {
		return EnergyBridge.forgeEnergyView(new Source(budget, 100_000.0));
	}

	@Test
	void specificationExample() {
		// 78.75 kW at nameplate, the whole of it exported, no ceiling: 9843.75 J on the table
		TickBudget budget = new TickBudget();
		budget.open(EnergyBudgets.offeredJoules(true, 78.75, 78.75, 1.0, Double.MAX_VALUE));
		assertEquals(9843.75, budget.available(), 1e-9);

		// a Mekanism cable takes 2500 J of it: the grid has 78.75 - 20 = 58.75 kW left
		assertEquals(2500.0, budget.claim(2500.0, false), 1e-9);
		assertEquals(58.75, EnergyBudgets.netKw(78.75, budget.claimed()), 1e-9);
	}

	@Test
	void fourMegawattsIsHalfAMillionJoulesATick() {
		assertEquals(500_000.0, EnergyBudgets.maxJoulesPerTick(true, 4000.0, 1.0, Double.MAX_VALUE), 1e-9);
	}

	@Test
	void claimsThroughForgeEnergyTakeWholeFeAndKeepTheRemainder() {
		TickBudget budget = new TickBudget();
		budget.open(1001.0);
		IEnergyStorage view = view(budget);

		// 1001 J is 400 whole FE (1000 J); the extra joule is not burnt
		assertEquals(400, view.getEnergyStored());
		assertEquals(40, view.extractEnergy(40, false));
		assertEquals(1001.0 - 100.0, budget.available(), 1e-9);
		// asking more than is there gives what is there, in whole FE
		assertEquals(360, view.extractEnergy(1000, false));
		assertEquals(1.0, budget.available(), 1e-9);
		assertEquals(0, view.extractEnergy(1000, false));
	}

	@Test
	void whatACableTakesIsWhatTheGridDoesNotGet() {
		TickBudget budget = new TickBudget();
		budget.open(EnergyBudgets.offeredJoules(true, 1000.0, 4000.0, 1.0, Double.MAX_VALUE));
		assertEquals(1000.0, EnergyBudgets.netKw(1000.0, budget.claimed()), 1e-9);

		// 100 FE is 250 J is 2 kW
		assertEquals(100, view(budget).extractEnergy(100, false));
		assertEquals(998.0, EnergyBudgets.netKw(1000.0, budget.claimed()), 1e-9);
	}

	@Test
	void simulatedExtractionTakesNothing() {
		TickBudget budget = new TickBudget();
		budget.open(1000.0);
		assertEquals(400, view(budget).extractEnergy(400, true));
		assertEquals(1000.0, budget.available(), 1e-9);
	}

	@Test
	void neverReceives() {
		TickBudget budget = new TickBudget();
		budget.open(1000.0);
		IEnergyStorage view = view(budget);
		assertEquals(0, view.receiveEnergy(1000, false));
		assertFalse(view.canReceive());
		assertTrue(view.canExtract());
	}
}
