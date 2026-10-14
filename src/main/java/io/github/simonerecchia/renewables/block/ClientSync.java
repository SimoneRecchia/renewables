package io.github.simonerecchia.renewables.block;



import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Publishes a machine's state to the clients watching it, no more often than it is worth: at most every
 * {@value #INTERVAL_TICKS} ticks, and only when the state has moved.  Sending the whole tag twice a second regardless
 * cost a park of arrays a thousand packets a second per player, and marked every chunk of it to be saved.
 */
final class ClientSync {
	private static final int INTERVAL_TICKS = 10;
	/** Nothing has been sent yet, which is a state and not a time. */
	private static final long NEVER = Long.MIN_VALUE;

	private long lastTick = NEVER;
	/** What the clients were last sent from here; the update tag of every user holds all it saves, so it stands for both. */
	private CompoundTag lastSent;

	/** Sends now if the gate is open and something has changed, and answers whether it went. */
	boolean throttled(BlockEntity blockEntity) {
		Level level = blockEntity.getLevel();
		if (level == null || level.isClientSide()) return false;

		if (!due(level.getGameTime())) return false;
		if (!changed(blockEntity.getUpdateTag())) return false;

		now(blockEntity);
		return true;
	}

	/** Whether the interval has run since the last look, which then starts it again. */
	boolean due(long now) {
		if (lastTick != NEVER && now - lastTick < INTERVAL_TICKS) return false;

		lastTick = now;
		return true;
	}

	/** Whether this tag differs from the last one sent, which it then becomes. */
	boolean changed(CompoundTag tag) {
		if (tag.equals(lastSent)) return false;

		lastSent = tag;
		return true;
	}

	/** Sends regardless: for a setting a player or a program has just changed. */
	static void now(BlockEntity blockEntity) {
		Level level = blockEntity.getLevel();
		if (level == null || level.isClientSide()) return;

		blockEntity.setChanged();
		level.sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(),
				Block.UPDATE_CLIENTS);
	}
}
