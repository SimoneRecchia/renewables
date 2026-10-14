package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.client.model.MeshModel;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The shared pass that draws every machine whose model is an OBJ (docs/spec/models.md section 6).
 *
 * STUB: owner M.  The signatures are what the clean renderers were written against and are the contract;
 * the bodies draw nothing yet.  A renderer subclasses this only to inherit these statics: it keeps its own
 * buffer cache and subscribes its own {@code RenderLevelStageEvent}.
 */
@OnlyIn(Dist.CLIENT)
public abstract class MachineRenderer {
	/** One group's vertex buffer on the GPU, kept per (block position, group name). */
	public static final class GroupBuffer {
	}

	/** What a renderer does with one machine, given the model and the block's pose already on the stack. */
	@FunctionalInterface
	public interface Draw<T extends BlockEntity> {
		void draw(T machine, RenderContext context, PoseStack poseStack, Matrix4f projection);
	}

	/** Draws every tracked machine of a type with just the block's pose.  Returns the positions it saw. */
	protected static <T extends BlockEntity> Set<BlockPos> drawAll(RenderLevelStageEvent event, Class<T> type, double maxDistanceSq,
			Function<BlockState, Direction> facing, Direction authored, Map<BlockPos, Map<String, GroupBuffer>> cache) {
		return new HashSet<>();
	}

	/** As above, but the renderer decides what to draw and how each group is posed. */
	protected static <T extends BlockEntity> Set<BlockPos> drawAll(RenderLevelStageEvent event, Class<T> type, double maxDistanceSq,
			Function<BlockState, Direction> facing, Direction authored, Map<BlockPos, Map<String, GroupBuffer>> cache, Draw<T> draw) {
		return new HashSet<>();
	}

	/** As above, for only those machines of the type that pass {@code filter}. */
	protected static <T extends BlockEntity> Set<BlockPos> drawAll(RenderLevelStageEvent event, Class<T> type, double maxDistanceSq,
			Function<BlockState, Direction> facing, Direction authored, Map<BlockPos, Map<String, GroupBuffer>> cache, Draw<T> draw,
			Predicate<T> filter) {
		return new HashSet<>();
	}

	/** Draws groups, each with the matrix in {@code poses}; a group with no matrix is not drawn. */
	protected static void renderGrouped(MeshModel model, Map<String, Matrix4f> poses, Matrix4f projection, ResourceLocation texture,
			int packedLight, BlockPos pos, Map<BlockPos, Map<String, GroupBuffer>> cache) {
	}

	/** Draws every group with the one pose on the stack, except those {@code visible} rejects. */
	protected static void renderGrouped(MeshModel model, PoseStack poseStack, Matrix4f projection, ResourceLocation texture,
			int packedLight, BlockPos pos, Map<BlockPos, Map<String, GroupBuffer>> cache, Predicate<String> visible) {
	}

	/** The centre of the groups named {@code pivot_<name>*}, or {@code fallback} when the model has none. */
	protected static Vec3 pivot(MeshModel model, String name, Vec3 fallback) {
		return fallback;
	}

	/** Forgets the interpolated angles of positions that were not drawn this pass. */
	protected static void cleanupAngles(Map<BlockPos, Float> angles, Set<BlockPos> seen) {
		angles.keySet().retainAll(seen);
	}

	/**
	 * The names {@code <prefix>_<side>} of the model's cable-entry groups that a DC cable is actually
	 * arriving at, for each of the four horizontal sides (the side is read in the model's frame).
	 */
	protected static Set<String> cableEntries(Level level, BlockPos pos, Direction authored, Direction facing, String prefix) {
		return Set.of();
	}

	/** Whether a group is shown: a {@code <prefix>_<direction>} group only when its entry is live, any other always. */
	protected static boolean entryVisible(String groupName, String prefix, Set<String> live) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (groupName.startsWith(prefix + "_" + direction.getName())) return live.contains(prefix + "_" + direction.getName());
		}

		return true;
	}

	/** Whether a DC cable block next to, above or below {@code pos} on that side reaches this position. */
	protected static boolean cableArrives(Level level, BlockPos pos, Direction side) {
		return false;
	}
}
