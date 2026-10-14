package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.catalog.ConductorCatalog;
import io.github.simonerecchia.renewables.catalog.TowerCatalog;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** A span is held to the shortest of its conductor's limit and what each end will hold. */
class SpanLimitTest {
	private static InsulatorHost holding(double blocks) {
		return new InsulatorHost() {
			@Override
			public int[] getInsulatorIds() {
				return new int[] {1};
			}

			@Override
			public Vec3 getWirePosition(int index) {
				return Vec3.ZERO;
			}

			@Override
			public String fittingType() {
				return FittingTypes.LATTICE_TOWER;
			}

			@Override
			public boolean feeds(InsulatorHost other) {
				return true;
			}

			@Override
			public double maxSpanBlocks() {
				return blocks;
			}

			@Override
			public double getCurrentPower() {
				return 0.0;
			}
		};
	}

	/** A machine with no limit of its own leaves the conductor's, which is all a pole or a cabin ever had. */
	@Test
	void theConductorDecidesWhereNoEndHasALimit() {
		ConductorSpec line = ConductorCatalog.ACSR_592_QUAD;
		InsulatorHost free = holding(Double.POSITIVE_INFINITY);

		assertEquals(line.maxSpan(), SpanPlanner.longestSpan(line, free, free), 0.0);
	}

	/**
	 * The towers' ruling spans were never read.  Today they cannot bind - the quad bundle stops at 160 blocks and the
	 * shortest tower holds 160 - but a tower rated shorter than the conductor, at either end, now holds the span to it.
	 */
	@Test
	void aTowerHoldsTheSpanToItsRulingSpanAtEitherEnd() {
		ConductorSpec line = ConductorCatalog.ACSR_592_QUAD;
		InsulatorHost suspension = holding(TowerCatalog.SUSPENSION.maxSpan());
		InsulatorHost shorter = holding(120.0);

		assertEquals(Math.min(line.maxSpan(), TowerCatalog.SUSPENSION.maxSpan()), SpanPlanner.longestSpan(line, suspension, suspension), 0.0);
		assertEquals(120.0, SpanPlanner.longestSpan(line, suspension, shorter), 0.0);
		assertEquals(120.0, SpanPlanner.longestSpan(line, shorter, suspension), 0.0);
	}
}
