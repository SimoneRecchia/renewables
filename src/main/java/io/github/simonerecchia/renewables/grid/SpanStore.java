package io.github.simonerecchia.renewables.grid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The spans of one level, with the questions the rest of the mod asks of them answered without a scan: is this
 * pair already joined, how many spans hang from this fitting, which spans use these ids.
 *
 * Plain collections, no world and no persistence (that is {@link WireData}), so the invariants can be tested:
 * a pair is unique in either order, a fitting is never joined to itself, ids are positive.
 */
final class SpanStore {
	private final Map<Long, WireConnection> spans = new LinkedHashMap<>();
	private final Map<Integer, List<Long>> keysAt = new HashMap<>();

	/** The unordered pair of ids, one key for both orders so that "already joined" is one lookup. */
	static long pairKey(int a, int b) {
		int low = Math.min(a, b);
		int high = Math.max(a, b);
		return ((long) low << 32) | (high & 0xFFFFFFFFL);
	}

	int size() {
		return spans.size();
	}

	boolean isEmpty() {
		return spans.isEmpty();
	}

	/** Whether the two fittings are already joined, whichever way round the span was strung. */
	boolean joined(int a, int b) {
		return spans.containsKey(pairKey(a, b));
	}

	/** How many spans hang from a fitting. */
	int countAt(int id) {
		List<Long> keys = keysAt.get(id);
		return keys == null ? 0 : keys.size();
	}

	/** @return false, and nothing changes, if an id is not positive, the ends are one fitting, or the pair is taken */
	boolean add(WireConnection span) {
		int a = span.startInsulatorId();
		int b = span.endInsulatorId();
		if (a <= 0 || b <= 0 || a == b) return false;

		long key = pairKey(a, b);
		if (spans.containsKey(key)) return false;

		spans.put(key, span);
		keysAt.computeIfAbsent(a, id -> new ArrayList<>(2)).add(key);
		keysAt.computeIfAbsent(b, id -> new ArrayList<>(2)).add(key);
		return true;
	}

	/** A copy of every span, safe to hold while the store changes. */
	List<WireConnection> all() {
		return new ArrayList<>(spans.values());
	}

	/** Takes out every span that has one of these ids at either end, and returns them. */
	List<WireConnection> removeAttachedTo(Collection<Integer> ids) {
		Set<Long> doomed = new java.util.LinkedHashSet<>();
		for (int id : ids) {
			List<Long> keys = keysAt.get(id);
			if (keys != null) doomed.addAll(keys);
		}

		List<WireConnection> removed = new ArrayList<>(doomed.size());
		for (long key : doomed) {
			WireConnection span = spans.remove(key);
			if (span == null) continue;

			removed.add(span);
			forget(span.startInsulatorId(), key);
			forget(span.endInsulatorId(), key);
		}

		return removed;
	}

	private void forget(int id, long key) {
		List<Long> keys = keysAt.get(id);
		if (keys == null) return;

		keys.remove(key);
		if (keys.isEmpty()) keysAt.remove(id);
	}
}
