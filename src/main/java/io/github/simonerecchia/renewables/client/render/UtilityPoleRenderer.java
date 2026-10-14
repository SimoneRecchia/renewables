package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.MachineBlock;
import io.github.simonerecchia.renewables.block.UtilityPoleBlock;
import io.github.simonerecchia.renewables.block.UtilityPoleBlockEntity;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the utility poles.  Nothing on one moves, but a pole can be offset, yawed and tilted by the player: the
 * pass applies the block entity's own transform (offset, yaw, pitch) after the block's facing, which is the same
 * chain the picker and the wire anchors use.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class UtilityPoleRenderer extends MachineRenderer {
	/** A pole is six blocks tall and carries the line: drawn as far as the wires are by default, or a line ends in mid-air. */
	private static final double MAX_RENDER_DISTANCE_SQ = 128 * 128;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, UtilityPoleBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(MachineBlock.FACING), UtilityPoleBlock.AUTHORED);
	}

	public static void init() {
		ModelBindings.register(ModBlocks.UTILITY_POLE.get());
	}
}
