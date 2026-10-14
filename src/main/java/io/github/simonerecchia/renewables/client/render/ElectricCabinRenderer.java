package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.ElectricCabinBlock;
import io.github.simonerecchia.renewables.block.ElectricCabinBlockEntity;
import io.github.simonerecchia.renewables.block.MachineBlock;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws the electric cabins.  The door is part of the model and stays shut. */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ElectricCabinRenderer extends MachineRenderer {
	private static final double MAX_RENDER_DISTANCE_SQ = 64 * 64;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, ElectricCabinBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(MachineBlock.FACING), ElectricCabinBlock.AUTHORED, BUFFER_CACHE);
	}

	public static void init() {
		ModelBindings.register(ModBlocks.ELECTRIC_CABIN.get());
	}
}
