package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdPoolTest {
	@Test
	void unassignedSlotsGetFreshIncreasingIds() {
		IdPool pool = new IdPool(0, true, null);
		int[] ids = new int[3];

		pool.claim(ids);

		assertArrayEquals(new int[] {1, 2, 3}, ids);
	}

	@Test
	void aNegativeIdIsNotAssigned() {
		IdPool pool = new IdPool(0, true, null);
		int[] ids = {-1, 0};

		pool.claim(ids);

		assertArrayEquals(new int[] {1, 2}, ids);
	}

	@Test
	void anAssignedSlotIsLeftAlone() {
		IdPool pool = new IdPool(0, true, null);
		int[] ids = {7, 0};

		pool.adopt(7);
		pool.claim(ids);

		assertArrayEquals(new int[] {7, 8}, ids);
	}

	@Test
	void adoptingRaisesTheCounterSoAnIdFromDiskIsNeverReissued() {
		IdPool pool = new IdPool(0, true, null);
		pool.adopt(41);
		pool.adopt(12);

		int[] ids = new int[1];
		pool.claim(ids);

		assertEquals(42, ids[0]);
	}

	/** The restart bug: a counter that started from 1 again would hand out ids that saved spans already name. */
	@Test
	void theCounterSurvivesARestartThroughItsSavedValue() {
		AtomicInteger saved = new AtomicInteger();
		IdPool before = new IdPool(0, true, saved::set);
		int[] ids = new int[5];
		before.claim(ids);
		assertEquals(5, saved.get());

		// a new process: a new pool, seeded from what was saved
		IdPool after = new IdPool(saved.get(), true, saved::set);
		int[] fresh = new int[1];
		after.claim(fresh);

		assertEquals(6, fresh[0]);
		assertEquals(6, saved.get());
	}

	@Test
	void theCounterNeverMovesBack() {
		IdPool pool = new IdPool(100, true, null);
		pool.adopt(5);
		int[] ids = new int[1];
		pool.claim(ids);

		assertEquals(101, ids[0]);
		assertEquals(101, pool.highest());
	}

	@Test
	void releasingDoesNotMakeAnIdAvailableAgain() {
		IdPool pool = new IdPool(0, true, null);
		int[] first = new int[2];
		pool.claim(first);
		pool.release(first.clone());

		int[] second = new int[2];
		pool.claim(second);

		Set<Integer> all = new HashSet<>();
		for (int id : first) all.add(id);
		for (int id : second) all.add(id);
		assertEquals(4, all.size());
	}

	@Test
	void askingTwiceWithTheSameArrayChangesNothing() {
		IdPool pool = new IdPool(0, true, null);
		int[] ids = new int[2];

		pool.claim(ids);
		int[] once = ids.clone();
		pool.claim(ids);

		assertArrayEquals(once, ids);
	}

	/** A block copied together with its data: the second machine claims an id the first already holds. */
	@Test
	void aSecondMachineClaimingAnIdInUseIsGivenANewOneAndTheFirstKeepsIt() {
		IdPool pool = new IdPool(0, true, null);
		int[] original = {0};
		pool.claim(original);
		int id = original[0];

		int[] copy = {id};
		pool.claim(copy);

		assertNotEquals(id, copy[0]);
		assertEquals(id, original[0]);
		assertTrue(copy[0] > id);
	}

	@Test
	void anIdReleasedByItsMachineCanBeLoadedAgainByTheSameOne() {
		IdPool pool = new IdPool(0, true, null);
		int[] ids = new int[2];
		pool.claim(ids);
		int[] saved = ids.clone();

		// the chunk unloads (release) and loads again: a new array read from disk carries the saved ids
		pool.release(saved);
		int[] reloaded = saved.clone();
		pool.claim(reloaded);

		assertArrayEquals(saved, reloaded);
	}

	@Test
	void anIdRepeatedWithinOneMachineIsReissued() {
		IdPool pool = new IdPool(0, true, null);
		int[] ids = {3, 3};

		pool.adopt(3);
		pool.claim(ids);

		assertEquals(3, ids[0]);
		assertNotEquals(3, ids[1]);
	}

	@Test
	void aHolderThatNoLongerCarriesTheIdDoesNotHoldIt() {
		IdPool pool = new IdPool(0, true, null);
		int[] first = {0};
		pool.claim(first);
		int id = first[0];

		// rewritten in place, as a constructor's claim is when the saved ids are then read over it
		first[0] = 99;
		pool.adopt(99);

		int[] other = {id};
		pool.claim(other);
		assertEquals(id, other[0]);
	}

	@Test
	void withoutDuplicateTrackingAClaimOnlyFillsTheGaps() {
		IdPool pool = new IdPool(1_000, false, null);
		int[] a = {5, 0};
		int[] b = {5, 0};

		pool.claim(a);
		pool.claim(b);

		assertEquals(5, a[0]);
		assertEquals(5, b[0]);
		assertEquals(1_001, a[1]);
		assertEquals(1_002, b[1]);
	}

	@Test
	void manyMachinesGetDistinctIds() {
		IdPool pool = new IdPool(0, true, null);
		Set<Integer> seen = new HashSet<>();
		for (int machine = 0; machine < 5_000; machine++) {
			int[] ids = new int[8];
			pool.claim(ids);
			for (int id : ids) assertTrue(seen.add(id));
		}
	}
}
