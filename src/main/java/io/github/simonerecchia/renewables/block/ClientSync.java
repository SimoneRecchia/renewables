package io.github.simonerecchia.renewables.block;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Publishes a machine's state to the clients watching it, no more often than it is worth: it looks every
 * {@value #INTERVAL_TICKS} ticks and sends only what has moved.  Sending the whole tag twice a second regardless cost
 * a park of arrays a thousand packets a second per player, and marked every chunk of it to be saved.
 *
 * Two ways of sending.  A machine there are few of sends its whole update tag, when anything in it has changed.  A
 * machine there are thousands of - a row - sends only the keys that moved, a SCADA's report by exception: a state (a
 * mode, a stow, a flag, a position) the first look after it changes, a reading once it is half a percent from what the
 * clients were last told and at most once a second, and nothing at all when nothing has moved.  The client lays each
 * delta over the whole state it already holds.  A setting a player changes goes at once either way, through {@link #now}.
 */
final class ClientSync {
	private static final int INTERVAL_TICKS = 10;
	/** The least time between two deltas that carry only readings, in ticks: once a second. */
	static final int READING_INTERVAL_TICKS = 20;
	/** Nothing has been sent yet, which is a state and not a time. */
	private static final long NEVER = Long.MIN_VALUE;
	/** Marks an update that carries only what changed, to be laid over what the client already has. */
	static final String DELTA = "renewables:delta";
	/** The keys a delta takes away. */
	static final String REMOVED = "renewables:removed";
	/** How far a reading moves, as a share of itself, before it is worth sending again. */
	static final double DEADBAND = 0.005;
	/** And absolutely, for a reading sitting near zero. */
	static final double ABSOLUTE_DEADBAND = 1.0e-3;

	private final boolean deltas;
	private long lastTick = NEVER;
	/** When readings last went out, for the once-a-second ceiling on them. */
	private long lastReadingTick = NEVER;
	/**
	 * Server: what the watching clients were last told.  For a whole-tag machine the tag last sent; for a delta-synced
	 * one the same thing key by key, kept up to date by every delta.  Null until they have been told anything.
	 */
	@Nullable
	private CompoundTag told;
	/** Client: everything the server has said, each delta laid over the last whole tag. */
	@Nullable
	private CompoundTag heard;

	ClientSync() {
		this(false);
	}

	private ClientSync(boolean deltas) {
		this.deltas = deltas;
	}

	/** For a machine with many readings and many copies of itself in a world: it sends only what moved. */
	static ClientSync deltas() {
		return new ClientSync(true);
	}

	/** Sends now if the gate is open and something has moved, and answers whether it went. */
	boolean throttled(BlockEntity blockEntity) {
		Level level = blockEntity.getLevel();
		if (level == null || level.isClientSide()) return false;

		long now = level.getGameTime();
		if (!due(now)) return false;

		if (!deltas) {
			if (!changed(blockEntity.getUpdateTag())) return false;

			now(blockEntity);
			return true;
		}

		CompoundTag delta = changes(blockEntity.getUpdateTag(), DEADBAND, readingsDue(now));
		if (delta.isEmpty()) return false;

		readingsSent(now);

		// what moved is state, and the chunk has to be saved with it; what did not move does not dirty the chunk
		blockEntity.setChanged();
		if (level instanceof ServerLevel serverLevel) {
			Packet<ClientGamePacketListener> packet = ClientboundBlockEntityDataPacket.create(blockEntity, entity -> delta);
			for (ServerPlayer player : serverLevel.getChunkSource().chunkMap.getPlayers(new ChunkPos(blockEntity.getBlockPos()), false)) {
				player.connection.send(packet);
			}
		}

		return true;
	}

	/** Whether the interval has run since the last look, which then starts it again. */
	boolean due(long now) {
		if (lastTick != NEVER && now - lastTick < INTERVAL_TICKS) return false;

		lastTick = now;
		return true;
	}

	/** Records a delta going out: whatever went carried every reading past its band, so their second starts again. */
	void readingsSent(long now) {
		lastReadingTick = now;
	}

	/** Whether readings may go out at this tick: a second after the last delta that carried them. */
	boolean readingsDue(long now) {
		return lastReadingTick == NEVER || now - lastReadingTick >= READING_INTERVAL_TICKS;
	}

	/** Whole-tag machines: whether this tag differs from the last one sent, which it then becomes. */
	boolean changed(CompoundTag tag) {
		if (tag.equals(told)) return false;

		told = tag;
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

	/**
	 * The update packet of a delta-synced machine, for vanilla's own broadcast after {@link #now} or a change of
	 * block state: everything that has changed since the clients were last told, however little, or null for nothing.
	 */
	@Nullable
	Packet<ClientGamePacketListener> exactUpdate(BlockEntity blockEntity) {
		CompoundTag delta = changes(blockEntity.getUpdateTag(), 0.0, true);
		return delta.isEmpty() ? null : ClientboundBlockEntityDataPacket.create(blockEntity, entity -> delta);
	}

	/** {@link #changes(CompoundTag, double, boolean)} with the readings due. */
	CompoundTag changes(CompoundTag whole, double deadband) {
		return changes(whole, deadband, true);
	}

	/**
	 * What to send of a whole update tag, given what the clients were told: every state that changed, the keys that
	 * are gone, and - when they are due, or when a state is going anyway - every reading that moved past the deadband.
	 * Readings not sent stay as they were told, so a slow creep is sent once it has crept far enough.  The first time it
	 * is the whole tag, unmarked, which a client takes as it is.
	 */
	CompoundTag changes(CompoundTag whole, double deadband, boolean readingsDue) {
		if (told == null) {
			told = whole.copy();
			return whole;
		}

		CompoundTag states = new CompoundTag();
		CompoundTag readings = new CompoundTag();
		ListTag removed = new ListTag();
		for (String key : told.getAllKeys()) {
			if (!whole.contains(key)) removed.add(StringTag.valueOf(key));
		}

		for (String key : whole.getAllKeys()) {
			Tag value = whole.get(key);
			Tag had = told.get(key);
			if (had == null || !isReading(had, value)) {
				if (had == null || !had.equals(value)) states.put(key, value);
			} else if (moved(had, value, deadband)) {
				readings.put(key, value);
			}
		}

		boolean sending = !states.isEmpty() || !removed.isEmpty() || (readingsDue && !readings.isEmpty());
		CompoundTag delta = new CompoundTag();
		if (!sending) return delta;

		for (String key : states.getAllKeys()) {
			delta.put(key, states.get(key));
		}

		for (String key : readings.getAllKeys()) {
			delta.put(key, readings.get(key));
		}

		for (Tag key : removed) {
			told.remove(key.getAsString());
		}

		for (String key : delta.getAllKeys()) {
			told.put(key, delta.get(key).copy());
		}

		if (!removed.isEmpty()) delta.put(REMOVED, removed);
		delta.putBoolean(DELTA, true);
		return delta;
	}

	/** A reading is a floating-point value, which wanders; anything else is a state, and is exact. */
	private static boolean isReading(Tag had, Tag now) {
		return (had instanceof DoubleTag || had instanceof FloatTag) && had.getId() == now.getId();
	}

	/** Whether a value has moved enough to send: a reading past its deadband, anything else at all. */
	static boolean moved(Tag had, Tag now, double deadband) {
		if (deadband <= 0.0 || !isReading(had, now)) return !had.equals(now);

		double before = ((NumericTag) had).getAsDouble();
		double after = ((NumericTag) now).getAsDouble();
		double band = Math.max(ABSOLUTE_DEADBAND, deadband * Math.max(Math.abs(before), Math.abs(after)));
		return Math.abs(after - before) > band;
	}

	/** Client: lays an update over what was heard before and answers the whole state, for the machine to load. */
	CompoundTag heard(CompoundTag update) {
		if (heard == null || !update.contains(DELTA)) {
			heard = update.copy();
			heard.remove(DELTA);
			heard.remove(REMOVED);
			return heard;
		}

		for (Tag key : update.getList(REMOVED, Tag.TAG_STRING)) {
			heard.remove(key.getAsString());
		}

		for (String key : update.getAllKeys()) {
			if (key.equals(DELTA) || key.equals(REMOVED)) continue;

			heard.put(key, update.get(key).copy());
		}

		return heard;
	}
}
