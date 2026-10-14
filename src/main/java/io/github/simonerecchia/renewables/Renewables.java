package io.github.simonerecchia.renewables;

import io.github.simonerecchia.renewables.api.power.IElectricPowerConsumer;
import io.github.simonerecchia.renewables.compat.computercraft.ComputerCraftBridge;
import io.github.simonerecchia.renewables.core.ModGameRules;
import io.github.simonerecchia.renewables.core.ServerConfig;
import io.github.simonerecchia.renewables.core.ServerEvents;
import io.github.simonerecchia.renewables.model.ModelDefinitions;
import io.github.simonerecchia.renewables.net.Net;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;
import io.github.simonerecchia.renewables.registry.ModBlocks;
import io.github.simonerecchia.renewables.registry.ModItems;
import io.github.simonerecchia.renewables.registry.ModTabs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * The mod's entry point: declares the registries, the configuration and the game rule, and wires the event
 * handlers.  There is no game logic here.
 *
 * Order matters in the constructor, and is the one in docs/spec/entry.md section 3: the block entity registry
 * joins the bus after its types are declared.  The game rule is not registered here: constructors run in parallel
 * on the mod-loading workers and GameRules keeps its rules in an unsynchronised static map, so it goes in common
 * setup's main-thread work - still before any world is read, which is when a saved rule needs to exist.
 */
@Mod(Renewables.MOD_ID)
public final class Renewables {
	public static final String MOD_ID = "renewables";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public Renewables() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

		ModBlocks.BLOCKS.register(modBus);
		ModItems.ITEMS.register(modBus);
		ModTabs.TABS.register(modBus);

		// stored with the world, not in config/: <world>/serverconfig/Renewables/server.toml
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, "Renewables/server.toml");

		ModBlockEntities.BLOCK_ENTITIES.register(modBus);

		LOGGER.info("Registered {} items, {} blocks, and {} block entity types",
				ModItems.ITEMS.getEntries().size(), ModBlocks.BLOCKS.getEntries().size(), ModBlockEntities.BLOCK_ENTITIES.getEntries().size());

		modBus.addListener(Renewables::onCommonSetup);
		modBus.addListener(Renewables::onRegisterCapabilities);

		MinecraftForge.EVENT_BUS.register(ServerEvents.class);
	}

	private static void onCommonSetup(FMLCommonSetupEvent event) {
		// queued onto the main thread: registries are fully frozen by then, and no other mod is writing GameRules
		event.enqueueWork(() -> {
			ModGameRules.register();
			Net.init();
			ModelDefinitions.bootstrap();
			ComputerCraftBridge.register();
		});
	}

	private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
		event.register(IElectricPowerConsumer.class);
	}
}
