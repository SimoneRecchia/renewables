package io.github.simonerecchia.renewables.grid;

import net.minecraft.server.level.ServerLevel;

/**
 * The power-flow simulation, one per server level.
 *
 * STUB: owner G.  {@link #tick} is called once per level at the end of every server tick, after the block
 * entities have run; {@link #recalcNow} is what a switch calls when it changes state.
 */
public final class Grid {
	private Grid() {
	}

	/** Rebuilds and solves the level's network at the end of the server tick. */
	public static void tick(ServerLevel level) {
		// STUB: owner G
	}

	/** Solves the level's network now instead of waiting for the next tick, e.g. after a switch moved. */
	public static void recalcNow(ServerLevel level) {
		// STUB: owner G
	}

	/** The server is shutting down: drop every per-level state so a world loaded next in this JVM starts clean. */
	public static void clear() {
		// STUB: owner G
	}
}
