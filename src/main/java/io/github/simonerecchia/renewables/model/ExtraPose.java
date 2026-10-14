package io.github.simonerecchia.renewables.model;

import io.github.simonerecchia.renewables.block.ModelFacing;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A per-machine adjustment on top of the block's own facing: an offset, a yaw and a pitch.
 *
 * Only the utility pole has one, but three things have to agree on it to the pixel - the renderer that draws the
 * model, the picker that decides which fitting is under the crosshair, and the block entity that tells the grid
 * where a wire ends - so the transform lives here, once, and all three call it.
 *
 * For a point {@code p} of the model, in blocks and in the frame it was authored in, the world position is
 * {@code block + (0.5, 0, 0.5) + turn(offset + yaw(pitch(p)))}: pitch about X first, then yaw about Y, both about the
 * model's origin at the middle of the block's base, then the offset, then the quarter turns that take the authored
 * facing to the placed one.
 */
public interface ExtraPose {
	/** Offset in blocks, in the model's frame (before the facing turn). */
	Vec3 poseOffset();

	/** Rotation about the vertical, in degrees. */
	float poseYawDegrees();

	/** Rotation about the east-west axis, in degrees. */
	float posePitchDegrees();

	/** Where a point of the model is in the world; {@code pose} may be null for a machine that has none. */
	static Vec3 toWorld(Vec3 local, BlockPos pos, Direction authored, Direction facing, @Nullable ExtraPose pose) {
		Vec3 p = local;

		if (pose != null) {
			Vector3f v = new Vector3f((float) p.x, (float) p.y, (float) p.z);
			v.rotateX((float) Math.toRadians(pose.posePitchDegrees()));
			v.rotateY((float) Math.toRadians(pose.poseYawDegrees()));
			p = new Vec3(v.x(), v.y(), v.z()).add(pose.poseOffset());
		}

		return Vec3.atLowerCornerOf(pos).add(0.5, 0.0, 0.5).add(ModelFacing.turned(p, authored, facing));
	}
}
