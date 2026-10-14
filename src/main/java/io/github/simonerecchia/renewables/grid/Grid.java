package io.github.simonerecchia.renewables.grid;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The power-flow simulation, one network per server level.
 *
 * {@link #tick} is called once per level at the end of every server tick, after the block entities have run, so
 * every generator has already opened its energy budget for the tick and the network sees only the residue.
 * {@link #recalcNow} is what a switch calls when it changes state, so that it reads as changed before the player lets
 * go of the button.  What each level's network remembers is in {@link LevelGrid}; this class only owns them, and
 * {@link #clear} empties them all.
 *
 * Server thread only.
 */
public final class Grid {
	private static final Map<ResourceKey<Level>, LevelGrid> GRIDS = new HashMap<>();

	private Grid() {
	}

	/** Rebuilds and solves the level's network at the end of the server tick. */
	public static void tick(ServerLevel level) {
		run(level, false);
	}

	/** Solves the level's network now instead of waiting for the next tick, e.g. after a switch moved. */
	public static void recalcNow(ServerLevel level) {
		run(level, true);
	}

	/** The server is shutting down: drop every per-level state so a world loaded next in this JVM starts clean. */
	public static void clear() {
		GRIDS.clear();
		InsulatorIds.reset();
	}

	/** A level has been unloaded: its network goes with it. */
	public static void forget(ServerLevel level) {
		GRIDS.remove(level.dimension());
	}

	private static void run(ServerLevel level, boolean immediate) {
		LevelGrid grid = GRIDS.get(level.dimension());

		// a level with no spans and nothing left to tell anyone costs one lookup a tick
		if (grid == null) {
			if (WireLedger.size(level) == 0) return;

			grid = new LevelGrid();
			GRIDS.put(level.dimension(), grid);
		}

		// a faulty machine must not take the server down; the level's own log keeps it to a line a minute
		try {
			grid.solve(level);
		} catch (RuntimeException e) {
			grid.faults().report(level, immediate ? "failed to recalculate" : "failed to tick", e);
		}

		if (grid.idle() && WireLedger.size(level) == 0) GRIDS.remove(level.dimension());
	}
}
