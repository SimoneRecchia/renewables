package io.github.simonerecchia.renewables.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.PvModuleSpec;
import io.github.simonerecchia.renewables.catalog.PvCatalog;
import io.github.simonerecchia.renewables.catalog.PvModuleCatalog;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every module's datasheet against the physics a datasheet cannot escape: the maximum power point multiplies out
 * to the nameplate, the fill factor is that of a real cell, and the open-circuit voltage is what that many cells
 * in series make.  The HB-066-440 used to open at 80.5 V on 66 cells, which is 1.22 V a cell; silicon makes 0.75.
 */
class PvModuleDatasheetTest {
	/** A module and how many of its cells are in series: a half-cut layout is two halves in parallel. */
	private record Module(PvModuleSpec spec, int seriesCells, double lowVoltsPerCell, double highVoltsPerCell) {
	}

	private static final List<Module> MODULES = List.of(
			new Module(PvModuleCatalog.HP_108_415, 54, 0.66, 0.72),
			new Module(PvModuleCatalog.HJ_132_430, 66, 0.72, 0.76),
			new Module(PvModuleCatalog.HB_066_440, 66, 0.70, 0.76),
			new Module(PvModuleCatalog.HT_268_530, 268, 0.80, 0.90),
			new Module(PvModuleCatalog.HN_144_580, 72, 0.69, 0.74),
			new Module(PvModuleCatalog.HN_132_700, 66, 0.69, 0.74));

	@Test
	void theMaximumPowerPointMultipliesOutToTheNameplate() {
		for (Module m : MODULES) {
			PvModuleSpec spec = m.spec();
			assertEquals(spec.ratedPowerW(), spec.vmp() * spec.imp(), spec.ratedPowerW() * 0.01, spec.displayName());
		}
	}

	@Test
	void theFillFactorIsThatOfARealCell() {
		for (Module m : MODULES) {
			PvModuleSpec spec = m.spec();
			double fillFactor = spec.ratedPowerW() / (spec.voc() * spec.isc());
			assertTrue(fillFactor > 0.70 && fillFactor < 0.86, spec.displayName() + " FF " + fillFactor);
		}
	}

	@Test
	void theOpenCircuitVoltageIsWhatTheCellsInSeriesMake() {
		for (Module m : MODULES) {
			double perCell = m.spec().voc() / m.seriesCells();
			assertTrue(perCell >= m.lowVoltsPerCell() && perCell <= m.highVoltsPerCell(), m.spec().displayName() + " " + perCell + " V a cell");
		}
	}

	/** Between 18 and 24 % at standard conditions, CdTe at the bottom and back contact at the top. */
	@Test
	void efficienciesAreTodays() {
		for (Module m : MODULES) {
			double efficiency = m.spec().efficiency();
			assertTrue(efficiency > 0.18 && efficiency < 0.24, m.spec().displayName() + " " + efficiency);
		}
	}

	/** The fuse a string needs, 1.5 Isc, is one its modules allow. */
	@Test
	void everyModuleTakesTheFuseItsStringNeeds() {
		for (Module m : MODULES) {
			assertTrue(1.5 * m.spec().isc() <= m.spec().seriesFuseA(), m.spec().displayName());
		}
	}

	/** Loading the array catalogue builds every product, and each one checks its strings against the cold morning. */
	@Test
	void everyArrayKeepsItsStringsUnderTheModulesRating() {
		PvCatalog.all().forEach(array -> assertTrue(
				array.stringOpenCircuitVoltage(-10.0) <= array.module().maxSystemVolts(), array.displayName()));
	}
}
