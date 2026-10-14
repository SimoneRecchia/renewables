package io.github.simonerecchia.renewables.grid;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hands out the integer id every fitting is known by, unique across the server.
 *
 * STUB: owner G.  This is the in-memory counter the contract allows for now: it restarts with the process,
 * so ids must become world data (monotonic, never reused) before wires are persisted for real.  The three
 * entry points are the contract and do not change.
 */
public final class InsulatorIds {
	private static final AtomicInteger HIGHEST = new AtomicInteger();

	private InsulatorIds() {
	}

	/** Gives every unassigned (zero) slot a fresh id. */
	public static void claim(int[] ids) {
		for (int i = 0; i < ids.length; i++) {
			if (ids[i] <= 0) ids[i] = HIGHEST.incrementAndGet();
		}
	}

	/** An id read back from disk is in use: the counter must never hand it out again. */
	public static void adopt(int id) {
		HIGHEST.accumulateAndGet(id, Math::max);
	}

	/** The block is gone. Ids are never reused, so there is nothing to give back. */
	public static void release(int[] ids) {
	}
}
