package io.github.simonerecchia.renewables.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Every machine block entity in the client's level, which is what the renderers walk each frame.
 *
 * Machines add themselves when they load and remove themselves when they go (through the common
 * {@code ClientTracking}, which never mentions this class on a server).  Emptied when the player leaves a
 * world or the client level unloads, so a change of world leaves no ghost machines behind.
 *
 * Indexed by exact class so that twelve renderers each asking for their own type do not each filter the
 * whole set every frame.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientBlockEntities {
	private static final Map<Class<?>, Set<BlockEntity>> BY_TYPE = new ConcurrentHashMap<>();

	private ClientBlockEntities() {
	}

	public static void track(BlockEntity blockEntity) {
		BY_TYPE.computeIfAbsent(blockEntity.getClass(), type -> ConcurrentHashMap.newKeySet()).add(blockEntity);
	}

	public static void untrack(BlockEntity blockEntity) {
		Set<BlockEntity> same = BY_TYPE.get(blockEntity.getClass());
		if (same != null) same.remove(blockEntity);
	}

	/** A fresh list of the tracked machines that are of this type, or a subtype of it. */
	public static <T extends BlockEntity> List<T> of(Class<T> type) {
		List<T> found = new ArrayList<>();
		for (Map.Entry<Class<?>, Set<BlockEntity>> entry : BY_TYPE.entrySet()) {
			if (!type.isAssignableFrom(entry.getKey())) continue;

			for (BlockEntity blockEntity : entry.getValue()) {
				found.add(type.cast(blockEntity));
			}
		}

		return found;
	}

	public static void clear() {
		BY_TYPE.clear();
	}
}
