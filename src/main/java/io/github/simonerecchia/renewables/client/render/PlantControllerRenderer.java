package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.block.PlantControllerBlock;
import io.github.simonerecchia.renewables.block.PlantControllerBlockEntity;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import io.github.simonerecchia.renewables.Renewables;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws the plant control cabinet. Nothing on it moves: the door stays shut and the whip does not sway. */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PlantControllerRenderer extends MachineRenderer {
	private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, PlantControllerBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(PlantControllerBlock.FACING), PlantControllerBlock.AUTHORED);
	}

	public static void init() {
		ModelBindings.register(ModBlocks.PLANT_CONTROLLER.get());
	}
}
