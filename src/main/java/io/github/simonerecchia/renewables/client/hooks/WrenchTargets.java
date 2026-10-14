package io.github.simonerecchia.renewables.client.hooks;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.MachineShell;
import io.github.simonerecchia.renewables.block.TurbineTowerBlock;
import io.github.simonerecchia.renewables.client.model.PartPicker;
import io.github.simonerecchia.renewables.net.PanelGuard;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * What the power wrench is pointed at (docs/spec/client-net.md section 4; grid-blocks.md section 9.1).
 *
 * In order: the machine whose model the look ray passes through (any part, not only a fitting), among those within
 * the wrench's reach and in view; failing that, the block under the crosshair, resolved to the machine it belongs to;
 * and for a turbine's tower, the turbine seated on top of it.  The scan is a cube of {@link #REACH} blocks round the
 * player and runs once per click, which is fine for a click and not for a frame.
 */
@OnlyIn(Dist.CLIENT)
public final class WrenchTargets {
	/** The wrench's reach in blocks, the same number the server's panel guard is built on. */
	public static final int REACH = (int) PanelGuard.WRENCH_REACH;

	/** How far short of the point a machine's box is entered the line-of-sight check stops, so the box itself is not "in the way". */
	private static final double CLEARANCE = 0.02;

	private WrenchTargets() {
	}

	/** The position of the machine to open a screen for, or null if the wrench is pointed at nothing of the mod's. */
	@Nullable
	public static BlockPos find(Level level, Player player) {
		BlockPos target = pointedMachine(level, player);
		if (target == null) target = underCrosshair(level, player);
		if (target == null) return null;

		if (level.getBlockState(target).getBlock() instanceof TurbineTowerBlock) {
			BlockPos turbine = TurbineTowerBlock.findTurbineAbove(level, target);
			if (turbine != null) return turbine;
		}

		return target;
	}

	/** Whether a block belongs to this mod, by its registry namespace: so a machine added later needs no list. */
	public static boolean isOurs(Block block) {
		return Renewables.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace());
	}

	/** The machine the ray from the eye passes through the model of, nearest first, with nothing solid in between. */
	@Nullable
	private static BlockPos pointedMachine(Level level, Player player) {
		Vec3 eye = player.getEyePosition(1.0f);
		Vec3 look = player.getViewVector(1.0f);
		BlockPos around = player.blockPosition();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		double reachSq = (double) REACH * REACH;

		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dy = -REACH; dy <= REACH; dy++) {
				for (int dz = -REACH; dz <= REACH; dz++) {
					cursor.set(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
					if (!level.hasChunkAt(cursor)) continue;

					BlockEntity entity = level.getBlockEntity(cursor);
					if (entity == null || !isOurs(entity.getBlockState().getBlock())) continue;
					if (Vec3.atCenterOf(cursor).distanceToSqr(eye) > reachSq) continue;

					BlockPos pos = cursor.immutable();
					Vec3 hit = PartPicker.nearestHit(level, pos, eye, look);
					if (hit == null) continue;

					double distance = hit.distanceToSqr(eye);
					if (distance >= bestDistance || !clear(level, player, eye, hit, pos)) continue;

					bestDistance = distance;
					best = pos;
				}
			}
		}

		return best;
	}

	/** Nothing between the eye and the point, other than the machine's own blocks (a shell cell is the machine). */
	private static boolean clear(Level level, Player player, Vec3 eye, Vec3 hit, BlockPos machine) {
		Vec3 along = hit.subtract(eye);
		double length = along.length();
		if (length < 1.0e-6) return true;

		Vec3 stop = eye.add(along.scale(Math.max(0.0, 1.0 - CLEARANCE / length)));
		BlockHitResult blocked = level.clip(new ClipContext(eye, stop, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (blocked.getType() == HitResult.Type.MISS) return true;

		return MachineShell.hostOr(level, blocked.getBlockPos()).equals(machine);
	}

	/** The block the crosshair is on, within the wrench's reach, as the machine it is part of - if it is one of ours. */
	@Nullable
	private static BlockPos underCrosshair(Level level, Player player) {
		HitResult hit = player.pick(PanelGuard.WRENCH_REACH, 1.0f, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return null;

		BlockPos pos = MachineShell.hostOr(level, block.getBlockPos());
		Block target = level.getBlockState(pos).getBlock();
		if (target instanceof TurbineTowerBlock) return pos;

		// a block of the mod that is not a machine (there is nothing to read off it) is not a target
		return isOurs(target) && level.getBlockEntity(pos) != null ? pos : null;
	}
}
