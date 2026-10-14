package io.github.simonerecchia.renewables.registry;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.CombinerSpec;
import io.github.simonerecchia.renewables.api.power.DcCableSpec;
import io.github.simonerecchia.renewables.api.power.InverterSpec;
import io.github.simonerecchia.renewables.api.power.PvArraySpec;
import io.github.simonerecchia.renewables.api.power.SwitchgearSpec;
import io.github.simonerecchia.renewables.api.power.TowerSpec;
import io.github.simonerecchia.renewables.api.power.TransformerSpec;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.block.DcCableBlock;
import io.github.simonerecchia.renewables.block.PvCombinerBlock;
import io.github.simonerecchia.renewables.catalog.CableCatalog;
import io.github.simonerecchia.renewables.catalog.CombinerCatalog;
import io.github.simonerecchia.renewables.catalog.ConductorCatalog;
import io.github.simonerecchia.renewables.catalog.InverterCatalog;
import io.github.simonerecchia.renewables.catalog.PartCatalog;
import io.github.simonerecchia.renewables.catalog.PvCatalog;
import io.github.simonerecchia.renewables.catalog.SwitchgearCatalog;
import io.github.simonerecchia.renewables.catalog.TowerCatalog;
import io.github.simonerecchia.renewables.catalog.TransformerCatalog;
import io.github.simonerecchia.renewables.catalog.TurbineCatalog;
import io.github.simonerecchia.renewables.item.ConductorItem;
import io.github.simonerecchia.renewables.item.DcCableItem;
import io.github.simonerecchia.renewables.item.PowerWrenchItem;
import io.github.simonerecchia.renewables.item.PvArrayBlockItem;
import io.github.simonerecchia.renewables.item.PvCombinerBlockItem;
import io.github.simonerecchia.renewables.item.PvInverterBlockItem;
import io.github.simonerecchia.renewables.item.TooltipBlockItem;
import io.github.simonerecchia.renewables.item.TooltipItem;
import io.github.simonerecchia.renewables.item.TurbineBlockItem;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Every item the mod registers (77), with the per-family maps the rest of the code looks them up in.
 *
 * Maps are keyed by catalogue spec id and keep catalogue order, like {@link ModBlocks}'s.  The shell and the
 * ground-laid conductors have no item: a run is laid with its reel.
 */
public final class ModItems {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Renewables.MOD_ID);

	/** Reels of overhead conductor, one per conductor spec. */
	public static final Map<ResourceLocation, RegistryObject<Item>> CONDUCTORS = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Item>> PV_ARRAYS = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Item>> PV_INVERTERS = new LinkedHashMap<>();
	/** DC cable, which is the block's own item: placing the reel places the cable. */
	public static final Map<ResourceLocation, RegistryObject<Item>> DC_CABLES = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Item>> PV_COMBINERS = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Item>> LATTICE_TOWERS = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Item>> TRANSFORMERS = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Item>> SWITCHGEARS = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Item>> TURBINES = new LinkedHashMap<>();
	/** The 33 parts, by their string id, in catalogue order. */
	public static final Map<String, RegistryObject<Item>> PARTS = new LinkedHashMap<>();

	public static final RegistryObject<Item> POWER_WRENCH;
	public static final RegistryObject<Item> UTILITY_POLE;
	public static final RegistryObject<Item> ELECTRIC_CABIN;
	public static final RegistryObject<Item> POWER_BOX;
	public static final RegistryObject<Item> WIND_TURBINE;
	public static final RegistryObject<Item> CIRCUIT_BOARD;
	public static final RegistryObject<Item> CPU;
	public static final RegistryObject<Item> SCREEN;
	public static final RegistryObject<Item> INSULATOR;
	public static final RegistryObject<Item> METAL_CASING;
	public static final RegistryObject<Item> MOTOR_CORE;
	public static final RegistryObject<Item> TURBINE_TOWER;
	public static final RegistryObject<Item> MET_STATION;
	public static final RegistryObject<Item> PLANT_CONTROLLER;

	static {
		for (ConductorSpec spec : ConductorCatalog.all()) {
			CONDUCTORS.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new ConductorItem(new Item.Properties().stacksTo(64), spec)));
		}

		POWER_WRENCH = ITEMS.register("power_wrench",
				() -> new PowerWrenchItem(new Item.Properties().stacksTo(1)));

		UTILITY_POLE = block("utility_pole", ModBlocks.UTILITY_POLE);
		ELECTRIC_CABIN = block("electric_cabin", ModBlocks.ELECTRIC_CABIN);
		POWER_BOX = block("power_box", ModBlocks.POWER_BOX);
		WIND_TURBINE = ITEMS.register("wind_turbine",
				() -> new TurbineBlockItem(ModBlocks.WIND_TURBINE.get(), new Item.Properties(), TurbineCatalog.C130_40));

		CIRCUIT_BOARD = plain("circuit_board");
		CPU = plain("cpu");
		SCREEN = plain("screen");
		INSULATOR = plain("insulator");
		METAL_CASING = plain("metal_casing");
		MOTOR_CORE = plain("motor_core");

		for (PartCatalog.Part part : PartCatalog.all()) {
			PARTS.put(part.id(), plain(part.id()));
		}

		TURBINE_TOWER = block("turbine_tower", ModBlocks.TURBINE_TOWER);

		for (PvArraySpec spec : PvCatalog.all()) {
			PV_ARRAYS.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new PvArrayBlockItem(ModBlocks.PV_ARRAYS.get(spec.id()).get(), new Item.Properties(), spec)));
		}

		for (InverterSpec spec : InverterCatalog.all()) {
			PV_INVERTERS.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new PvInverterBlockItem(ModBlocks.PV_INVERTERS.get(spec.id()).get(), new Item.Properties(), spec)));
		}

		for (DcCableSpec spec : CableCatalog.all()) {
			DC_CABLES.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new DcCableItem((DcCableBlock) ModBlocks.DC_CABLES.get(spec.id()).get(), new Item.Properties())));
		}

		for (CombinerSpec spec : CombinerCatalog.all()) {
			PV_COMBINERS.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new PvCombinerBlockItem((PvCombinerBlock) ModBlocks.PV_COMBINERS.get(spec.id()).get(), new Item.Properties())));
		}

		for (TowerSpec spec : TowerCatalog.all()) {
			LATTICE_TOWERS.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new TooltipBlockItem(ModBlocks.LATTICE_TOWERS.get(spec.id()).get(), new Item.Properties(),
							"tooltip.renewables." + spec.id().getPath())));
		}

		for (TransformerSpec spec : TransformerCatalog.all()) {
			TRANSFORMERS.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new TooltipBlockItem(ModBlocks.TRANSFORMERS.get(spec.id()).get(), new Item.Properties(),
							"tooltip.renewables." + spec.id().getPath())));
		}

		for (SwitchgearSpec spec : SwitchgearCatalog.all()) {
			SWITCHGEARS.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new TooltipBlockItem(ModBlocks.SWITCHGEARS.get(spec.id()).get(), new Item.Properties(),
							"tooltip.renewables." + spec.id().getPath())));
		}

		MET_STATION = block("met_station", ModBlocks.MET_STATION);
		PLANT_CONTROLLER = block("plant_controller", ModBlocks.PLANT_CONTROLLER);

		for (TurbineSpec spec : TurbineCatalog.all()) {
			if (spec == TurbineCatalog.C130_40) {
				TURBINES.put(spec.id(), WIND_TURBINE);
				continue;
			}

			TURBINES.put(spec.id(), ITEMS.register(spec.id().getPath(),
					() -> new TurbineBlockItem(ModBlocks.TURBINES.get(spec.id()).get(), new Item.Properties(), spec)));
		}
	}

	private ModItems() {
	}

	/** A block's item with its one grey line of tooltip: {@code tooltip.renewables.<id>}. */
	private static RegistryObject<Item> block(String id, RegistryObject<net.minecraft.world.level.block.Block> block) {
		return ITEMS.register(id, () -> new TooltipBlockItem(block.get(), new Item.Properties(), "tooltip.renewables." + id));
	}

	/** An ingredient: not placeable, not usable, with one grey line of tooltip. */
	private static RegistryObject<Item> plain(String id) {
		return ITEMS.register(id, () -> new TooltipItem(new Item.Properties(), "tooltip.renewables." + id));
	}
}
