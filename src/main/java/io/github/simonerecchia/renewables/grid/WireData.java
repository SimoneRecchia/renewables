package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.Renewables;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * A level's spans as saved data.  One per level, stored with that level, so a span belongs to the dimension it was
 * strung in and nothing about a level outlives it in a static.
 */
final class WireData extends SavedData {
	static final String NAME = "renewables_wires";

	private final SpanStore store = new SpanStore();

	SpanStore store() {
		return store;
	}

	/** The level's data, read from disk the first time and created empty if the level has none. */
	static WireData of(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(WireData::load, WireData::new, NAME);
	}

	static WireData load(CompoundTag tag) {
		WireData data = new WireData();
		for (WireConnection span : SpanCodec.read(tag, message -> Renewables.LOGGER.warn("Wire data: {}", message))) {
			if (!data.store.add(span)) Renewables.LOGGER.warn("Wire data: span {} - {} repeats another, dropped",
					span.startInsulatorId(), span.endInsulatorId());
		}

		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		return SpanCodec.write(store.all(), tag);
	}
}
