package io.github.simonerecchia.renewables.client.model;

import io.github.simonerecchia.renewables.block.WindTurbineBlockEntity;
import io.github.simonerecchia.renewables.model.ExtraPose;
import io.github.simonerecchia.renewables.model.ModelDefinition;
import io.github.simonerecchia.renewables.model.ModelDefinitions;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Which part of a machine's model is under a ray: the crosshair, the wire preview, the wrench.
 *
 * Works on the per-group boxes, not on the triangles (docs/spec/models.md section 11): the answer is always a group
 * or the middle of one, never a face.  The ray is carried into the model's own frame by the same chain the renderer
 * draws with - the block's facing, and the block entity's {@link ExtraPose} when it has one - so a fitting is hit
 * exactly where it is drawn, offset and tilt included.  Where boxes overlap along the ray the nearest wins, and a
 * tie goes to the lower fitting index.  Client only.
 */
@OnlyIn(Dist.CLIENT)
public final class PartPicker {
	private PartPicker() {
	}

	/** Whether the ray passes through the box of the named group of the machine at {@code pos}. */
	public static boolean isPointed(Level level, BlockPos pos, String part, Vec3 origin, Vec3 direction) {
		PartSearch search = search(level, pos, level.getBlockState(pos));
		return search != null && search.isPointed(part, origin, direction);
	}

	/** The wire fitting the ray hits first (nearest along the ray), or null. */
	@Nullable
	public static String pointedFitting(Level level, BlockPos pos, Vec3 origin, Vec3 direction) {
		// most blocks have no fitting at all, and this is asked of every block around the player
		BlockState state = level.getBlockState(pos);
		ModelDefinition definition = ModelDefinitions.of(state.getBlock());
		if (definition == null || definition.insulators().isEmpty()) return null;

		PartSearch search = search(level, pos, state);
		return search == null ? null : search.fitting(origin, direction).name();
	}

	/** Like {@link #pointedFitting} but over every group of the model; a convenience, never used to pick a fitting. */
	@Nullable
	public static String pointedAnyPart(Level level, BlockPos pos, Vec3 origin, Vec3 direction) {
		PartSearch search = search(level, pos, level.getBlockState(pos));
		return search == null ? null : search.any(origin, direction).name();
	}

	/** The world position of the middle of a group's box, or null when the group or the model is unknown. */
	@Nullable
	public static Vec3 partCenter(Level level, BlockPos pos, String part) {
		PartSearch search = search(level, pos, level.getBlockState(pos));
		return search == null ? null : search.center(part);
	}

	/** Where the ray enters the nearest group box of the machine, or null when it hits none. */
	@Nullable
	public static Vec3 nearestHit(Level level, BlockPos pos, Vec3 origin, Vec3 direction) {
		PartSearch search = search(level, pos, level.getBlockState(pos));
		return search == null ? null : search.entryPoint(origin, direction);
	}

	// ---- the machine at a position ----

	@Nullable
	private static PartSearch search(Level level, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		MeshModel model = ModelBindings.model(block);
		if (model == null) return null;

		ModelDefinition definition = ModelDefinitions.of(block);
		Direction authored = definition == null ? Direction.NORTH : definition.authored();
		Direction facing = ModelBindings.drawnFacing(state, authored);

		PartFrame frame = PartFrame.of(pos, authored, facing, poseOf(level.getBlockEntity(pos)));
		return new PartSearch(model, frame, definition == null ? List.of() : definition.insulators());
	}

	/** The extra transform the renderer draws this machine with. */
	@Nullable
	private static ExtraPose poseOf(@Nullable BlockEntity machine) {
		if (machine instanceof ExtraPose pose) return pose;

		// the model is drawn from the foot of the tower and the machine is the block on top of it
		if (machine instanceof WindTurbineBlockEntity turbine) return new Lowered(-turbine.towerHeight());

		return null;
	}

	/** A model drawn some blocks below its block, with no turn. */
	private record Lowered(double dy) implements ExtraPose {
		@Override
		public Vec3 poseOffset() {
			return new Vec3(0.0, dy, 0.0);
		}

		@Override
		public float poseYawDegrees() {
			return 0.0f;
		}

		@Override
		public float posePitchDegrees() {
			return 0.0f;
		}
	}
}
