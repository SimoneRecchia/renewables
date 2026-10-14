package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.Dispatchable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which plant controller is holding which unit, and the limit each unit had before it was taken hold of.
 *
 * A hold is a lease: the controller renews it every tick it dispatches, and one that is not renewed lapses, and the
 * unit is given its own limit back - when the controller goes to OFF or is broken, when the unit leaves its range,
 * and when the controller's chunk is not loaded.  A unit out of memory when its hold lapses keeps the entry until it
 * is loaded again.  The ledger is saved with the level, so a restart cannot make a controller mistake the limit it set
 * for the one the unit had: that was a plant curtailed for good after one reboot.
 */
public final class DispatchLedger extends SavedData {
	static final String NAME = "renewables_dispatch";
	/** How long a hold outlives the last word from the controller that made it, in game ticks. */
	static final long LEASE_TICKS = 100L;
	/** How often lapsed holds are looked for, in game ticks. */
	private static final int CHECK_TICKS = 20;
	/** A renewal time that has lapsed whatever the clock reads, without the arithmetic overflowing. */
	private static final long LAPSED = Long.MIN_VALUE / 4;

	/** One unit held: by whom, what it had, its nameplate then, and when the hold was last renewed. */
	record Hold(BlockPos controller, double originalKw, double nameplateKw, long renewedAt) {
	}

	private final Map<BlockPos, Hold> holds = new LinkedHashMap<>();

	// ---- the bookkeeping, which needs no level ----

	/**
	 * A controller takes or keeps hold of a unit.  The first time, what the unit had is written down; after that it is
	 * kept, however often the controller has changed the limit since.  Another controller's live hold is respected.
	 */
	boolean hold(BlockPos unit, BlockPos controller, double currentLimitKw, double nameplateKw, long now) {
		Hold had = holds.get(unit);
		if (had != null && !had.controller().equals(controller) && now - had.renewedAt() <= LEASE_TICKS) return false;

		double original = had != null ? had.originalKw() : currentLimitKw;
		holds.put(unit.immutable(), new Hold(controller.immutable(), original, nameplateKw, now));
		setDirty();
		return true;
	}

	/** Lets go of a unit at once: its hold lapses now, whether or not it can be given back this tick. */
	void letGo(BlockPos unit, BlockPos controller) {
		Hold had = holds.get(unit);
		if (had == null || !had.controller().equals(controller)) return;

		holds.put(unit, new Hold(had.controller(), had.originalKw(), had.nameplateKw(), LAPSED));
		setDirty();
	}

	/** Every unit a controller holds. */
	List<BlockPos> heldBy(BlockPos controller) {
		List<BlockPos> units = new ArrayList<>();
		holds.forEach((unit, hold) -> {
			if (hold.controller().equals(controller)) units.add(unit);
		});

		return units;
	}

	/** The units whose holds have lapsed by now. */
	List<BlockPos> lapsed(long now) {
		List<BlockPos> units = new ArrayList<>();
		holds.forEach((unit, hold) -> {
			if (now - hold.renewedAt() > LEASE_TICKS) units.add(unit);
		});

		return units;
	}

	@Nullable
	Hold holdOn(BlockPos unit) {
		return holds.get(unit);
	}

	void forget(BlockPos unit) {
		if (holds.remove(unit) != null) setDirty();
	}

	// ---- in a level ----

	/** The level's ledger, read from disk the first time; an empty one is never written. */
	static DispatchLedger of(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(DispatchLedger::load, DispatchLedger::new, NAME);
	}

	/** Holds a unit to a limit for a controller, unless another controller holds it; answers whether it did. */
	static boolean dispatch(ServerLevel level, BlockPos unitPos, BlockPos controller, Dispatchable unit, double limitKw) {
		if (!of(level).hold(unitPos, controller, unit.getActivePowerLimit(), unit.getNameplateKw(), level.getGameTime())) return false;

		if (Math.abs(unit.getActivePowerLimit() - limitKw) > 0.01) unit.setActivePowerLimit(limitKw);
		return true;
	}

	/** Gives a unit back what it had: now if it is loaded, otherwise the next time it is. */
	static void release(ServerLevel level, BlockPos unitPos, BlockPos controller) {
		DispatchLedger ledger = of(level);
		ledger.letGo(unitPos, controller);
		ledger.giveBack(level, unitPos);
	}

	/** Gives back every lapsed hold whose unit is loaded; runs at the end of each level's tick. */
	public static void tick(ServerLevel level) {
		if (level.getGameTime() % CHECK_TICKS != 0) return;

		DispatchLedger ledger = of(level);
		if (ledger.holds.isEmpty()) return;

		for (BlockPos unit : ledger.lapsed(level.getGameTime())) {
			ledger.giveBack(level, unit);
		}
	}

	/** Restores a lapsed hold's limit if the unit is loaded, and drops the entry; a unit that is gone is just dropped. */
	private void giveBack(ServerLevel level, BlockPos unitPos) {
		Hold hold = holds.get(unitPos);
		if (hold == null || level.getGameTime() - hold.renewedAt() <= LEASE_TICKS) return;
		if (!level.isLoaded(unitPos)) return;

		BlockEntity entity = level.getBlockEntity(unitPos);
		// the same machine, or one of the same size: a different one built where it stood never had that limit
		if (entity instanceof Dispatchable unit && Math.abs(unit.getNameplateKw() - hold.nameplateKw()) < 0.01) {
			unit.setActivePowerLimit(hold.originalKw());
		}

		forget(unitPos);
	}

	// ---- persistence ----

	static DispatchLedger load(CompoundTag tag) {
		DispatchLedger ledger = new DispatchLedger();
		ListTag list = tag.getList("holds", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			ledger.holds.put(BlockPos.of(entry.getLong("unit")), new Hold(BlockPos.of(entry.getLong("controller")),
					entry.getDouble("original"), entry.getDouble("nameplate"), entry.getLong("renewed")));
		}

		return ledger;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag list = new ListTag();
		holds.forEach((unit, hold) -> {
			CompoundTag entry = new CompoundTag();
			entry.putLong("unit", unit.asLong());
			entry.putLong("controller", hold.controller().asLong());
			entry.putDouble("original", hold.originalKw());
			entry.putDouble("nameplate", hold.nameplateKw());
			entry.putLong("renewed", hold.renewedAt());
			list.add(entry);
		});

		tag.put("holds", list);
		return tag;
	}
}
