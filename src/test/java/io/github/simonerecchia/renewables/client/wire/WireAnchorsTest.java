package io.github.simonerecchia.renewables.client.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.simonerecchia.renewables.grid.InsulatorHost;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An id can move from one machine to another (the server renumbers a duplicate, a machine is copied with its data);
 * the index entry that still names the old machine must not hide the span's end at the new one.
 */
class WireAnchorsTest {
	/** A machine with one fitting whose id can change, as an update from the server changes it. */
	private static final class Machine implements InsulatorHost {
		int id;
		final Vec3 anchor;

		Machine(int id, Vec3 anchor) {
			this.id = id;
			this.anchor = anchor;
		}

		@Override
		public int[] getInsulatorIds() {
			return new int[] {id};
		}

		@Override
		public Vec3 getWirePosition(int index) {
			return index == 0 ? anchor : null;
		}

		@Override
		public String fittingType() {
			return "utility_pole";
		}

		@Override
		public boolean feeds(InsulatorHost other) {
			return false;
		}

		@Override
		public double getCurrentPower() {
			return 0.0;
		}
	}

	@BeforeEach
	@AfterEach
	void empty() {
		InsulatorIndex.clear();
	}

	@Test
	void aStaleEntryFallsBackToTheMachineWhereTheSpanSaysItIs() {
		Machine old = new Machine(7, new Vec3(1, 2, 3));
		Machine now = new Machine(7, new Vec3(10, 20, 30));
		InsulatorIndex.register(old);
		// the server gave the old machine a new number; nothing told the index
		old.id = 9;

		assertEquals(now.anchor, WireAnchors.anchorOf(7, () -> now));
		assertSame(now, InsulatorIndex.find(7), "the index now names the machine that holds the id");
	}

	@Test
	void aStaleEntryWithNothingAtThePositionIsDroppedAndTheSpanWaits() {
		Machine old = new Machine(7, new Vec3(1, 2, 3));
		InsulatorIndex.register(old);
		old.id = 9;

		assertNull(WireAnchors.anchorOf(7, () -> null));
		assertNull(InsulatorIndex.find(7));
	}

	@Test
	void aCurrentEntryIsAnsweredWithoutAskingThePosition() {
		Machine machine = new Machine(7, new Vec3(1, 2, 3));
		InsulatorIndex.register(machine);

		assertEquals(machine.anchor, WireAnchors.anchorOf(7, () -> {
			throw new AssertionError("the index already knew");
		}));
	}

	@Test
	void forgettingAMachinesIdsLeavesAnIdThatHasGoneToAnother() {
		Machine copy = new Machine(7, new Vec3(1, 2, 3));
		Machine original = new Machine(7, new Vec3(10, 20, 30));
		InsulatorIndex.register(original);
		InsulatorIndex.register(copy);

		// the original goes, or gives the id up: the copy registered since keeps it
		InsulatorIndex.unregister(original, new int[] {7});
		assertSame(copy, InsulatorIndex.find(7));

		InsulatorIndex.unregister(copy, new int[] {7});
		assertNull(InsulatorIndex.find(7));
	}
}
