package io.github.simonerecchia.renewables.block;

import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The state property a power field switches: the first boolean that is called {@code powered} or {@code lit}.
 *
 * A name, not a list of blocks, because the field is meant to switch whatever the player has built round the box
 * - a lamp, a rail, a furnace, a door, a block from another mod - and the two names are what all of those call it.
 * The consequence is that it also switches blocks nobody thought of (an observer, a door), which is the price of
 * not keeping a list.
 */
final class FieldSwitch {
	private FieldSwitch() {
	}

	/** The property to set, or null if this block has none. */
	@Nullable
	static BooleanProperty of(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property instanceof BooleanProperty flag && named(flag.getName())) return flag;
		}

		return null;
	}

	static boolean named(String name) {
		return "powered".equalsIgnoreCase(name) || "lit".equalsIgnoreCase(name);
	}
}
