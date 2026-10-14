package io.github.simonerecchia.renewables.model;

import io.github.simonerecchia.renewables.api.power.PvArraySpec;
import io.github.simonerecchia.renewables.api.power.TowerSpec;
import io.github.simonerecchia.renewables.block.ElectricCabinBlock;
import io.github.simonerecchia.renewables.block.LatticeTowerBlock;
import io.github.simonerecchia.renewables.block.MetStationBlock;
import io.github.simonerecchia.renewables.block.PlantControllerBlock;
import io.github.simonerecchia.renewables.block.PowerBoxBlock;
import io.github.simonerecchia.renewables.block.PvArrayBlock;
import io.github.simonerecchia.renewables.block.PvCombinerBlock;
import io.github.simonerecchia.renewables.block.PvInverterBlock;
import io.github.simonerecchia.renewables.block.SwitchgearBlock;
import io.github.simonerecchia.renewables.block.TransformerBlock;
import io.github.simonerecchia.renewables.block.UtilityPoleBlock;
import io.github.simonerecchia.renewables.block.WindTurbineBlock;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import io.github.simonerecchia.renewables.Renewables;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * Which model draws which block: the one table the renderers, the picking code and the wire anchors all
 * read, filled identically on client and server.
 *
 * The fitting lists are saved data - a span stores an index into one - so they may only grow at the end.
 * Blocks that are not here (turbine tower, shell, DC cables, ground conductors) are drawn some other way.
 */
public final class ModelDefinitions {
	private static final Map<Block, ModelDefinition> DEFINITIONS = new ConcurrentHashMap<>();
	private static volatile boolean ready;

	private ModelDefinitions() {
	}

	public static void register(Block block, ModelDefinition definition) {
		DEFINITIONS.put(block, definition);
	}

	/** Registers a whole family, building each block's definition from the block itself. */
	public static void registerAll(Map<?, ? extends Supplier<? extends Block>> blocks, Function<Block, ModelDefinition> build) {
		for (Supplier<? extends Block> entry : blocks.values()) {
			Block block = entry.get();
			register(block, build.apply(block));
		}
	}

	/** The definition for a block, or null if nothing draws it from a model.  Fills the table on first use. */
	@Nullable
	public static ModelDefinition of(Block block) {
		if (!ready) bootstrap();

		return DEFINITIONS.get(block);
	}

	/** Every block that has a definition, for tools that walk them all. */
	public static List<Block> blocks() {
		if (!ready) bootstrap();

		return new ArrayList<>(DEFINITIONS.keySet());
	}

	/** Fills the table.  Idempotent, and safe to call from either thread. */
	public static synchronized void bootstrap() {
		if (ready) return;

		register(ModBlocks.UTILITY_POLE.get(), new ModelDefinition(model("utility_pole/utility_pole"), UtilityPoleBlock.AUTHORED,
				numbered("insulator_", "_porcelain", 8)));
		register(ModBlocks.POWER_BOX.get(), new ModelDefinition(model("power_box/power_box"), PowerBoxBlock.AUTHORED,
				List.of("insulator_porcelain")));
		// the cabin's model is named cab, not after its folder
		register(ModBlocks.ELECTRIC_CABIN.get(), new ModelDefinition(model("electric_cab/cab"), ElectricCabinBlock.AUTHORED,
				List.of("insulator_input_porcelain", "insulator_output_porcelain")));

		// one model for the whole wind line, scaled by the renderer.  The object is called insulator and the
		// material insulator_porcelain, and a group is named object + "_" + material.
		registerAll(ModBlocks.TURBINES, block -> new ModelDefinition(model("wind_turbine/wind_turbine"), WindTurbineBlock.AUTHORED,
				List.of("insulator_insulator_porcelain")));

		registerAll(ModBlocks.PV_ARRAYS, block -> new ModelDefinition(model(arrayModel(((PvArrayBlock) block).spec())),
				PvArrayBlock.AUTHORED, List.of()));
		registerAll(ModBlocks.PV_INVERTERS, block -> new ModelDefinition(model("pv_inverter/pv_inverter"), PvInverterBlock.AUTHORED,
				List.of("insulator_porcelain")));
		registerAll(ModBlocks.PV_COMBINERS, block -> new ModelDefinition(model("pv_combiner/pv_combiner"), PvCombinerBlock.AUTHORED,
				List.of()));

		// low-voltage side first
		registerAll(ModBlocks.TRANSFORMERS, block -> {
			var spec = ((TransformerBlock) block).spec();
			return new ModelDefinition(model(spec.modelName() + "/" + spec.modelName()), TransformerBlock.AUTHORED,
					numbered("bushing_", "_porcelain", spec.bushings()));
		});

		// six phases: the lower arm from the outside in, then the upper
		registerAll(ModBlocks.LATTICE_TOWERS, block -> {
			TowerSpec spec = ((LatticeTowerBlock) block).spec();
			return new ModelDefinition(model(spec.modelName() + "/" + spec.modelName()), LatticeTowerBlock.AUTHORED,
					numbered("insulator_", "_porcelain", spec.circuits() * 3));
		});

		// line side first, then load: the index is also the bus of an open switch
		registerAll(ModBlocks.SWITCHGEARS, block -> {
			var spec = ((SwitchgearBlock) block).spec();
			return new ModelDefinition(model(spec.modelName() + "/" + spec.modelName()), SwitchgearBlock.AUTHORED,
					numbered("insulator_", "_porcelain", spec.fittings()));
		});

		register(ModBlocks.MET_STATION.get(), new ModelDefinition(model("met_mast/met_mast"), MetStationBlock.AUTHORED, List.of()));
		register(ModBlocks.PLANT_CONTROLLER.get(), new ModelDefinition(model("plant_controller/plant_controller"),
				PlantControllerBlock.AUTHORED, List.of()));

		ready = true;
	}

	private static String arrayModel(PvArraySpec spec) {
		return switch (spec.mounting()) {
			case FLAT -> "pv_flat/pv_flat";
			case FIXED_TILT -> "pv_tilt/pv_tilt";
			case SINGLE_AXIS -> "pv_track/pv_track";
			case DUAL_AXIS -> "pv_dual/pv_dual";
		};
	}

	/** {@code models/<path>.obj} in the mod's namespace. */
	private static ResourceLocation model(String path) {
		return new ResourceLocation(Renewables.MOD_ID, "models/" + path + ".obj");
	}

	/** {@code prefix1suffix ... prefixNsuffix}, counting from one. */
	private static List<String> numbered(String prefix, String suffix, int count) {
		List<String> names = new ArrayList<>(count);
		for (int i = 1; i <= count; i++) names.add(prefix + i + suffix);

		return names;
	}
}
