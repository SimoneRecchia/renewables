package io.github.simonerecchia.renewables.client.hooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.simonerecchia.renewables.client.hooks.FittingSelector.Selection;
import io.github.simonerecchia.renewables.grid.InsulatorHost;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** A ground-laid run has a fitting and no model groups: it is aimed at like an insulator, by a box round its clamp. */
class FittingSelectorTest {
	private static final BlockPos RUN = new BlockPos(10, 64, -3);
	/** Where GroundConductorBlockEntity puts its clamp: the block's axis, 1.55 px up. */
	private static final Vec3 CLAMP = new Vec3(10.5, 64 + 1.55 / 16.0, -2.5);

	/** A run as the selector sees one: one numbered fitting, at the clamp. */
	private static final class Run implements InsulatorHost {
		@Override
		public int[] getInsulatorIds() {
			return new int[] {42};
		}

		@Override
		public Vec3 getWirePosition(int index) {
			return index == 0 ? CLAMP : null;
		}

		@Override
		public String fittingType() {
			return "ground_conductor";
		}

		@Override
		public boolean feeds(InsulatorHost other) {
			return false;
		}

		@Override
		public double getCurrentPower() {
			return 0.0;
		}
	}

	@Test
	void aimingAtTheClampFromStandingHeightPicksTheRunsOneFitting() {
		Vec3 eye = new Vec3(10.5, 65.62, 0.5);
		Selection selection = FittingSelector.bareFitting(RUN, new Run(), eye, CLAMP.subtract(eye));

		assertNotNull(selection);
		assertEquals(0, selection.index());
		assertEquals(42, selection.id());
		assertEquals(RUN, selection.hostPos());
		assertEquals(CLAMP, selection.anchor());
		assertEquals(CLAMP, selection.point());
	}

	@Test
	void aimingFromAcrossTheYardStillReachesIt() {
		// the click in the air reaches sixteen blocks: the fitting's box is what is tested, not block reach
		Vec3 eye = new Vec3(-5.5, 66.62, -2.5);
		assertNotNull(FittingSelector.bareFitting(RUN, new Run(), eye, CLAMP.subtract(eye)));
	}

	@Test
	void theSkyAboveTheRunIsNotTheClamp() {
		Vec3 eye = new Vec3(10.5, 65.62, 0.5);
		assertNull(FittingSelector.bareFitting(RUN, new Run(), eye, new Vec3(0.0, 1.0, -1.0)));
	}

	@Test
	void theRunsArmShortOfTheClampIsNotTheClamp() {
		// the ray goes on into the ground past the arm, and the box stops at the block's floor
		Vec3 eye = new Vec3(10.5, 65.62, 0.5);
		Vec3 arm = new Vec3(10.5, 64.05, -2.05);
		assertNull(FittingSelector.bareFitting(RUN, new Run(), eye, arm.subtract(eye)));
	}

	@Test
	void theClampStandsInFrontOfTheArmBehindIt() {
		Vec3 eye = new Vec3(10.5, 65.62, 0.5);
		Vec3 farArm = new Vec3(10.5, 64.05, -2.95);
		assertNotNull(FittingSelector.bareFitting(RUN, new Run(), eye, farArm.subtract(eye)));
	}

	@Test
	void aRayPointingAwayFindsNothing() {
		Vec3 eye = new Vec3(10.5, 65.62, 0.5);
		assertNull(FittingSelector.bareFitting(RUN, new Run(), eye, eye.subtract(CLAMP)));
	}
}
