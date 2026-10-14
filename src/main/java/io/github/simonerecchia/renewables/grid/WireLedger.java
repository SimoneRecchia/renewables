package io.github.simonerecchia.renewables.grid;

import java.util.List;
import net.minecraft.server.level.ServerLevel;

/**
 * Every span saved in a world, per level, and what happens to it when a machine goes.
 *
 * STUB: owner G.  Persistence is a {@code SavedData} on each level; the in-memory list here only lets the
 * login synchronisation compile and run.  Spans removed here must be announced to the clients with
 * {@code Net.sendToPlayersInLevel} (message {@code SpanChangedPayload}) and refunded, as grid.md section 9 says.
 */
public final class WireLedger {
	private WireLedger() {
	}

	/** Every span of a level, in no particular order. */
	public static List<WireConnection> spans(ServerLevel level) {
		return List.of();
	}

	/** Removes (and refunds) every span attached to any of these fitting ids. */
	public static void removeAttachedTo(ServerLevel level, int[] insulatorIds) {
		// STUB: owner G
	}

	/** The server is up: read each level's saved spans. */
	public static void onServerStarted(net.minecraft.server.MinecraftServer server) {
		// STUB: owner G
	}

	/** The server is stopping: mark the saved data dirty so the engine writes it out. */
	public static void onServerStopping(net.minecraft.server.MinecraftServer server) {
		// STUB: owner G
	}
}
