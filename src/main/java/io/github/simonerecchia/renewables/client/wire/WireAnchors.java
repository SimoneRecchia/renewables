package io.github.simonerecchia.renewables.client.wire;

import io.github.simonerecchia.renewables.grid.InsulatorHost;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Where a fitting is in the world, as far as this client can tell: the one question a span has to ask twice.
 *
 * The id is looked up in {@link InsulatorIndex} first; failing that, and only if the chunk the span says the machine
 * is in is loaded, the block entity there is asked and entered in the index.  A machine that is not loaded yet
 * answers "don't know" and the span waits, it is never dropped.
 */
@OnlyIn(Dist.CLIENT)
final class WireAnchors {
	private WireAnchors() {
	}

	/** The end of a wire at fitting {@code id}, or null while it cannot be told. */
	@Nullable
	static Vec3 anchorOf(Level level, int id, BlockPos hostPos) {
		if (id <= 0) return null;

		InsulatorHost host = InsulatorIndex.find(id);
		if (host == null) {
			if (!level.isLoaded(hostPos)) return null;

			BlockEntity entity = level.getBlockEntity(hostPos);
			if (!(entity instanceof InsulatorHost found)) return null;

			InsulatorIndex.register(found);
			host = found;
		}

		int index = indexOf(host.getInsulatorIds(), id);
		return index < 0 ? null : host.getWirePosition(index);
	}

	/** The position of {@code id} among a machine's fitting ids, or -1. */
	static int indexOf(int[] ids, int id) {
		for (int i = 0; i < ids.length; i++) {
			if (ids[i] == id) return i;
		}

		return -1;
	}
}
