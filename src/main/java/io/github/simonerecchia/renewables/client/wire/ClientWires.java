package io.github.simonerecchia.renewables.client.wire;

import io.github.simonerecchia.renewables.grid.WireConnection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The spans the client knows about, the connection a player has begun, and the geometry cached for drawing them.
 *
 * The server is the only source: a full list at login, on a change of dimension and on respawn (in batches, the first
 * replacing and the rest adding), then one message per span strung or taken down.  The client keeps every span of its
 * level, drawn or not; a span whose machines are not loaded stays in the map and appears by itself when they are
 * (docs/spec/client-net.md sections 5 and 6).  The key is the two fitting ids, start first: the server refuses a
 * duplicate in either order.
 *
 * Everything here is touched only on the client's main thread - the network handlers are queued onto it and so are
 * the block entities' load and update callbacks - so nothing is synchronised.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientWires {
	private static final Map<Long, ClientSpan> BY_KEY = new HashMap<>();
	/** The same spans in a flat list, which is what the renderer walks without allocating. */
	private static final ArrayList<ClientSpan> SPANS = new ArrayList<>();
	/** Which spans touch each fitting id, so a machine that moves invalidates only its own. */
	private static final Map<Integer, List<ClientSpan>> BY_INSULATOR = new HashMap<>();
	private static final LinkMachine<PendingLink> LINK = new LinkMachine<>();

	private ClientWires() {
	}

	// ---- messages from the server ----

	/**
	 * A sync batch arrived.  With {@code replace}, forget every span first - and the pending connection, and what was
	 * cached about where fittings are - because this is the whole world as the server sees it now.
	 */
	public static void sync(List<WireConnection> spans, boolean replace) {
		if (replace) {
			clearSpans();
			LINK.clear();
		}

		for (WireConnection span : spans) {
			add(span);
		}
	}

	/** One span was strung.  One with the same two ends is replaced. */
	public static void add(WireConnection span) {
		// an id of zero or less is a fitting the server has not numbered: there is nothing to look up
		if (span.startInsulatorId() <= 0 || span.endInsulatorId() <= 0) return;

		long key = keyOf(span.startInsulatorId(), span.endInsulatorId());
		ClientSpan old = BY_KEY.get(key);
		if (old != null) detach(old);

		ClientSpan added = new ClientSpan(span, key);
		BY_KEY.put(key, added);
		added.slot = SPANS.size();
		SPANS.add(added);
		BY_INSULATOR.computeIfAbsent(span.startInsulatorId(), id -> new ArrayList<>(2)).add(added);
		BY_INSULATOR.computeIfAbsent(span.endInsulatorId(), id -> new ArrayList<>(2)).add(added);
	}

	/** One span was taken down. */
	public static void remove(WireConnection span) {
		ClientSpan old = BY_KEY.get(keyOf(span.startInsulatorId(), span.endInsulatorId()));
		if (old != null) detach(old);
	}

	/** The geometry cached for spans touching any of these fitting ids is stale: a machine moved, reloaded or went. */
	public static void invalidate(int[] insulatorIds) {
		if (SPANS.isEmpty()) return;

		for (int id : insulatorIds) {
			List<ClientSpan> touching = BY_INSULATOR.get(id);
			if (touching == null) continue;

			for (int i = 0; i < touching.size(); i++) {
				touching.get(i).invalidate();
			}
		}
	}

	/** Forget everything: leaving a world, or the client level unloading. */
	public static void clear() {
		clearSpans();
		LINK.clear();
	}

	// ---- reading ----

	/** Every span the client knows, as a new list: for the few callers that are not a frame loop. */
	public static List<WireConnection> all() {
		List<WireConnection> all = new ArrayList<>(SPANS.size());
		for (ClientSpan span : SPANS) {
			all.add(span.wire);
		}

		return Collections.unmodifiableList(all);
	}

	public static int count() {
		return SPANS.size();
	}

	/** The first fitting of the connection the player has begun, or null. */
	@Nullable
	public static PendingLink pending() {
		return LINK.pending();
	}

	/** The two-click machine, for the input code. */
	public static LinkMachine<PendingLink> link() {
		return LINK;
	}

	/** Drops the connection the player has begun. */
	public static void cancelPending() {
		LINK.clear();
	}

	/** The registry's own list, for the renderer to walk by index.  Do not modify. */
	static List<ClientSpan> spans() {
		return SPANS;
	}

	// ---- internals ----

	static long keyOf(int startId, int endId) {
		return ((long) startId << 32) | (endId & 0xFFFFFFFFL);
	}

	private static void clearSpans() {
		BY_KEY.clear();
		SPANS.clear();
		BY_INSULATOR.clear();
	}

	/** Takes one span out of all three structures; the list by swapping the last into its place. */
	private static void detach(ClientSpan span) {
		BY_KEY.remove(span.key);

		int last = SPANS.size() - 1;
		ClientSpan moved = SPANS.remove(last);
		if (moved != span) {
			SPANS.set(span.slot, moved);
			moved.slot = span.slot;
		}

		span.slot = -1;
		dropFrom(span.wire.startInsulatorId(), span);
		dropFrom(span.wire.endInsulatorId(), span);
	}

	private static void dropFrom(int id, ClientSpan span) {
		List<ClientSpan> touching = BY_INSULATOR.get(id);
		if (touching == null) return;

		touching.remove(span);
		if (touching.isEmpty()) BY_INSULATOR.remove(id);
	}
}
