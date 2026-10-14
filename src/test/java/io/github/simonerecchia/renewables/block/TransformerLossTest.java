package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.simonerecchia.renewables.catalog.TransformerCatalog;
import org.junit.jupiter.api.Test;

class TransformerLossTest {
	/** 2.4 kW whatever is drawn, and 1.1 % of the load. */
	@Test
	void aMachineUnitLosesItsCoreAndAShareOfTheLoad() {
		double out = TransformerFlow.output(TransformerCatalog.MACHINE, 1000.0);

		assertEquals(1000.0 - (2.4 + 11.0), out, 1e-9);
	}

	@Test
	void aSubstationLosesLessOfTheLoadAndMoreOfItsCore() {
		double out = TransformerFlow.output(TransformerCatalog.SUBSTATION, 60_000.0);

		assertEquals(60_000.0 - (28.0 + 330.0), out, 1e-9);
	}

	@Test
	void aFlowThatDoesNotCoverTheCoreLossComesOutAsNothingNotNegative() {
		assertEquals(0.0, TransformerFlow.output(TransformerCatalog.SUBSTATION, 10.0), 1e-12);
		assertEquals(0.0, TransformerFlow.output(TransformerCatalog.MACHINE, 0.0), 1e-12);
	}

	@Test
	void theDirectionOfTheFlowIsKept() {
		double out = TransformerFlow.output(TransformerCatalog.MACHINE, -1000.0);

		assertEquals(-(1000.0 - (2.4 + 11.0)), out, 1e-9);
	}
}
