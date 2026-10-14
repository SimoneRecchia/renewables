package io.github.simonerecchia.renewables.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SpanStoreTest {
	private static WireConnection span(int a, int b) {
		return span(a, b, 4);
	}

	private static WireConnection span(int a, int b, int charged) {
		return new WireConnection(a, b, "mv_conductor", new BlockPos(a, 64, 0), new BlockPos(b, 64, 0),
				"utility_pole", "power_box", "bidirectional", "input", charged);
	}

	@Test
	void aPairIsJoinedInEitherOrder() {
		SpanStore store = new SpanStore();
		assertTrue(store.add(span(1, 2)));

		assertTrue(store.joined(1, 2));
		assertTrue(store.joined(2, 1));
		assertFalse(store.joined(1, 3));
	}

	@Test
	void theSamePairCannotBeAddedTwiceInEitherOrder() {
		SpanStore store = new SpanStore();
		assertTrue(store.add(span(1, 2)));

		assertFalse(store.add(span(1, 2)));
		assertFalse(store.add(span(2, 1)));
		assertEquals(1, store.size());
	}

	@Test
	void aFittingIsNeverJoinedToItselfAndAnIdIsPositive() {
		SpanStore store = new SpanStore();

		assertFalse(store.add(span(5, 5)));
		assertFalse(store.add(span(0, 5)));
		assertFalse(store.add(span(5, -1)));
		assertTrue(store.isEmpty());
	}

	@Test
	void countsSpansPerFitting() {
		SpanStore store = new SpanStore();
		store.add(span(1, 2));
		store.add(span(1, 3));
		store.add(span(4, 1));

		assertEquals(3, store.countAt(1));
		assertEquals(1, store.countAt(2));
		assertEquals(0, store.countAt(9));
	}

	@Test
	void removingAMachinesIdsRemovesEverySpanOnThemAndReturnsThem() {
		SpanStore store = new SpanStore();
		store.add(span(1, 2));
		store.add(span(3, 1));
		store.add(span(4, 5));

		List<WireConnection> removed = store.removeAttachedTo(List.of(1));

		assertEquals(2, removed.size());
		assertEquals(1, store.size());
		assertFalse(store.joined(1, 2));
		assertTrue(store.joined(4, 5));
		assertEquals(0, store.countAt(1));
		assertEquals(0, store.countAt(2));
		assertEquals(1, store.countAt(4));
	}

	@Test
	void aSpanBetweenTwoRemovedIdsIsReturnedOnce() {
		SpanStore store = new SpanStore();
		store.add(span(1, 2));

		assertEquals(1, store.removeAttachedTo(List.of(1, 2)).size());
		assertTrue(store.isEmpty());
	}

	/**
	 * A machine copied with its data carries the original's ids.  Taking the copy out removes the spans that put
	 * those ids at the copy's block, and none of the original's.
	 */
	@Test
	void removingAtAPositionLeavesTheSameIdsElsewhere() {
		SpanStore store = new SpanStore();
		BlockPos original = new BlockPos(3205, -60, 5);
		BlockPos copy = new BlockPos(1604, -60, 4);
		store.add(new WireConnection(33, 25, "mv_conductor", original, new BlockPos(1602, -60, 2),
				"utility_pole", "utility_pole", "bidirectional", "bidirectional", 3));
		store.add(new WireConnection(26, 34, "mv_conductor", new BlockPos(1600, -60, 0), copy,
				"utility_pole", "utility_pole", "bidirectional", "bidirectional", 1));

		List<WireConnection> removed = store.removeAttachedAt(copy, List.of(33, 34));

		assertEquals(1, removed.size());
		assertEquals(26, removed.get(0).startInsulatorId());
		assertTrue(store.joined(33, 25));
	}

	@Test
	void anIdIsWhereItsSpansPutIt() {
		SpanStore store = new SpanStore();
		store.add(span(1, 2));
		store.add(span(3, 1));

		assertEquals(java.util.Set.of(new BlockPos(1, 64, 0)), store.endsOf(1));
		assertTrue(store.endsOf(9).isEmpty());
	}

	@Test
	void removingAnUnknownIdDoesNothing() {
		SpanStore store = new SpanStore();
		store.add(span(1, 2));

		assertTrue(store.removeAttachedTo(List.of(77)).isEmpty());
		assertEquals(1, store.size());
	}

	@Test
	void theRefundIsTheChargedCountExactlyAsSaved() {
		SpanStore store = new SpanStore();
		store.add(span(1, 2, 10));
		store.add(span(1, 3, 0));

		List<WireConnection> removed = store.removeAttachedTo(List.of(1));

		assertEquals(10, removed.stream().filter(s -> s.endInsulatorId() == 2).findFirst().orElseThrow().chargedItems());
		assertEquals(0, removed.stream().filter(s -> s.endInsulatorId() == 3).findFirst().orElseThrow().chargedItems());
	}

	// ---- saving ----

	@Test
	void spansSurviveAWriteAndRead() {
		SpanStore store = new SpanStore();
		store.add(span(1, 2, 10));
		store.add(span(7, 3, 0));

		CompoundTag tag = SpanCodec.write(store.all(), new CompoundTag());
		List<WireConnection> read = SpanCodec.read(tag, message -> {
			throw new AssertionError(message);
		});

		assertEquals(store.all(), read);
	}

	@Test
	void anUnknownConductorAndAnUnknownRoleDoNotBreakLoading() {
		WireConnection odd = new WireConnection(1, 2, "mystery_conductor", new BlockPos(0, 0, 0), new BlockPos(5, 0, 0),
				"utility_pole", "utility_pole", "sideways", "output", 3);

		List<WireConnection> read = SpanCodec.read(SpanCodec.write(List.of(odd), new CompoundTag()), message -> {
			throw new AssertionError(message);
		});

		assertEquals(1, read.size());
		assertEquals("mystery_conductor", read.get(0).wireType());
		assertEquals("bidirectional", read.get(0).startPowerType());
		assertEquals(3, read.get(0).chargedItems());
	}

	@Test
	void aDamagedSpanIsSkippedAndTheOthersAreKept() {
		CompoundTag tag = SpanCodec.write(List.of(span(1, 2), span(3, 4)), new CompoundTag());
		tag.getList("spans", 10).getCompound(0).remove("startPos");
		List<String> problems = new java.util.ArrayList<>();

		List<WireConnection> read = SpanCodec.read(tag, problems::add);

		assertEquals(1, read.size());
		assertEquals(3, read.get(0).startInsulatorId());
		assertEquals(1, problems.size());
	}

	@Test
	void aSpanWithBadIdsIsSkipped() {
		CompoundTag tag = SpanCodec.write(List.of(span(1, 2)), new CompoundTag());
		tag.getList("spans", 10).getCompound(0).putInt("end", 1);
		List<String> problems = new java.util.ArrayList<>();

		assertTrue(SpanCodec.read(tag, problems::add).isEmpty());
		assertEquals(1, problems.size());
	}

	@Test
	void aNewerVersionIsReadAsFarAsItIsUnderstood() {
		CompoundTag tag = SpanCodec.write(List.of(span(1, 2)), new CompoundTag());
		tag.putInt("version", SpanCodec.VERSION + 1);
		List<String> problems = new java.util.ArrayList<>();

		assertEquals(1, SpanCodec.read(tag, problems::add).size());
		assertEquals(1, problems.size());
	}

	@Test
	void anEmptyOrForeignTagReadsAsNoSpans() {
		assertTrue(SpanCodec.read(new CompoundTag(), message -> {
		}).isEmpty());
	}
}
