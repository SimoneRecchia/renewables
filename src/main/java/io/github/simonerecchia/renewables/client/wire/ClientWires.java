package io.github.simonerecchia.renewables.client.wire;

import io.github.simonerecchia.renewables.grid.WireConnection;

import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The spans the client knows about, and the geometry cached for drawing them.
 *
 * STUB: owner C.  The entry points are the contract: the network handlers and the machines call exactly
 * these.  The real class keeps the span map (keyed by the two fitting ids), the pending connection and the
 * per-span geometry cache, and draws them (docs/spec/client-net.md sections 5 and 6).
 */
@OnlyIn(Dist.CLIENT)
public final class ClientWires {
	private ClientWires() {
	}

	/** A sync batch arrived: with {@code replace}, forget every span first, the pending connection and the caches. */
	public static void sync(List<WireConnection> spans, boolean replace) {
		// STUB: owner C
	}

	/** One span was strung. */
	public static void add(WireConnection span) {
		// STUB: owner C
	}

	/** One span was taken down. */
	public static void remove(WireConnection span) {
		// STUB: owner C
	}

	/** The geometry cached for spans touching any of these fitting ids is stale: a machine moved or reloaded. */
	public static void invalidate(int[] insulatorIds) {
		// STUB: owner C
	}

	/** Forget everything: leaving a world, or the client level unloading. */
	public static void clear() {
		// STUB: owner C
	}
}
