package io.github.simonerecchia.renewables.grid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The world's record of the insulator ids handed out, kept with the Overworld's data: not the largest so far but
 * the reservation the counter holds ahead of it ({@link IdPool}), so a restart never comes back below an id in use.
 *
 * It is the whole of the persistence: ids are unique across every dimension, so one counter serves them all.
 */
final class InsulatorIdData extends SavedData {
	static final String NAME = "renewables_insulator_ids";
	private static final String HIGHEST = "highest";

	private int highest;

	int highest() {
		return highest;
	}

	void setHighest(int highest) {
		if (highest == this.highest) return;

		this.highest = highest;
		setDirty();
	}

	static InsulatorIdData load(CompoundTag tag) {
		InsulatorIdData data = new InsulatorIdData();
		data.highest = Math.max(0, tag.getInt(HIGHEST));
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		tag.putInt(HIGHEST, highest);
		return tag;
	}
}
