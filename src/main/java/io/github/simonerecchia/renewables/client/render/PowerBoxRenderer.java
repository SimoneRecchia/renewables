package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.MachineBlock;
import io.github.simonerecchia.renewables.block.PowerBoxBlock;
import io.github.simonerecchia.renewables.block.PowerBoxBlockEntity;
import io.github.simonerecchia.renewables.client.model.MeshModel;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the power boxes (kiosks).  On the ground the whole model is drawn; mounted on a wall the plinth and the
 * conduit are left out and the rest is moved back so that the back of the cabinet sits on the wall.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PowerBoxRenderer extends MachineRenderer {
	private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();

	/**
	 * How far back a wall-mounted cabinet moves, in blocks along the model's own z, which points at the wall.  The
	 * collision of the mounted variant is moved by the same amount (grid-blocks.md section 5.1).
	 */
	private static final float WALL_SETBACK = 0.28f;
	/** The block state property that says the box is on a wall. */
	private static final String MOUNTED = "mounted";

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, PowerBoxBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(MachineBlock.FACING), PowerBoxBlock.AUTHORED, BUFFER_CACHE, PowerBoxRenderer::render);
	}

	private static void render(PowerBoxBlockEntity box, RenderContext context, PoseStack poseStack, Matrix4f projection) {
		MeshModel model = context.model();
		if (!mounted(box.getBlockState())) {
			renderGrouped(model, poseStack, projection, context.texture(), context.packedLight(), box.getBlockPos(), BUFFER_CACHE, name -> true);
			return;
		}

		Map<String, Matrix4f> poses = new HashMap<>();
		poseStack.pushPose();
		poseStack.translate(0.0, 0.0, WALL_SETBACK);
		Matrix4f moved = new Matrix4f(poseStack.last().pose());
		poseStack.popPose();

		for (String groupName : model.groups.keySet()) {
			if (groupName.startsWith("pivot_") || groupName.startsWith("plinth") || groupName.startsWith("conduit")) continue;

			poses.put(groupName, moved);
		}

		renderGrouped(model, poses, projection, context.texture(), context.packedLight(), box.getBlockPos(), BUFFER_CACHE);
	}

	/** Read by name, so that this does not depend on what the block calls its property constant. */
	private static boolean mounted(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals(MOUNTED) && state.getValue(property) instanceof Boolean value) return value;
		}

		return false;
	}

	public static void init() {
		ModelBindings.register(ModBlocks.POWER_BOX.get());
	}
}
