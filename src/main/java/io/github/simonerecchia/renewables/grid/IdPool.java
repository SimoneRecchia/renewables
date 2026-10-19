package io.github.simonerecchia.renewables.grid;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import javax.annotation.Nullable;

/**
 * The counter behind {@link InsulatorIds}, with no world attached so it can be tested alone.
 *
 * Ids only ever grow and are never handed out twice: the counter is the largest id ever given or read from disk.
 * On top of that, when it is asked to, the pool remembers which loaded machine holds which id, so that a second
 * machine that turns up claiming an id already held (a block copied together with its data, a pasted structure)
 * is given a fresh one instead, and the spans stay with the first.
 *
 * What is persisted is not the counter but a reservation ahead of it ({@link #RESERVE} ids at a time), written
 * before the first id past it is handed out: a chunk is saved when it unloads, the world's data only at the next
 * autosave, so a server killed in between would otherwise restart below an id that a saved machine already holds.
 */
final class IdPool {
	/** How many ids each reservation covers: a restart skips at most this many. */
	static final int RESERVE = 1024;
	/** How many remembered holders the table may grow to before the ones that no longer hold their id are swept out. */
	private static final int SWEEP_FROM = 1024;

	private final boolean detectDuplicates;
	@Nullable
	private final IntConsumer persist;
	/** Ids at or above this are not ones this pool hands out: read from a block's data, they are somebody's NBT. */
	private final int ceiling;
	private int highest;
	/** No id above this has been handed out or written down anywhere this pool knows of. */
	private int reserved;
	/** Which array of ids holds each id.  Weakly, so a machine that vanishes without a word does not pin its entry. */
	private final Map<Integer, WeakReference<int[]>> holders = new HashMap<>();
	private int sweepAt = SWEEP_FROM;

	/**
	 * @param persisted        what was persisted last: no id above it was ever handed out
	 * @param detectDuplicates whether to track holders and re-issue an id held by another machine
	 * @param persist          told each new reservation, before any id under it is handed out; it must be on disk
	 *                         when it returns
	 */
	IdPool(int persisted, boolean detectDuplicates, @Nullable IntConsumer persist) {
		this(persisted, detectDuplicates, persist, Integer.MAX_VALUE);
	}

	/**
	 * @param ceiling the first id that is never real: claimed, it is replaced; adopted, it is ignored.  A counter
	 *                persisted at or above it was pushed there by such an id before this check, and starts again from
	 *                nought - the saved spans are adopted when the world opens and bring it back over every id in use
	 */
	IdPool(int persisted, boolean detectDuplicates, @Nullable IntConsumer persist, int ceiling) {
		this.ceiling = ceiling;
		this.highest = persisted >= ceiling ? 0 : Math.max(0, persisted);
		this.reserved = highest;
		this.detectDuplicates = detectDuplicates;
		this.persist = persist;
	}

	synchronized int highest() {
		return highest;
	}

	/** The value persisted last: an id at or below it may have been handed out before a restart. */
	synchronized int reserved() {
		return reserved;
	}

	/** {@link #claim(int[], IntPredicate)} with nothing known about where an id belongs. */
	synchronized void claim(int[] ids) {
		claim(ids, id -> false);
	}

	/**
	 * Gives every slot that is unassigned (zero or less) a fresh id.  With duplicate detection, a slot whose id is
	 * already held by a different array, repeated earlier in the same one, or {@code foreign} (the saved spans put it
	 * on another machine) is treated as unassigned too.
	 *
	 * Asking again with the same array changes nothing: an array is its own holder.
	 */
	synchronized void claim(int[] ids, IntPredicate foreign) {
		for (int i = 0; i < ids.length; i++) {
			int id = ids[i];
			// one id at the top of the range from a crafted item's NBT would wrap every id after it to negative
			if (id >= ceiling) id = 0;

			if (id > 0 && detectDuplicates && (heldElsewhere(id, ids) || repeatedBefore(ids, i) || foreign.test(id))) id = 0;

			if (id > 0) {
				raise(id);
			} else {
				id = highest + 1;
				raise(id);
				ids[i] = id;
			}

			if (detectDuplicates) hold(id, ids);
		}
	}

	/** An id read back from disk is in use, whether or not its machine is loaded: nothing may hand it out again. */
	synchronized void adopt(int id) {
		if (id > 0 && id < ceiling) raise(id);
	}

	/** The machine is gone: its ids no longer have a holder.  They are not reused. */
	synchronized void release(int[] ids) {
		if (!detectDuplicates) return;

		for (int id : ids) {
			if (id > 0) holders.remove(id);
		}
	}

	/** Forgets who holds what; the counter stays. */
	synchronized void forgetHolders() {
		holders.clear();
	}

	private void raise(int id) {
		if (id <= highest) return;

		highest = id;
		if (id > reserved) {
			reserved = id >= Integer.MAX_VALUE - RESERVE ? Integer.MAX_VALUE : id + RESERVE - 1;
			if (persist != null) persist.accept(reserved);
		}
	}

	private boolean heldElsewhere(int id, int[] ids) {
		WeakReference<int[]> reference = holders.get(id);
		if (reference == null) return false;

		int[] holder = reference.get();
		// a holder that no longer carries the id (it was rewritten in place) does not hold it
		return holder != null && holder != ids && contains(holder, id);
	}

	private static boolean repeatedBefore(int[] ids, int index) {
		for (int j = 0; j < index; j++) {
			if (ids[j] == ids[index]) return true;
		}

		return false;
	}

	private static boolean contains(int[] ids, int id) {
		for (int value : ids) {
			if (value == id) return true;
		}

		return false;
	}

	private void hold(int id, int[] ids) {
		holders.put(id, new WeakReference<>(ids));

		if (holders.size() > sweepAt) {
			holders.entrySet().removeIf(entry -> {
				int[] holder = entry.getValue().get();
				return holder == null || !contains(holder, entry.getKey());
			});
			sweepAt = Math.max(SWEEP_FROM, holders.size() * 2);
		}
	}
}
