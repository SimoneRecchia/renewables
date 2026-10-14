package io.github.simonerecchia.renewables.client.model;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.PvArrayBlock;
import io.github.simonerecchia.renewables.model.ModelDefinition;
import io.github.simonerecchia.renewables.model.ModelDefinitions;
import io.github.simonerecchia.renewables.model.PartBox;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

/**
 * Tells the client which blocks are drawn from a model, and answers questions about the model of a block
 * (docs/spec/models.md section 5.3).
 *
 * A renderer registers its blocks here at client setup; a block with no binding is never drawn by the machine
 * pass.  Registering costs nothing: the model is loaded, and its boxes worked out, the first time something asks
 * for them, which is what keeps this correct when the resources are not yet loaded at setup, and what makes a
 * resource reload a matter of dropping the cache.
 */
@OnlyIn(Dist.CLIENT)
public final class ModelBindings {
	/** Which OBJ draws a block, and the texture to use for a group whose material has none. */
	private record Binding(ResourceLocation obj, ResourceLocation fallback) {
	}

	private static final Map<Block, Binding> BOUND = new ConcurrentHashMap<>();
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private ModelBindings() {
	}

	/** Binds a block to the model {@link ModelDefinitions} gives it. */
	public static void register(Block block) {
		ModelDefinition definition = ModelDefinitions.of(block);
		if (definition == null) {
			warnOnce("nodef:" + block, "Block " + block + " has no model definition, so it is not drawn from a model");
			return;
		}

		register(block, definition.obj(), null);
	}

	/**
	 * Binds a block to a model of its own choosing, for the blocks that are drawn from a model but are not in the
	 * definitions table: the turbine tower draws the turbine's.  A null fallback means the missing-texture picture.
	 */
	public static void register(Block block, ResourceLocation obj, @Nullable ResourceLocation fallbackTexture) {
		BOUND.put(block, new Binding(obj, fallbackTexture == null ? MissingTextureAtlasSprite.getLocation() : fallbackTexture));
	}

	/** A whole family at once. */
	public static void registerAll(Map<?, RegistryObject<Block>> blocks) {
		for (RegistryObject<Block> block : blocks.values()) {
			register(block.get());
		}
	}

	public static boolean isBound(Block block) {
		return BOUND.containsKey(block);
	}

	/** The loaded model that draws this block, or null if the block is unbound or its model cannot be loaded. */
	@Nullable
	public static MeshModel model(Block block) {
		Binding binding = BOUND.get(block);
		return binding == null ? null : ModelCache.get(binding.obj());
	}

	/** The texture for a group whose material gives none.  Never null. */
	public static ResourceLocation fallbackTexture(Block block) {
		Binding binding = BOUND.get(block);
		return binding == null ? MissingTextureAtlasSprite.getLocation() : binding.fallback();
	}

	/** The block's wire fittings, in the order a stored span indexes them; empty when it has none. */
	public static List<String> interactive(Block block) {
		ModelDefinition definition = ModelDefinitions.of(block);
		return definition == null ? List.of() : definition.insulators();
	}

	/** The box of one group of the block's model, in the model's frame, or null. */
	@Nullable
	public static PartBox box(Block block, String group) {
		MeshModel model = model(block);
		return model == null ? null : model.box(group);
	}

	/** Where a conductor rests on a vertical fitting: the middle of the top of its box. */
	@Nullable
	public static Vector3f top(Block block, String group) {
		PartBox box = box(block, group);
		return box == null ? null : box.top();
	}

	@Nullable
	public static Vector3f center(Block block, String group) {
		PartBox box = box(block, group);
		return box == null ? null : box.center();
	}

	/** The boxes of every group of the block's model; empty when it has none. */
	public static Map<String, PartBox> boxes(Block block) {
		MeshModel model = model(block);
		return model == null ? Map.of() : model.boxes();
	}

	/**
	 * The boxes of the block's fittings only, in fitting order.  A fitting the model does not contain is logged
	 * once and has no box, so nothing can hang a wire on it.
	 */
	public static Map<String, PartBox> fittingBoxes(Block block) {
		MeshModel model = model(block);
		List<String> names = interactive(block);
		if (model == null || names.isEmpty()) return Map.of();

		Map<String, PartBox> found = new LinkedHashMap<>();
		for (String name : names) {
			PartBox box = model.box(name);
			if (box != null) {
				found.put(name, box);
			} else {
				warnOnce("nofit:" + block + name, "Block " + block + " lists the fitting \"" + name + "\", which its model does not contain");
			}
		}

		return Collections.unmodifiableMap(found);
	}

	/**
	 * The facing a block's model is drawn at: the block's horizontal facing, the authored one for a block with none.
	 * An array that tracks the sun is always drawn at its authored facing, whatever its state says.
	 */
	public static Direction drawnFacing(BlockState state, Direction authored) {
		if (state.getBlock() instanceof PvArrayBlock array && array.spec().tracked()) return Direction.NORTH;

		return state.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(authored);
	}

	/** Forgets what was derived from the loaded models; the bindings themselves are code, not resources. */
	public static void reload() {
		WARNED.clear();
	}

	private static void warnOnce(String key, String message) {
		if (WARNED.add(key)) Renewables.LOGGER.warn(message);
	}
}
