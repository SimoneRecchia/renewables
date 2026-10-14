package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.simonerecchia.renewables.model.ExtraPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The arithmetic a model's frame goes through to land in the world: three readers depend on it agreeing. */
class FacingMathTest {
	private static final double EPS = 1e-5;

	private static void assertVec(double x, double y, double z, Vec3 actual) {
		assertEquals(x, actual.x, EPS, "x of " + actual);
		assertEquals(y, actual.y, EPS, "y of " + actual);
		assertEquals(z, actual.z, EPS, "z of " + actual);
	}

	@Test
	void quarterTurnsFromTheAuthoredFacing() {
		assertEquals(0, ModelFacing.quarters(Direction.NORTH, Direction.NORTH));
		assertEquals(2, ModelFacing.quarters(Direction.NORTH, Direction.SOUTH));
		assertEquals(1, ModelFacing.quarters(Direction.NORTH, Direction.WEST));
		assertEquals(3, ModelFacing.quarters(Direction.NORTH, Direction.EAST));
	}

	/** grid-blocks.md 1.2: q=1 (x,z) -> (z,-x); q=2 -> (-x,-z); q=3 -> (-z,x). */
	@Test
	void cellOffsetsTurnAsTheSpecificationSays() {
		BlockPos arm = new BlockPos(2, 4, 1);

		assertEquals(new BlockPos(2, 4, 1), MachineShell.turned(arm, 0));
		assertEquals(new BlockPos(1, 4, -2), MachineShell.turned(arm, 1));
		assertEquals(new BlockPos(-2, 4, -1), MachineShell.turned(arm, 2));
		assertEquals(new BlockPos(-1, 4, 2), MachineShell.turned(arm, 3));
	}

	/** A cell turned by a quarter and by the same facing's point turn land on the same block: tables and anchors agree. */
	@Test
	void aCellOffsetAndAPointTurnTheSameWay() {
		for (Direction facing : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
			int quarters = ModelFacing.quarters(Direction.NORTH, facing);
			BlockPos cell = MachineShell.turned(new BlockPos(-2, 0, 0), quarters);
			Vec3 point = ModelFacing.turned(new Vec3(-2.0, 0.0, 0.0), Direction.NORTH, facing);

			assertEquals(cell.getX(), Math.round(point.x), "x at " + facing);
			assertEquals(cell.getZ(), Math.round(point.z), "z at " + facing);
		}
	}

	private static ExtraPose pose(Vec3 offset, float yaw, float pitch) {
		return new ExtraPose() {
			@Override
			public Vec3 poseOffset() {
				return offset;
			}

			@Override
			public float poseYawDegrees() {
				return yaw;
			}

			@Override
			public float posePitchDegrees() {
				return pitch;
			}
		};
	}

	@Test
	void noPoseIsJustTheFacingAndTheBlock() {
		Vec3 at = ExtraPose.toWorld(new Vec3(1.0, 5.0, 0.0), new BlockPos(10, 64, 20), Direction.NORTH, Direction.NORTH, null);

		assertVec(11.5, 69.0, 20.5, at);
	}

	@Test
	void aZeroPoseIsNoPose() {
		Vec3 plain = ExtraPose.toWorld(new Vec3(1.2, 6.0, -0.3), BlockPos.ZERO, Direction.NORTH, Direction.EAST, null);
		Vec3 zero = ExtraPose.toWorld(new Vec3(1.2, 6.0, -0.3), BlockPos.ZERO, Direction.NORTH, Direction.EAST, pose(Vec3.ZERO, 0, 0));

		assertVec(plain.x, plain.y, plain.z, zero);
	}

	/** The offset is in the model's frame, so it turns with the pole and is not an offset in the world. */
	@Test
	void theOffsetTurnsWithTheFacing() {
		ExtraPose shifted = pose(new Vec3(1.0, 0.0, 0.0), 0, 0);

		// a point at the model's origin, offset a block along the model's +x: east of the block when facing north; facing west the
		// front has swung a quarter to the left, and the model's east side with it, to the north
		assertVec(1.5, 0.0, 0.5, ExtraPose.toWorld(Vec3.ZERO, BlockPos.ZERO, Direction.NORTH, Direction.NORTH, shifted));
		assertVec(0.5, 0.0, -0.5, ExtraPose.toWorld(Vec3.ZERO, BlockPos.ZERO, Direction.NORTH, Direction.WEST, shifted));
	}

	/** Yaw turns about the vertical through the base, the way Vec3.yRot does, and then the offset is added. */
	@Test
	void yawIsAboutTheVerticalAndTheOffsetComesAfter() {
		ExtraPose turned = pose(new Vec3(0.0, 0.0, 1.0), 90.0f, 0);

		// (1,0,0) yawed a quarter -> (0,0,-1); then +1 on z -> the origin
		assertVec(0.5, 0.0, 0.5, ExtraPose.toWorld(new Vec3(1.0, 0.0, 0.0), BlockPos.ZERO, Direction.NORTH, Direction.NORTH, turned));
	}

	/** Pitch comes first: it tilts the top of a 6 m pole off the vertical, along z, before anything else. */
	@Test
	void pitchTiltsTheTopAboutTheEastWestAxis() {
		ExtraPose leaning = pose(Vec3.ZERO, 0, 45.0f);
		Vec3 top = ExtraPose.toWorld(new Vec3(0.0, 6.0, 0.0), BlockPos.ZERO, Direction.NORTH, Direction.NORTH, leaning);

		double lean = 6.0 * Math.sin(Math.toRadians(45.0));
		assertEquals(0.5, top.x, EPS);
		assertEquals(6.0 * Math.cos(Math.toRadians(45.0)), top.y, EPS);
		assertEquals(0.5 + Math.abs(lean), 0.5 + Math.abs(top.z - 0.5), EPS);
	}
}
