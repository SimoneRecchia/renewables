package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.MachineBlock;
import io.github.simonerecchia.renewables.block.PowerBoxBlock;
import io.github.simonerecchia.renewables.block.PowerBoxBlockEntity;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the power boxes (kiosks).  On the ground the whole model is drawn; mounted on a wall the plinth and the
 * conduit are left out.  The set-back onto the wall is not this class's: the block entity is an {@code ExtraPose}
 * of {@link PowerBoxBlock#BACKSET}, which the pass applies before this draws, exactly as the collision, the picker
 * and the wire anchor apply it.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PowerBoxRenderer extends MachineRenderer {
	private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, PowerBoxBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(MachineBlock.FACING), PowerBoxBlock.AUTHORED, PowerBoxRenderer::render);
	}

	private static void render(PowerBoxBlockEntity box, RenderContext context, PoseStack poseStack) {
		boolean mounted = PowerBoxBlock.mounted(box.getBlockState());
		renderGrouped(context, poseStack, groupName -> !mounted || !(groupName.startsWith("plinth") || groupName.startsWith("conduit")));
	}

	public static void init() {
		ModelBindings.register(ModBlocks.POWER_BOX.get());
	}
}
