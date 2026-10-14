package io.github.simonerecchia.renewables.client.model;

import io.github.simonerecchia.renewables.model.ExtraPose;
import io.github.simonerecchia.renewables.model.PartBox;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where a machine's model frame sits in the world, for one machine as it is drawn right now.
 *
 * It is not a second copy of the arithmetic.  {@link ExtraPose#toWorld} is the one statement of how a point of the
 * model lands in the world (facing turn, offset, yaw, pitch), shared by the renderer, the wire anchors and this
 * class; the frame is read off it by asking where the origin and three far points go.  So whatever that function
 * does, a ray tested here hits exactly what the renderer drew, and for a pole that is yawed and tilted the ray is
 * brought into the model's frame, where the boxes really are boxes (models.md section 11.3).
 */
final class PartFrame {
	/** How far along each axis the basis points are taken: far enough that float rounding in toWorld is negligible. */
	private static final double REACH = 16.0;

	private final Vec3 origin;
	/** Rows of the inverse of the linear part: world offset from the origin -> model coordinates. */
	private final double[][] inverse;
	/** Columns of the linear part: where the model's x, y, z unit vectors point in the world. */
	private final double[][] linear;

	private PartFrame(Vec3 origin, double[][] linear, double[][] inverse) {
		this.origin = origin;
		this.linear = linear;
		this.inverse = inverse;
	}

	/** The frame of the machine at {@code pos}; {@code pose} is null for a machine with no extra transform. */
	static PartFrame of(BlockPos pos, Direction authored, Direction facing, @Nullable ExtraPose pose) {
		Vec3 o = ExtraPose.toWorld(Vec3.ZERO, pos, authored, facing, pose);
		Vec3 x = ExtraPose.toWorld(new Vec3(REACH, 0, 0), pos, authored, facing, pose).subtract(o).scale(1.0 / REACH);
		Vec3 y = ExtraPose.toWorld(new Vec3(0, REACH, 0), pos, authored, facing, pose).subtract(o).scale(1.0 / REACH);
		Vec3 z = ExtraPose.toWorld(new Vec3(0, 0, REACH), pos, authored, facing, pose).subtract(o).scale(1.0 / REACH);

		double[][] linear = {{x.x, y.x, z.x}, {x.y, y.y, z.y}, {x.z, y.z, z.z}};
		return new PartFrame(o, linear, invert(linear));
	}

	/** A point of the model, in the world. */
	Vec3 toWorld(Vec3 local) {
		return origin.add(apply(linear, local.x, local.y, local.z));
	}

	/** A point of the world, in the model's frame. */
	Vec3 toLocalPoint(Vec3 world) {
		Vec3 d = world.subtract(origin);
		return apply(inverse, d.x, d.y, d.z);
	}

	/** A direction of the world, in the model's frame (no translation). */
	Vec3 toLocalDirection(Vec3 world) {
		return apply(inverse, world.x, world.y, world.z);
	}

	/**
	 * The ray's parameter where it enters the box, or NaN for a miss.  The parameter means the same on the world ray
	 * and on its image in the model's frame, because the map between them is affine, so entries of different boxes
	 * of the same machine compare directly.
	 */
	double entry(PartBox box, Vec3 worldOrigin, Vec3 worldDirection) {
		Vec3 o = toLocalPoint(worldOrigin);
		Vec3 d = toLocalDirection(worldDirection);

		return Rays.entry(o.x, o.y, o.z, d.x, d.y, d.z,
				box.min().x(), box.min().y(), box.min().z(), box.max().x(), box.max().y(), box.max().z());
	}

	private static Vec3 apply(double[][] m, double x, double y, double z) {
		return new Vec3(m[0][0] * x + m[0][1] * y + m[0][2] * z,
				m[1][0] * x + m[1][1] * y + m[1][2] * z,
				m[2][0] * x + m[2][1] * y + m[2][2] * z);
	}

	/** The inverse of a 3x3 by cofactors; the identity for one that cannot be inverted, which a pose never is. */
	private static double[][] invert(double[][] m) {
		double c00 = m[1][1] * m[2][2] - m[1][2] * m[2][1];
		double c01 = m[1][2] * m[2][0] - m[1][0] * m[2][2];
		double c02 = m[1][0] * m[2][1] - m[1][1] * m[2][0];
		double det = m[0][0] * c00 + m[0][1] * c01 + m[0][2] * c02;
		if (Math.abs(det) < 1.0e-12) return new double[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

		double s = 1.0 / det;
		return new double[][] {
				{c00 * s, (m[0][2] * m[2][1] - m[0][1] * m[2][2]) * s, (m[0][1] * m[1][2] - m[0][2] * m[1][1]) * s},
				{c01 * s, (m[0][0] * m[2][2] - m[0][2] * m[2][0]) * s, (m[0][2] * m[1][0] - m[0][0] * m[1][2]) * s},
				{c02 * s, (m[0][1] * m[2][0] - m[0][0] * m[2][1]) * s, (m[0][0] * m[1][1] - m[0][1] * m[1][0]) * s}};
	}
}
