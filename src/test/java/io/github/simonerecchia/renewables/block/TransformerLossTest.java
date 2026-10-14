package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.TransformerSpec;
import io.github.simonerecchia.renewables.catalog.TransformerCatalog;
import org.junit.jupiter.api.Test;

/** P0 + Pk (S / Sr)^2, against figures worked out by hand. */
class TransformerLossTest {
	/** 1000 kW through 2500 kVA is 0.4 of the rating: 1.811 + 20.35 x 0.16 = 5.067 kW. */
	@Test
	void aMachineUnitLosesItsCoreAndTheSquareOfItsLoad() {
		double out = TransformerFlow.output(TransformerCatalog.MACHINE, 1000.0);

		assertEquals(1000.0 - 5.067, out, 1e-9);
		// at full load the windings are the 20.35 kW Ecodesign allows
		assertEquals(1.811 + 20.35, TransformerCatalog.MACHINE.lossKw(2500.0), 1e-9);
	}

	/** 60 MW through 63 MVA: 28 + 230 x (60/63)^2 = 236.617 kW, 0.39 %. */
	@Test
	void aSubstationLosesLessOfTheLoadAndMoreOfItsCore() {
		double out = TransformerFlow.output(TransformerCatalog.SUBSTATION, 60_000.0);

		assertEquals(60_000.0 - 236.6168, out, 1e-3);
	}

	/**
	 * The Ecodesign peak efficiency index of the substation unit, 1 - 2 sqrt(P0 Pk) / Sr, clears the 99.745 % Tier 2
	 * asks of a 63 MVA transformer; it peaks where the winding loss equals the core's, at 35 % load.
	 */
	@Test
	void theSubstationUnitMeetsTheEcodesignPeakEfficiency() {
		TransformerSpec spec = TransformerCatalog.SUBSTATION;
		double pei = 1.0 - 2.0 * Math.sqrt(spec.coreLossKw() * spec.loadLossKw()) / spec.ratingKva();

		assertTrue(pei >= 0.99745, "PEI " + pei);
		double peak = spec.ratingKva() * Math.sqrt(spec.coreLossKw() / spec.loadLossKw());
		assertEquals(pei, spec.efficiencyAt(peak), 1e-9);
	}

	@Test
	void aFlowThatDoesNotCoverTheCoreLossComesOutAsNothingNotNegative() {
		assertEquals(0.0, TransformerFlow.output(TransformerCatalog.SUBSTATION, 10.0), 1e-12);
		assertEquals(0.0, TransformerFlow.output(TransformerCatalog.MACHINE, 0.0), 1e-12);
	}

	@Test
	void theDirectionOfTheFlowIsKept() {
		double out = TransformerFlow.output(TransformerCatalog.MACHINE, -1000.0);

		assertEquals(-(1000.0 - 5.067), out, 1e-9);
	}
}
