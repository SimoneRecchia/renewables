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
import io.github.simonerecchia.renewables.block.ElectricCabinBlock;
import io.github.simonerecchia.renewables.block.GroundConductorBlock;
import io.github.simonerecchia.renewables.block.LatticeTowerBlock;
import io.github.simonerecchia.renewables.block.MachineShellBlock;
import io.github.simonerecchia.renewables.block.MetStationBlock;
import io.github.simonerecchia.renewables.block.PlantControllerBlock;
import io.github.simonerecchia.renewables.block.PowerBoxBlock;
import io.github.simonerecchia.renewables.block.PvArrayBlock;
import io.github.simonerecchia.renewables.block.PvCombinerBlock;
import io.github.simonerecchia.renewables.block.PvInverterBlock;
import io.github.simonerecchia.renewables.block.SwitchgearBlock;
import io.github.simonerecchia.renewables.block.TransformerBlock;
import io.github.simonerecchia.renewables.block.TurbineTowerBlock;
import io.github.simonerecchia.renewables.block.UtilityPoleBlock;
import io.github.simonerecchia.renewables.block.WindTurbineBlock;
import io.github.simonerecchia.renewables.catalog.CableCatalog;
import io.github.simonerecchia.renewables.catalog.CombinerCatalog;
import io.github.simonerecchia.renewables.catalog.ConductorCatalog;
import io.github.simonerecchia.renewables.catalog.InverterCatalog;
import io.github.simonerecchia.renewables.catalog.PvCatalog;
import io.github.simonerecchia.renewables.catalog.SwitchgearCatalog;
import io.github.simonerecchia.renewables.catalog.TowerCatalog;
import io.github.simonerecchia.renewables.catalog.TransformerCatalog;
import io.github.simonerecchia.renewables.catalog.TurbineCatalog;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Every block the mod registers (38), and the per-family maps the rest of the code looks them up in.
 *
 * The family maps are keyed by the <em>spec id</em> of the catalogue entry ({@code renewables:c130_40}), not
 * by registry name, and keep the catalogue's order: a creative tab and a sheet of renders show them that way.
 * Registry names are the specs' paths except for the C130, which has always been called {@code wind_turbine}.
 *
 * Registration order has no functional meaning (saves hold names), but the singles come first, as
 * docs/spec/entry.md section 3 lists them.
 */
public final class ModBlocks {
	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Renewables.MOD_ID);

	// ---- singles ----
	public static final RegistryObject<Block> UTILITY_POLE = BLOCKS.register("utility_pole",
			() -> new UtilityPoleBlock(machine(2.0f, 10.0f)));
	public static final RegistryObject<Block> ELECTRIC_CABIN = BLOCKS.register("electric_cabin",
			() -> new ElectricCabinBlock(machine(2.0f, 10.0f)));
	public static final RegistryObject<Block> POWER_BOX = BLOCKS.register("power_box",
			() -> new PowerBoxBlock(machine(2.0f, 10.0f)));
	/** The C130-4.0, which kept the name of the machine the mod started with. */
	public static final RegistryObject<Block> WIND_TURBINE = BLOCKS.register("wind_turbine",
			() -> new WindTurbineBlock(machine(2.0f, 10.0f), TurbineCatalog.C130_40));
	/** The invisible cells a machine bigger than a block fills.  Never an item, drops nothing. */
	public static final RegistryObject<Block> MACHINE_SHELL = BLOCKS.register("machine_shell",
			() -> new MachineShellBlock(machine(2.0f, 10.0f).pushReaction(PushReaction.BLOCK).dynamicShape()));
	public static final RegistryObject<Block> TURBINE_TOWER = BLOCKS.register("turbine_tower",
			() -> new TurbineTowerBlock(machine(2.0f, 10.0f)));

	// ---- families, in catalogue order ----
	public static final Map<ResourceLocation, RegistryObject<Block>> PV_ARRAYS = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Block>> PV_INVERTERS = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Block>> DC_CABLES = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Block>> PV_COMBINERS = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Block>> LATTICE_TOWERS = new LinkedHashMap<>();
	/** Ground-laid conductor, one per conductor spec; the reel looks its block up here by the spec's id. */
	public static final Map<ResourceLocation, RegistryObject<Block>> GROUND_CONDUCTORS = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Block>> TRANSFORMERS = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Block>> SWITCHGEARS = new LinkedHashMap<>();
	public static final Map<ResourceLocation, RegistryObject<Block>> TURBINES = new LinkedHashMap<>();

	public static final RegistryObject<Block> MET_STATION;
	public static final RegistryObject<Block> PLANT_CONTROLLER;

	static {
		for (PvArraySpec spec : PvCatalog.all()) {
			PV_ARRAYS.put(spec.id(), BLOCKS.register(spec.id().getPath(),
					() -> new PvArrayBlock(glass(1.0f, 2.0f), spec)));
		}

		for (InverterSpec spec : InverterCatalog.all()) {
			PV_INVERTERS.put(spec.id(), BLOCKS.register(spec.id().getPath(),
					() -> new PvInverterBlock(machine(2.5f, 10.0f).sound(SoundType.METAL), spec)));
		}

		// a cable is soft: hardness and resistance are one number, and no tool is needed
		for (DcCableSpec spec : CableCatalog.all()) {
			DC_CABLES.put(spec.id(), BLOCKS.register(spec.id().getPath(),
					() -> new DcCableBlock(BlockBehaviour.Properties.of().strength(0.2f), spec)));
		}

		for (CombinerSpec spec : CombinerCatalog.all()) {
			PV_COMBINERS.put(spec.id(), BLOCKS.register(spec.id().getPath(),
					() -> new PvCombinerBlock(machine(1.5f, 6.0f).sound(SoundType.METAL), spec)));
		}

		for (TowerSpec spec : TowerCatalog.all()) {
			LATTICE_TOWERS.put(spec.id(), BLOCKS.register(spec.id().getPath(),
					() -> new LatticeTowerBlock(machine(3.0f, 12.0f).sound(SoundType.METAL), spec)));
		}

		// the id without a suffix belongs to the reel
		for (ConductorSpec spec : ConductorCatalog.all()) {
			GROUND_CONDUCTORS.put(spec.id(), BLOCKS.register(spec.id().getPath() + "_run",
					() -> new GroundConductorBlock(BlockBehaviour.Properties.of(), spec)));
		}

		for (TransformerSpec spec : TransformerCatalog.all()) {
			TRANSFORMERS.put(spec.id(), BLOCKS.register(spec.id().getPath(),
					() -> new TransformerBlock(machine(3.0f, 12.0f).sound(SoundType.METAL), spec)));
		}

		for (SwitchgearSpec spec : SwitchgearCatalog.all()) {
			SWITCHGEARS.put(spec.id(), BLOCKS.register(spec.id().getPath(),
					() -> new SwitchgearBlock(machine(2.0f, 8.0f).sound(SoundType.METAL), spec)));
		}

		MET_STATION = BLOCKS.register("met_station", () -> new MetStationBlock(light(1.5f, 3.0f)));
		PLANT_CONTROLLER = BLOCKS.register("plant_controller", () -> new PlantControllerBlock(light(1.5f, 3.0f)));

		// the wind line last: the C130 is the single registered above, the other five are new
		for (TurbineSpec spec : TurbineCatalog.all()) {
			if (spec == TurbineCatalog.C130_40) {
				TURBINES.put(spec.id(), WIND_TURBINE);
				continue;
			}

			TURBINES.put(spec.id(), BLOCKS.register(spec.id().getPath(),
					() -> new WindTurbineBlock(machine(2.0f, 10.0f), spec)));
		}
	}

	private ModBlocks() {
	}

	/**
	 * The shared shape of a machine: hard to break without the right tool, and not a full cube - but solid to a
	 * fluid.  Vanilla calls a block solid only when its collision box is nearly a cube (and never when the shape is
	 * dynamic), and a block that is not solid is one water flows into and a bucket replaces: kiosks, switches,
	 * towers and every cell of a big machine were washed away like torches, and lava took them without a drop.
	 */
	private static BlockBehaviour.Properties machine(float hardness, float resistance) {
		return BlockBehaviour.Properties.of().strength(hardness, resistance).requiresCorrectToolForDrops().noOcclusion()
				.forceSolidOn();
	}

	private static BlockBehaviour.Properties glass(float hardness, float resistance) {
		return machine(hardness, resistance).sound(SoundType.GLASS);
	}

	/** A light machine: a stone pickaxe and up takes it apart (needs_stone_tool, written by tools/gen_crafting.py). */
	private static BlockBehaviour.Properties light(float hardness, float resistance) {
		return machine(hardness, resistance).sound(SoundType.METAL);
	}
}
