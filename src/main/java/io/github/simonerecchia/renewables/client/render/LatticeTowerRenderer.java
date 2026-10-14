package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.LatticeTowerBlock;
import io.github.simonerecchia.renewables.block.LatticeTowerBlockEntity;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the lattice towers.  They are eleven blocks tall and have to be seen from the next tower along the line,
 * so they have the longest draw distance of any machine.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LatticeTowerRenderer extends MachineRenderer {
	private static final double MAX_RENDER_DISTANCE_SQ = 192 * 192;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, LatticeTowerBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(LatticeTowerBlock.FACING), LatticeTowerBlock.AUTHORED);
	}

	public static void init() {
		ModelBindings.registerAll(ModBlocks.LATTICE_TOWERS);
	}
}
