package io.github.simonerecchia.renewables.client.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.RegistryObject;

/**
 * Tells the client which blocks are drawn from a model.  A renderer registers its blocks here at client setup;
 * a block with no binding is never drawn by the machine pass.
 *
 * STUB: owner M.  This only remembers the blocks; the real one also loads the model lazily, builds the
 * per-block group boxes and marks the fittings as interactive (docs/spec/models.md section 5.3).
 */
@OnlyIn(Dist.CLIENT)
public final class ModelBindings {
	private static final Set<Block> BOUND = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private ModelBindings() {
	}

	public static void register(Block block) {
		BOUND.add(block);
	}

	/** A whole family at once. */
	public static void registerAll(Map<?, RegistryObject<Block>> blocks) {
		for (RegistryObject<Block> block : blocks.values()) {
			register(block.get());
		}
	}

	public static boolean isBound(Block block) {
		return BOUND.contains(block);
	}
}
