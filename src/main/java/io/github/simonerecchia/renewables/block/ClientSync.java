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
 * Publishes a machine's state to the clients watching it, no more often than it is worth.
 *
 * Two ways.  A machine there are few of sends its whole update tag every half second.  A machine there are
 * thousands of - a row - sends only what has moved, a SCADA's report by exception: a reading goes again once it has
 * moved half a percent from what the clients were last told, a setting the moment it changes, and nothing at all
 * when nothing has.  The client lays each delta over the whole state it already holds.
 */
final class ClientSync {
	private static final int INTERVAL_TICKS = 10;
	/** How often a delta-synced machine looks for something worth sending, in ticks. */
	private static final int DELTA_INTERVAL_TICKS = 20;
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
	/** Server: what the watching clients were last told, key by key; null until they have been told anything. */
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

	/** Sends now if the gate is open and, for a delta-synced machine, anything has moved; answers whether it went. */
	boolean throttled(BlockEntity blockEntity) {
		Level level = blockEntity.getLevel();
		if (level == null || level.isClientSide()) return false;

		long now = level.getGameTime();
		int interval = deltas ? DELTA_INTERVAL_TICKS : INTERVAL_TICKS;
		if (lastTick != NEVER && now - lastTick < interval) return false;

		lastTick = now;
		if (!deltas) {
			now(blockEntity);
			return true;
		}

		CompoundTag delta = changes(blockEntity.getUpdateTag(), DEADBAND);
		if (delta.isEmpty()) return false;

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
		CompoundTag delta = changes(blockEntity.getUpdateTag(), 0.0);
		return delta.isEmpty() ? null : ClientboundBlockEntityDataPacket.create(blockEntity, entity -> delta);
	}

	/**
	 * What to send of a whole update tag, given what the clients were told: every key that moved past the deadband,
	 * and the keys that are gone.  The first time it is the whole tag, unmarked, which a client takes as it is.
	 */
	CompoundTag changes(CompoundTag whole, double deadband) {
		if (told == null) {
			told = whole.copy();
			return whole;
		}

		CompoundTag delta = new CompoundTag();
		ListTag removed = new ListTag();
		for (String key : told.getAllKeys()) {
			if (!whole.contains(key)) removed.add(StringTag.valueOf(key));
		}

		for (String key : whole.getAllKeys()) {
			Tag value = whole.get(key);
			Tag had = told.get(key);
			if (had == null || moved(had, value, deadband)) delta.put(key, value);
		}

		if (delta.isEmpty() && removed.isEmpty()) return delta;

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

	/** Whether a value has moved enough to send: a reading past its deadband, anything else at all. */
	static boolean moved(Tag had, Tag now, double deadband) {
		boolean reading = (had instanceof DoubleTag || had instanceof FloatTag) && had.getId() == now.getId();
		if (deadband <= 0.0 || !reading) return !had.equals(now);

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
