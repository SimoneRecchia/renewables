package io.github.simonerecchia.renewables.client.hooks;

import io.github.simonerecchia.renewables.client.ClientBlockEntities;
import io.github.simonerecchia.renewables.client.model.PartPicker;
import io.github.simonerecchia.renewables.core.ClientConfig;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.model.ModelDefinition;
import io.github.simonerecchia.renewables.model.ModelDefinitions;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Which insulator is under a ray: the choice of a fitting, for the click and for the preview alike
 * (docs/spec/client-net.md section 3).
 *
 * Only insulators are candidates - aiming at a pole's shaft selects nothing, rather than starting a connection that
 * could never finish - and where two lie on the ray the one nearer the eye wins ({@link PartPicker#pointedFitting}
 * does the ray; this turns its answer into an id, an index and a point).  A machine with fittings and no model
 * definition, a ground-laid run, is aimed at by a box round each anchor instead ({@link #bareFitting}).
 */
@OnlyIn(Dist.CLIENT)
public final class FittingSelector {
	/** How far from the player, per axis, a click in the air looks for a machine: the reach of a lattice tower. */
	public static final int AIR_REACH = 16;

	/**
	 * Half the side of the box a fitting with no model group is aimed at, round its anchor: four pixels, which holds
	 * a run's dead-end clamp in any of its four turns, a splayed bundle's four included (gen_conductor_models CLAMP).
	 */
	static final double BARE_FITTING_HALF = 4.0 / 16.0;
	/** Longer than the diagonal of the air click's cube, so the segment stands in for the picker's endless ray. */
	private static final double BARE_RAY_LENGTH = 64.0;
	/** What a fitting with no model group is called, for {@code InsulatorHost.powerType}. */
	private static final String BARE_PART_PREFIX = "fitting_";

	private FittingSelector() {
	}

	/**
	 * An insulator the player has picked: whose it is, which, and the point a wire is attached to or aimed at.
	 *
	 * @param id     the fitting's persistent id; zero or less while the server has not numbered it yet
	 * @param anchor where a finished wire would end here, or null if the machine cannot tell yet
	 * @param point  the click's point: the anchor with {@code previewSnapping}, the middle of the porcelain without
	 */
	public record Selection(BlockPos hostPos, InsulatorHost host, int index, int id, String part, Vec3 point,
			@Nullable Vec3 anchor) {
	}

	/** The insulator of the machine at {@code hostPos} that the ray from {@code eye} along {@code look} passes through. */
	@Nullable
	public static Selection select(Level level, BlockPos hostPos, Vec3 eye, Vec3 look) {
		BlockEntity entity = level.getBlockEntity(hostPos);
		if (!(entity instanceof InsulatorHost host)) return null;

		ModelDefinition definition = ModelDefinitions.of(level.getBlockState(hostPos).getBlock());
		// a ground-laid run is drawn from block models: no OBJ group for the picker, only the anchor it says
		if (definition == null) return bareFitting(hostPos, host, eye, look);

		String part = PartPicker.pointedFitting(level, hostPos, eye, look);
		if (part == null) return null;

		int index = definition.insulators().indexOf(part);
		if (index < 0) return null;

		int[] ids = host.getInsulatorIds();
		int id = index < ids.length ? ids[index] : 0;
		Vec3 anchor = host.getWirePosition(index);
		Vec3 centre = PartPicker.partCenter(level, hostPos, part);

		Vec3 point;
		if (ClientConfig.previewSnapping()) {
			point = anchor != null ? anchor : centre;
		} else {
			point = centre != null ? centre : anchor;
		}

		if (point == null) point = Vec3.atCenterOf(hostPos);
		return new Selection(hostPos.immutable(), host, index, id, part, point, anchor);
	}

	/**
	 * The fitting of a machine with no model groups - a run's dead-end clamp - aimed at like an insulator: the ray has
	 * to pass through a box of {@link #BARE_FITTING_HALF} round the fitting's anchor, the nearest entry winning.  The
	 * anchor is both the point and where the wire ends, since there is no porcelain to take the middle of.
	 */
	@Nullable
	static Selection bareFitting(BlockPos hostPos, InsulatorHost host, Vec3 eye, Vec3 look) {
		int[] ids = host.getInsulatorIds();
		Vec3 far = eye.add(look.normalize().scale(BARE_RAY_LENGTH));

		Selection best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int index = 0; index < ids.length; index++) {
			Vec3 anchor = host.getWirePosition(index);
			if (anchor == null) continue;

			// cut at the block's floor: the ray goes on through the ground, and nothing is drawn under it
			AABB box = new AABB(anchor.x - BARE_FITTING_HALF, Math.max(anchor.y - BARE_FITTING_HALF, hostPos.getY()),
					anchor.z - BARE_FITTING_HALF, anchor.x + BARE_FITTING_HALF, anchor.y + BARE_FITTING_HALF,
					anchor.z + BARE_FITTING_HALF);
			Vec3 entry = box.clip(eye, far).orElse(null);
			if (entry == null) continue;

			double distance = entry.distanceToSqr(eye);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = new Selection(hostPos.immutable(), host, index, ids[index], BARE_PART_PREFIX + index, anchor, anchor);
			}
		}

		return best;
	}

	/**
	 * For a click in the air, which reaches past the block range: the insulator, among all the machines within
	 * {@link #AIR_REACH} blocks of {@code around} on each axis, whose point is nearest the eye.
	 *
	 * Nearest by the selected point, not by where the ray enters the box; and nothing is tested for being behind a
	 * wall - the whole point is to reach the top of a tower from the ground.
	 */
	@Nullable
	public static Selection nearestInReach(Level level, BlockPos around, Vec3 eye, Vec3 look) {
		Selection best = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockEntity entity : ClientBlockEntities.hosts()) {
			BlockPos pos = entity.getBlockPos();
			if (Math.abs(pos.getX() - around.getX()) > AIR_REACH || Math.abs(pos.getY() - around.getY()) > AIR_REACH
					|| Math.abs(pos.getZ() - around.getZ()) > AIR_REACH) {
				continue;
			}

			if (entity.getLevel() != level) continue;

			Selection candidate = select(level, pos, eye, look);
			if (candidate == null) continue;

			double distance = candidate.point().distanceToSqr(eye);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}

		return best;
	}
}
