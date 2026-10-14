package io.github.simonerecchia.renewables.client.events;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.client.wire.WireRenderer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Hooks the wire pass into the level renderer: once a frame, after the solid blocks. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WireRenderEvents {
	private WireRenderEvents() {
	}

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) WireRenderer.render(event);
	}
}
