package io.github.simonerecchia.renewables.grid;

import net.minecraft.server.level.ServerPlayer;

/**
 * Validates a request to string a span and, if it holds, creates it.
 *
 * STUB: owner G.  The full chain of server-side checks, in order, is grid.md section 6.2 (chunk loaded,
 * world border, may-interact, block entities present and owning the ids, fitting types, anchors, sanitised
 * power types, duplicates, conductor in hand, voltage class, reach, cost).  The packet handler has already
 * rejected a non-positive or repeated id and an oversized string, so this method may assume those.
 */
public final class SpanPlanner {
	private SpanPlanner() {
	}

	public static void request(ServerPlayer player, SpanRequest request) {
		// STUB: owner G
	}
}
