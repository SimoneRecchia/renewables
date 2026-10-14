package io.github.simonerecchia.renewables.client.wire;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.catalog.ConductorCatalog;
import io.github.simonerecchia.renewables.grid.WireConnection;

import javax.annotation.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * One span as the client draws it: the saved connection plus everything worked out from it that does not change
 * from frame to frame - where its two ends are, the shape between them, a box round it, the light on it.
 *
 * Nothing here is recomputed unless something moved ({@link #invalidate}); a frame only reads.  All of it runs on
 * the client's main thread.
 */
@OnlyIn(Dist.CLIENT)
final class ClientSpan {
	/** Frames between two attempts to find the ends of a span whose machines are not loaded. */
	private static final int RETRY_FRAMES = 20;

	final WireConnection wire;
	final long key;
	/** Null for a conductor this build does not know: it is drawn as a thin black wire. */
	@Nullable
	final ConductorSpec conductor;
	final WireStyle style;
	final WireProfile profile = new WireProfile();

	/** Position in the registry's list, which swap-removal keeps current. */
	int slot = -1;

	/** Start and end in the world; valid when {@link #ready}. */
	double sx, sy, sz, ex, ey, ez;
	/** The box round the cable, ends, sag and thickness included; valid when {@link #ready}. */
	AABB bounds = new AABB(0, 0, 0, 0, 0, 0);
	/** Light at the start, the middle and the end, packed; refreshed by the renderer. */
	int lightStart, lightMiddle, lightEnd;
	long lightStamp = Long.MIN_VALUE;

	private boolean ready;
	private long retryAt;

	ClientSpan(WireConnection wire, long key) {
		this.wire = wire;
		this.key = key;
		this.conductor = ConductorCatalog.byPath(wire.wireType());
		this.style = conductor == null ? WireStyle.GENERIC : WireStyles.of(conductor);
	}

	/** A machine at one of the ends moved, reloaded or went: everything cached is stale. */
	void invalidate() {
		ready = false;
		retryAt = 0L;
		lightStamp = Long.MIN_VALUE;
	}

	boolean ready() {
		return ready;
	}

	/**
	 * Finds both ends if they are not known and builds the shape, at most once every {@link #RETRY_FRAMES} frames
	 * while they cannot be found.
	 *
	 * @return whether the span can be drawn
	 */
	boolean prepare(Level level, long frame) {
		if (ready) return true;
		if (frame < retryAt) return false;

		retryAt = frame + RETRY_FRAMES;
		Vec3 start = WireAnchors.anchorOf(level, wire.startInsulatorId(), wire.startBlockPos());
		if (start == null) return false;

		Vec3 end = WireAnchors.anchorOf(level, wire.endInsulatorId(), wire.endBlockPos());
		if (end == null) return false;

		return place(start.x, start.y, start.z, end.x, end.y, end.z);
	}

	/** Sets the ends and rebuilds the shape and the box; false (and not ready) when there is nothing to draw. */
	boolean place(double sx, double sy, double sz, double ex, double ey, double ez) {
		this.sx = sx;
		this.sy = sy;
		this.sz = sz;
		this.ex = ex;
		this.ey = ey;
		this.ez = ez;

		if (!profile.rebuild(ex - sx, ey - sy, ez - sz, style.sag())) {
			ready = false;
			return false;
		}

		// the bundle's wires sit up to a spacing away from the centre line, so the box has to be that much fatter
		double fat = style.radius() + style.spacing() + 1.0e-3;
		double lowY = sy + profile.lowest() - fat;
		bounds = new AABB(Math.min(sx, ex) - fat, lowY, Math.min(sz, ez) - fat,
				Math.max(sx, ex) + fat, Math.max(sy, ey) + fat, Math.max(sz, ez) + fat);
		ready = true;
		return true;
	}

	/** Squared distance from a point to the span's box: zero inside it. */
	double distanceSqTo(double x, double y, double z) {
		double dx = Math.max(Math.max(bounds.minX - x, 0.0), x - bounds.maxX);
		double dy = Math.max(Math.max(bounds.minY - y, 0.0), y - bounds.maxY);
		double dz = Math.max(Math.max(bounds.minZ - z, 0.0), z - bounds.maxZ);
		return dx * dx + dy * dy + dz * dz;
	}
}
