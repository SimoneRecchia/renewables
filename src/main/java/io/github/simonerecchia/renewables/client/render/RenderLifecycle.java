package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.client.model.ModelCache;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * What the machine pass needs from the client's lifecycle, wired in one place (docs/spec/models.md section 4.3).
 *
 * <ul>
 * <li>At client setup it binds the blocks of the renderers that this subsystem owns (pole, cabin, power box, lattice
 *     tower, turbine and its tower).  Binding is idempotent, so a renderer that is also initialised from
 *     {@code RenewablesClient} is not a problem.</li>
 * <li>When the resources reload it drops every loaded model and every vertex buffer, so a changed model or a new
 *     resource pack shows up at once and nothing keeps a GPU buffer of the old one.</li>
 * <li>When the player leaves a world, or a client level unloads, every vertex buffer is released.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RenderLifecycle {
	private RenderLifecycle() {
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			UtilityPoleRenderer.init();
			ElectricCabinRenderer.init();
			PowerBoxRenderer.init();
			LatticeTowerRenderer.init();
			WindTurbineRenderer.init();

			MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut loggedOut) -> MachineRenderer.releaseAll());
			MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload unload) -> {
				if (unload.getLevel().isClientSide()) MachineRenderer.releaseAll();
			});
		});
	}

	@SubscribeEvent
	public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
		// runs on the render thread, which is where a GPU buffer has to be closed
		event.registerReloadListener((ResourceManagerReloadListener) manager -> {
			MachineRenderer.releaseAll();
			ModelCache.reload();
			ModelBindings.reload();
		});
	}
}
