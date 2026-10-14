package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.TurbineTowerBlock;
import io.github.simonerecchia.renewables.block.TurbineTowerBlockEntity;
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
	/** Its own cache: a tower and a turbine can be at the same position, one after the other, and must not share buffers. */
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();

	/** The width of a bare column: a quarter of the model's at two blocks, the model's own from thirteen on. */
	private static final float MIN_WIDTH = 0.25f;
	private static final float MAX_WIDTH = 1.0f;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, TurbineTowerBlockEntity.class, MAX_RENDER_DISTANCE_SQ, null, null, BUFFER_CACHE,
				TurbineTowerRenderer::render, TurbineTowerRenderer::isBareFoot);
	}

	/** Only the foot of a column, and only when no turbine is seated on it. */
	private static boolean isBareFoot(TurbineTowerBlockEntity tower) {
		Level level = tower.getLevel();
		if (level == null) return false;

		BlockPos pos = tower.getBlockPos();
		return TurbineTowerBlock.countBelow(level, pos) == 0 && TurbineTowerBlock.findTurbineAbove(level, pos) == null;
	}

	private static void render(TurbineTowerBlockEntity tower, RenderContext context, PoseStack poseStack, Matrix4f projection) {
		Level level = tower.getLevel();
		if (level == null) return;

		MeshModel model = context.model();
		float height = 1 + segmentsAbove(level, tower.getBlockPos());
		float width = width(height);

		Map<String, Matrix4f> poses = new HashMap<>();
		for (String groupName : model.groups.keySet()) {
			if (!groupName.startsWith("pole")) continue;

			poseStack.pushPose();
			if (groupName.startsWith("pole_base")) {
				poseStack.scale(width, width, width);
			} else {
				poseStack.scale(width, height / WindTurbineRenderer.authoredTowerHeight(model), width);
			}
			poses.put(groupName, new Matrix4f(poseStack.last().pose()));
			poseStack.popPose();
		}

		renderGrouped(model, poses, projection, context.texture(), context.packedLight(), tower.getBlockPos(), BUFFER_CACHE);
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
	}
}
