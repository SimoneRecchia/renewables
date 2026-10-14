package io.github.simonerecchia.renewables.registry;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.ElectricCabinBlockEntity;
import io.github.simonerecchia.renewables.block.GroundConductorBlockEntity;
import io.github.simonerecchia.renewables.block.LatticeTowerBlockEntity;
import io.github.simonerecchia.renewables.block.MetStationBlockEntity;
import io.github.simonerecchia.renewables.block.PlantControllerBlockEntity;
import io.github.simonerecchia.renewables.block.PowerBoxBlockEntity;
import io.github.simonerecchia.renewables.block.PvArrayBlockEntity;
import io.github.simonerecchia.renewables.block.PvCombinerBlockEntity;
import io.github.simonerecchia.renewables.block.PvInverterBlockEntity;
import io.github.simonerecchia.renewables.block.SwitchgearBlockEntity;
import io.github.simonerecchia.renewables.block.TransformerBlockEntity;
import io.github.simonerecchia.renewables.block.TurbineTowerBlockEntity;
import io.github.simonerecchia.renewables.block.UtilityPoleBlockEntity;
import io.github.simonerecchia.renewables.block.WindTurbineBlockEntity;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The 14 block entity types.
 *
 * A family shares one type, and the machine's spec is read back off the block it stands in, so a type is
 * valid for every block of its family.  {@code machine_shell} and the DC cables have none.
 */
public final class ModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
			DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Renewables.MOD_ID);

	public static final RegistryObject<BlockEntityType<UtilityPoleBlockEntity>> UTILITY_POLE = BLOCK_ENTITIES.register("utility_pole",
			() -> BlockEntityType.Builder.of(UtilityPoleBlockEntity::new, ModBlocks.UTILITY_POLE.get()).build(null));
	public static final RegistryObject<BlockEntityType<ElectricCabinBlockEntity>> ELECTRIC_CABIN = BLOCK_ENTITIES.register("electric_cabin",
			() -> BlockEntityType.Builder.of(ElectricCabinBlockEntity::new, ModBlocks.ELECTRIC_CABIN.get()).build(null));
	public static final RegistryObject<BlockEntityType<PowerBoxBlockEntity>> POWER_BOX = BLOCK_ENTITIES.register("power_box",
			() -> BlockEntityType.Builder.of(PowerBoxBlockEntity::new, ModBlocks.POWER_BOX.get()).build(null));
	public static final RegistryObject<BlockEntityType<LatticeTowerBlockEntity>> LATTICE_TOWER = BLOCK_ENTITIES.register("lattice_tower",
			() -> BlockEntityType.Builder.of(LatticeTowerBlockEntity::new, blocks(ModBlocks.LATTICE_TOWERS)).build(null));
	public static final RegistryObject<BlockEntityType<TransformerBlockEntity>> TRANSFORMER = BLOCK_ENTITIES.register("transformer",
			() -> BlockEntityType.Builder.of(TransformerBlockEntity::new, blocks(ModBlocks.TRANSFORMERS)).build(null));
	public static final RegistryObject<BlockEntityType<GroundConductorBlockEntity>> GROUND_CONDUCTOR = BLOCK_ENTITIES.register("ground_conductor",
			() -> BlockEntityType.Builder.of(GroundConductorBlockEntity::new, blocks(ModBlocks.GROUND_CONDUCTORS)).build(null));
	public static final RegistryObject<BlockEntityType<WindTurbineBlockEntity>> WIND_TURBINE = BLOCK_ENTITIES.register("wind_turbine",
			() -> BlockEntityType.Builder.of(WindTurbineBlockEntity::new, blocks(ModBlocks.TURBINES)).build(null));
	/** Stateless and never ticked: it exists so the renderer finds a tower that has no machine on it yet. */
	public static final RegistryObject<BlockEntityType<TurbineTowerBlockEntity>> TURBINE_TOWER = BLOCK_ENTITIES.register("turbine_tower",
			() -> BlockEntityType.Builder.of(TurbineTowerBlockEntity::new, ModBlocks.TURBINE_TOWER.get()).build(null));
	public static final RegistryObject<BlockEntityType<PvArrayBlockEntity>> PV_ARRAY = BLOCK_ENTITIES.register("pv_array",
			() -> BlockEntityType.Builder.of(PvArrayBlockEntity::new, blocks(ModBlocks.PV_ARRAYS)).build(null));
	public static final RegistryObject<BlockEntityType<PvInverterBlockEntity>> PV_INVERTER = BLOCK_ENTITIES.register("pv_inverter",
			() -> BlockEntityType.Builder.of(PvInverterBlockEntity::new, blocks(ModBlocks.PV_INVERTERS)).build(null));
	public static final RegistryObject<BlockEntityType<PvCombinerBlockEntity>> PV_COMBINER = BLOCK_ENTITIES.register("pv_combiner",
			() -> BlockEntityType.Builder.of(PvCombinerBlockEntity::new, blocks(ModBlocks.PV_COMBINERS)).build(null));
	public static final RegistryObject<BlockEntityType<MetStationBlockEntity>> MET_STATION = BLOCK_ENTITIES.register("met_station",
			() -> BlockEntityType.Builder.of(MetStationBlockEntity::new, ModBlocks.MET_STATION.get()).build(null));
	public static final RegistryObject<BlockEntityType<PlantControllerBlockEntity>> PLANT_CONTROLLER = BLOCK_ENTITIES.register("plant_controller",
			() -> BlockEntityType.Builder.of(PlantControllerBlockEntity::new, ModBlocks.PLANT_CONTROLLER.get()).build(null));
	public static final RegistryObject<BlockEntityType<SwitchgearBlockEntity>> SWITCHGEAR = BLOCK_ENTITIES.register("switchgear",
			() -> BlockEntityType.Builder.of(SwitchgearBlockEntity::new, blocks(ModBlocks.SWITCHGEARS)).build(null));

	private ModBlockEntities() {
	}

	private static Block[] blocks(Map<ResourceLocation, RegistryObject<Block>> family) {
		return family.values().stream().map(RegistryObject::get).toArray(Block[]::new);
	}
}
