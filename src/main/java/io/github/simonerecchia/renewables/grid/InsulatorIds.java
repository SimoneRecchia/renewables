package io.github.simonerecchia.renewables.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;

/**
 * Hands out the integer id every fitting is known by, unique across the server and never reused.
 *
 * Spans are saved on these ids, so they have to outlive a restart and must never point at the wrong machine.  The
 * counter is world data (kept with the Overworld, like every dimension's spans refer to ids from this one
 * sequence): a restart does not wind it back, and an id read from a machine in a chunk that is not loaded yet still
 * counts, because every span names its ids and {@link WireLedger} adopts them all when the world opens.  Two
 * machines that claim the same id (a block copied with its data) are told apart: the one the saved spans do not put
 * the id on, or else the second to load, is given a new one.
 *
 * The three entry points are the contract machines call.  They are safe to call from any thread, but only the
 * server thread works on the world's counter; a client (even the client half of a single-player game) takes its
 * ids from a separate, throwaway counter, because a client never assigns real ids - what it holds it was sent.
 */
public final class InsulatorIds {
	/**
	 * Where a client's own throwaway ids start: far above anything a world's counter reaches, so a stray one can
	 * never be taken for a server id.
	 */
	private static final int CLIENT_BASE = 1_500_000_000;

	private static final Object LOCK = new Object();
	private static final IdPool CLIENT = new IdPool(CLIENT_BASE, false, null);

	@Nullable
	private static MinecraftServer server;
	@Nullable
	private static IdPool world;
	/** Set when the world closes, so a machine unloading during shutdown cannot bind the counter to the dying server again. */
	private static boolean sealed;

	private InsulatorIds() {
	}

	/**
	 * Gives every unassigned slot (zero or less) a fresh id, and replaces an id that another loaded machine already
	 * holds.  Call it when a machine loads on the server, never from a constructor: a block entity read from disk is
	 * built before its saved ids are.
	 */
	public static void claim(int[] ids) {
		pool().claim(ids);
	}

	/**
	 * {@link #claim(int[])} for the machine at {@code pos}, which also replaces an id that the saved spans put on a
	 * machine somewhere else: a copy that loads before its original (or while the original's chunk is not loaded)
	 * must not take the original's spans.
	 */
	public static void claim(int[] ids, ServerLevel level, BlockPos pos) {
		pool().claim(ids, id -> WireLedger.belongsElsewhere(level, pos, id));
	}

	/** An id read from a saved machine is in use: the counter must never hand it out again. */
	public static void adopt(int id) {
		pool().adopt(id);
	}

	/** The machine has gone.  Ids are never reused, so this only forgets who held them. */
	public static void release(int[] ids) {
		pool().release(ids);
	}

	/**
	 * Binds the counter to the world's saved data.  Called when the Overworld is created, before any chunk (and so
	 * any machine) loads; later calls with the same server do nothing.
	 */
	public static void attach(ServerLevel overworld) {
		synchronized (LOCK) {
			sealed = false;
			if (world != null && server == overworld.getServer()) return;

			InsulatorIdData data = overworld.getDataStorage().computeIfAbsent(InsulatorIdData::load, InsulatorIdData::new, InsulatorIdData.NAME);
			server = overworld.getServer();
			world = new IdPool(data.highest(), true, data::setHighest);
		}
	}

	/** The world has closed: nothing of it is kept for the next one in this JVM. */
	public static void reset() {
		synchronized (LOCK) {
			server = null;
			world = null;
			sealed = true;
			CLIENT.forgetHolders();
		}
	}

	private static IdPool pool() {
		synchronized (LOCK) {
			if (world == null && !sealed) {
				// a machine may load before the level-created event reaches us on some path; the first claim on the server thread attaches
				MinecraftServer current = ServerLifecycleHooks.getCurrentServer();
				if (current != null && current.isSameThread() && current.overworld() != null) attach(current.overworld());
			}

			if (world != null && server != null && server.isSameThread()) return world;

			return CLIENT;
		}
	}
}
