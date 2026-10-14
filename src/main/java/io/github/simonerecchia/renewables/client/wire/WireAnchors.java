package io.github.simonerecchia.renewables.client.wire;

import io.github.simonerecchia.renewables.grid.InsulatorHost;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Where a fitting is in the world, as far as this client can tell: the one question a span has to ask twice.
 *
 * The id is looked up in {@link InsulatorIndex} first; failing that - or when the machine the index names no longer
 * holds the id, because it has gone to another machine since - and only if the chunk the span says the machine is in
 * is loaded, the block entity there is asked and entered in the index.  A machine that is not loaded yet answers
 * "don't know" and the span waits, it is never dropped.
 */
@OnlyIn(Dist.CLIENT)
final class WireAnchors {
	private WireAnchors() {
	}

	/** The end of a wire at fitting {@code id}, or null while it cannot be told. */
	@Nullable
	static Vec3 anchorOf(Level level, int id, BlockPos hostPos) {
		return anchorOf(level, id, hostPos, null);
	}

	/**
	 * The end of a wire at fitting {@code id} that runs towards {@code toward} (a tension tower's dead-end on that side;
	 * see {@link InsulatorHost#getWirePosition(int, Vec3)}), or the fitting's one point when {@code toward} is null.
	 */
	@Nullable
	static Vec3 anchorOf(Level level, int id, BlockPos hostPos, @Nullable Vec3 toward) {
		return anchorOf(id, () -> level.isLoaded(hostPos) && level.getBlockEntity(hostPos) instanceof InsulatorHost found
				? found : null, toward);
	}

	/**
	 * The same, with the machine at the span's own position given as {@code atHostPos}: null when it is not loaded
	 * or is not a machine with fittings.
	 */
	@Nullable
	static Vec3 anchorOf(int id, Supplier<InsulatorHost> atHostPos) {
		return anchorOf(id, atHostPos, null);
	}

	@Nullable
	private static Vec3 anchorOf(int id, Supplier<InsulatorHost> atHostPos, @Nullable Vec3 toward) {
		if (id <= 0) return null;

		InsulatorHost host = InsulatorIndex.find(id);
		if (host != null) {
			int index = indexOf(host.getInsulatorIds(), id);
			if (index >= 0) return positionOf(host, index, toward);

			// the id has gone to another machine since this one was entered: the entry is stale, not the span
			InsulatorIndex.unregister(host, new int[] {id});
		}

		InsulatorHost found = atHostPos.get();
		if (found == null) return null;

		InsulatorIndex.register(found);
		int index = indexOf(found.getInsulatorIds(), id);
		return index < 0 ? null : positionOf(found, index, toward);
	}

	private static Vec3 positionOf(InsulatorHost host, int index, @Nullable Vec3 toward) {
		return toward == null ? host.getWirePosition(index) : host.getWirePosition(index, toward);
	}

	/** The position of {@code id} among a machine's fitting ids, or -1. */
	static int indexOf(int[] ids, int id) {
		for (int i = 0; i < ids.length; i++) {
			if (ids[i] == id) return i;
		}

		return -1;
	}
}
