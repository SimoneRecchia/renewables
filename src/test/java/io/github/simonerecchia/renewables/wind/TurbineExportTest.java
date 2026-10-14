package io.github.simonerecchia.renewables.wind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.simonerecchia.renewables.api.power.TickBudget;
import io.github.simonerecchia.renewables.compat.energy.EnergyBridge;

import org.junit.jupiter.api.Test;

/** No joule is spent twice: what the cables of other mods take is what the wire grid does not get. */
class TurbineExportTest {
	@Test
	void unitsAgreeWithTheEnergyBridge() {
		assertEquals(EnergyBridge.JOULES_PER_KW, TurbineExport.JOULES_PER_KW_TICK, 0.0);
	}

	@Test
	void specificationExample() {
		// 78.75 kW at nameplate, the whole of it exported, no ceiling: 9843.75 J on the table
		double capacity = TurbineExport.capacityJoules(78.75, 1.0, Double.MAX_VALUE);
		assertEquals(9843.75, capacity, 1e-9);
		TickBudget budget = new TickBudget();
		budget.open(TurbineExport.offeredJoules(78.75, capacity));
		assertEquals(9843.75, budget.available(), 1e-9);

		// a Mekanism cable takes 2500 J of it: the grid has 78.75 - 20 = 58.75 kW left
		assertEquals(2500.0, budget.claim(2500.0, false), 1e-9);
		assertEquals(58.75, TurbineExport.gridKw(78.75, budget.claimed()), 1e-9);
		assertEquals(9843.75 - 2500.0, budget.available(), 1e-9);
	}

	@Test
	void fourMegawattsIsHalfAMillionJoulesATick() {
		assertEquals(500_000.0, TurbineExport.capacityJoules(4000.0, 1.0, Double.MAX_VALUE), 1e-9);
	}

	@Test
	void fractionAndCeilingBothLimit() {
		assertEquals(100_000.0, TurbineExport.capacityJoules(4000.0, 0.2, Double.MAX_VALUE), 1e-9);
		assertEquals(2000.0, TurbineExport.capacityJoules(4000.0, 1.0, 2000.0), 1e-9);
		assertEquals(0.0, TurbineExport.capacityJoules(4000.0, 0.0, Double.MAX_VALUE), 1e-9);
	}

	@Test
	void whatIsOfferedIsWhatIsMadeUnderTheCapacity() {
		assertEquals(125.0 * 1000.0, TurbineExport.offeredJoules(1000.0, 500_000.0), 1e-9);
		assertEquals(500_000.0, TurbineExport.offeredJoules(4000.0, 500_000.0), 1e-9);
		// a turbine making more than its share offers only the share, and the rest stays on the grid
		assertEquals(100_000.0, TurbineExport.offeredJoules(4000.0, 100_000.0), 1e-9);
	}

	@Test
	void noCapacityOffersNothing() {
		assertEquals(0.0, TurbineExport.offeredJoules(4000.0, 0.0), 0.0);
		assertEquals(0.0, TurbineExport.offeredJoules(4000.0, -1.0), 0.0);
		assertEquals(0.0, TurbineExport.offeredJoules(-10.0, 1000.0), 0.0);
	}

	@Test
	void theGridNeverGetsLessThanNothing() {
		assertEquals(0.0, TurbineExport.gridKw(10.0, 5000.0), 0.0);
		assertEquals(10.0, TurbineExport.gridKw(10.0, 0.0), 1e-9);
	}

	@Test
	void forgeEnergyViewClaimsWholeFeAndKeepsTheRemainder() {
		TickBudget budget = new TickBudget();
		budget.open(1001.0);
		var view = EnergyBridge.forgeEnergyView(new TickBudgetSource(budget, 100_000.0));

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
	void simulatedExtractionTakesNothing() {
		TickBudget budget = new TickBudget();
		budget.open(1000.0);
		var view = EnergyBridge.forgeEnergyView(new TickBudgetSource(budget, 100_000.0));
		assertEquals(400, view.extractEnergy(400, true));
		assertEquals(1000.0, budget.available(), 1e-9);
	}

	@Test
	void forgeEnergyViewNeverReceives() {
		TickBudget budget = new TickBudget();
		budget.open(1000.0);
		var view = EnergyBridge.forgeEnergyView(new TickBudgetSource(budget, 100_000.0));
		assertEquals(0, view.receiveEnergy(1000, false));
		assertEquals(false, view.canReceive());
		assertEquals(true, view.canExtract());
	}

	/** A budget with a ceiling, as a generator presents it to the bridge. */
	private record TickBudgetSource(TickBudget budget, double maxPerTick) implements io.github.simonerecchia.renewables.api.power.IEnergyBudget {
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
}
