package io.github.simonerecchia.renewables;

import io.github.simonerecchia.renewables.client.ClientBlockEntities;
import io.github.simonerecchia.renewables.client.render.MetStationRenderer;
import io.github.simonerecchia.renewables.client.render.PlantControllerRenderer;
import io.github.simonerecchia.renewables.client.render.PvArrayRenderer;
import io.github.simonerecchia.renewables.client.render.PvCombinerRenderer;
import io.github.simonerecchia.renewables.client.render.PvInverterRenderer;
import io.github.simonerecchia.renewables.client.render.SwitchgearRenderer;
import io.github.simonerecchia.renewables.client.render.TransformerRenderer;
import io.github.simonerecchia.renewables.client.wire.ClientWires;
import io.github.simonerecchia.renewables.client.wire.InsulatorIndex;
import io.github.simonerecchia.renewables.core.ClientConfig;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.net.packet.PowerUpdatePayload;
import io.github.simonerecchia.renewables.net.packet.SpanChangedPayload;
import io.github.simonerecchia.renewables.net.packet.WireSyncPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The client entry point.  Nothing here is reached from the dedicated server: the class is only subscribed on
 * {@link Dist#CLIENT}, and the network code gets to its message handlers through a {@code DistExecutor}.
 *
 * owner C fills in the input, wire-drawing and screen hooks; what is here is what is obvious from the
 * client's own lifecycle.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RenewablesClient {
	static {
		// registered here and not in the common constructor, so a dedicated server never creates the file
		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "Renewables/config.toml");
	}

	private RenewablesClient() {
	}

	@net.minecraftforge.eventbus.api.SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			// a change of world must not leave wires or machines from the last one standing in the new one
			MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut loggedOut) -> forgetWorld());
			MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload unload) -> {
				if (unload.getLevel().isClientSide()) forgetWorld();
			});

			// each registers its blocks with the model bindings, which is how the machine pass knows what to draw
			// owner M adds the pole, cabin, power box, tower and turbine renderers here
			PvArrayRenderer.init();
			PvInverterRenderer.init();
			PvCombinerRenderer.init();
			MetStationRenderer.init();
			PlantControllerRenderer.init();
			SwitchgearRenderer.init();
			TransformerRenderer.init();
		});
	}

	/** Forgets every span, every tracked machine and every fitting lookup. */
	public static void forgetWorld() {
		ClientWires.clear();
		ClientBlockEntities.clear();
		InsulatorIndex.clear();
	}

	// ---- server to client messages, run on the client's main thread ----

	public static void onWireSync(WireSyncPayload message) {
		ClientWires.sync(message.spans(), message.replace());
	}

	public static void onSpanChanged(SpanChangedPayload message) {
		if (message.created()) {
			ClientWires.add(message.span());
		} else {
			ClientWires.remove(message.span());
		}
	}

	/** What the grid delivers to a machine, for the wrench and the diagnostics screen to read. */
	public static void onPowerUpdate(PowerUpdatePayload message) {
		Level level = Minecraft.getInstance().level;
		if (level == null) return;

		BlockEntity machine = level.getBlockEntity(message.blockPos());
		if (machine instanceof InsulatorHost host) host.deliverPower(message.powerKw());
	}
}
