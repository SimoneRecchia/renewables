package io.github.simonerecchia.renewables.client.model;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Which part of a machine's model is under a ray: the crosshair, the wire preview, the wrench.
 *
 * STUB: owner M.  Works on per-group boxes, not on the triangles (docs/spec/models.md section 11).  Client only.
 */
public final class PartPicker {
	private PartPicker() {
	}

	/** Whether the ray passes through the box of the named group of the machine at {@code pos}. */
	public static boolean isPointed(Level level, BlockPos pos, String part, Vec3 origin, Vec3 direction) {
		return false;
	}

	/** The wire fitting the ray hits first (nearest along the ray), or null. */
	@Nullable
	public static String pointedFitting(Level level, BlockPos pos, Vec3 origin, Vec3 direction) {
		return null;
	}

	/** Like {@link #pointedFitting} but over every group of the model; a convenience, never used to pick a fitting. */
	@Nullable
	public static String pointedAnyPart(Level level, BlockPos pos, Vec3 origin, Vec3 direction) {
		return null;
	}

	/** The world position of the middle of a group's box, or null when the group or the model is unknown. */
	@Nullable
	public static Vec3 partCenter(Level level, BlockPos pos, String part) {
		return null;
	}

	/** Where the ray enters the nearest group box of the machine, or null when it hits none. */
	@Nullable
	public static Vec3 nearestHit(Level level, BlockPos pos, Vec3 origin, Vec3 direction) {
		return null;
	}
}
