package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.TurbineTowerBlock;
import io.github.simonerecchia.renewables.block.TurbineTowerBlockEntity;
import io.github.simonerecchia.renewables.client.model.GroupPoses;
import io.github.simonerecchia.renewables.client.model.MeshModel;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.model.ModelDefinition;
import io.github.simonerecchia.renewables.model.ModelDefinitions;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws a turbine tower that has no turbine on top of it: just the {@code pole*} groups of the turbine's own model,
 * stretched to the height of the column.  Once a turbine sits on the column the turbine's renderer draws the tower
 * with the turbine's scale and this one draws nothing, so the two never double up.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TurbineTowerRenderer extends MachineRenderer {
	private static final double MAX_RENDER_DISTANCE_SQ = 128 * 128;

	/** The width of a bare column: a quarter of the model's at two blocks, the model's own from thirteen on. */
	private static final float MIN_WIDTH = 0.25f;
	private static final float MAX_WIDTH = 1.0f;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, TurbineTowerBlockEntity.class, MAX_RENDER_DISTANCE_SQ, null, null,
				TurbineTowerRenderer::render, TurbineTowerRenderer::isBareFoot, TurbineTowerRenderer::bounds);
	}

	/** Only the foot of a column, and only when no turbine is seated on it. */
	private static boolean isBareFoot(TurbineTowerBlockEntity tower) {
		Level level = tower.getLevel();
		if (level == null) return false;

		BlockPos pos = tower.getBlockPos();
		return TurbineTowerBlock.countBelow(level, pos) == 0 && TurbineTowerBlock.findTurbineAbove(level, pos) == null;
	}

	private static void render(TurbineTowerBlockEntity tower, RenderContext context, PoseStack poseStack) {
		Level level = tower.getLevel();
		if (level == null) return;

		MeshModel model = context.model();
		float height = 1 + segmentsAbove(level, tower.getBlockPos());
		Matrix4f base = poseStack.last().pose();

		Map<String, Matrix4f> poses = new HashMap<>();
		for (String groupName : model.groups.keySet()) {
			Matrix4f pose = new Matrix4f(base);
			if (groupPose(groupName, model, height, pose)) poses.put(groupName, pose);
		}

		renderGrouped(context, poses);
	}

	/**
	 * Multiplies onto {@code pose} what one group of the turbine's model is drawn with on a bare column this many blocks
	 * tall; false for every group that is not the tube.  The picker calls it too.
	 */
	public static boolean groupPose(String groupName, MeshModel model, float height, Matrix4f pose) {
		if (!groupName.startsWith("pole")) return false;

		float width = width(height);
		if (groupName.startsWith("pole_base")) {
			pose.scale(width);
		} else {
			pose.scale(width, height / WindTurbineRenderer.authoredTowerHeight(model), width);
		}
		return true;
	}

	/** The column from its foot to its top, as wide as the widest a bare tower is drawn. */
	private static AABB bounds(TurbineTowerBlockEntity tower, MeshModel model) {
		BlockPos pos = tower.getBlockPos();
		int height = tower.getLevel() == null ? 1 : 1 + segmentsAbove(tower.getLevel(), pos);
		return new AABB(pos.getX() - 1.0, pos.getY() - 0.5, pos.getZ() - 1.0, pos.getX() + 2.0, pos.getY() + height + 0.5, pos.getZ() + 2.0);
	}

	/** Tower blocks directly above {@code pos}. */
	private static int segmentsAbove(Level level, BlockPos pos) {
		int count = 0;
		BlockPos.MutableBlockPos cursor = pos.mutable().move(0, 1, 0);
		while (count < TurbineTowerBlock.MAX_SEGMENTS && level.getBlockState(cursor).getBlock() instanceof TurbineTowerBlock) {
			count++;
			cursor.move(0, 1, 0);
		}

		return count;
	}

	/** How wide a bare column of this many blocks is drawn, as a multiple of the model's tube. */
	static float width(float height) {
		return Mth.clamp(MIN_WIDTH + (height - 2.0f) / 11.0f * (MAX_WIDTH - MIN_WIDTH), MIN_WIDTH, MAX_WIDTH);
	}

	/** Gives the tower the turbine's model: it has no definition of its own, because it has no wire fitting. */
	public static void init() {
		ModelDefinition turbine = ModelDefinitions.of(ModBlocks.WIND_TURBINE.get());
		if (turbine == null) {
			Renewables.LOGGER.warn("The turbine has no model definition, so the bare tower has no model to draw either");
			return;
		}

		ModelBindings.register(ModBlocks.TURBINE_TOWER.get(), turbine.obj(), null);
		// a tower block is pointed at as the tube it draws: only the foot of a bare column draws one, and that tube is
		// the stretched pole, not the whole turbine the model also holds.  A column under a turbine is the turbine's.
		GroupPoses.register(TurbineTowerBlockEntity.class, (tower, model) -> {
			if (!isBareFoot(tower) || tower.getLevel() == null) return groupName -> null;

			float height = 1 + segmentsAbove(tower.getLevel(), tower.getBlockPos());
			return groupName -> {
				Matrix4f pose = new Matrix4f();
				return groupPose(groupName, model, height, pose) ? pose : null;
			};
		});
	}
}
