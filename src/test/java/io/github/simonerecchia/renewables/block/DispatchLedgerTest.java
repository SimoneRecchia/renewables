package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** A controller's hold on a unit is a lease, and what the unit had before survives a restart. */
class DispatchLedgerTest {
	private static final BlockPos CONTROLLER = new BlockPos(0, 64, 0);
	private static final BlockPos OTHER = new BlockPos(100, 64, 0);
	private static final BlockPos INVERTER = new BlockPos(10, 64, 10);

	/** The limit written down is the one the unit had the first time, not whatever the controller set since. */
	@Test
	void theOriginalLimitIsTheFirstOne() {
		DispatchLedger ledger = new DispatchLedger();
		assertTrue(ledger.hold(INVERTER, CONTROLLER, 352.0, 352.0, 1000L));
		assertTrue(ledger.hold(INVERTER, CONTROLLER, 33.7, 352.0, 1001L));
		assertEquals(352.0, ledger.holdOn(INVERTER).originalKw(), 0.0);
	}

	/**
	 * Saved and read back - a server restart - the controller renewing its hold finds the original still there: it
	 * used to write down the 33.7 kW it had set itself, and give that back for ever.
	 */
	@Test
	void theOriginalSurvivesARestart() {
		DispatchLedger before = new DispatchLedger();
		before.hold(INVERTER, CONTROLLER, 352.0, 352.0, 1000L);
		before.hold(INVERTER, CONTROLLER, 33.7, 352.0, 1040L);

		DispatchLedger after = DispatchLedger.load(before.save(new CompoundTag()));
		assertTrue(after.hold(INVERTER, CONTROLLER, 33.7, 352.0, 1041L));
		assertEquals(352.0, after.holdOn(INVERTER).originalKw(), 0.0);
		assertTrue(after.lapsed(1041L).isEmpty());
	}

	/** A hold not renewed lapses after the lease; one let go lapses at once. */
	@Test
	void anUnrenewedHoldLapses() {
		DispatchLedger ledger = new DispatchLedger();
		ledger.hold(INVERTER, CONTROLLER, 110.0, 110.0, 1000L);
		assertTrue(ledger.lapsed(1000L + DispatchLedger.LEASE_TICKS).isEmpty());
		assertEquals(List.of(INVERTER), ledger.lapsed(1001L + DispatchLedger.LEASE_TICKS));

		DispatchLedger released = new DispatchLedger();
		released.hold(INVERTER, CONTROLLER, 110.0, 110.0, 1000L);
		released.letGo(INVERTER, CONTROLLER);
		assertEquals(List.of(INVERTER), released.lapsed(1001L));
		// and the original is still there to give back, whenever the unit is next loaded
		assertEquals(110.0, released.holdOn(INVERTER).originalKw(), 0.0);
	}

	/** A second controller cannot take a unit another is holding, but can once that hold has lapsed - keeping its original. */
	@Test
	void oneControllerAtATime() {
		DispatchLedger ledger = new DispatchLedger();
		ledger.hold(INVERTER, CONTROLLER, 110.0, 110.0, 1000L);
		assertFalse(ledger.hold(INVERTER, OTHER, 55.0, 110.0, 1050L));
		assertEquals(List.of(INVERTER), ledger.heldBy(CONTROLLER));
		assertTrue(ledger.heldBy(OTHER).isEmpty());

		assertTrue(ledger.hold(INVERTER, OTHER, 55.0, 110.0, 1000L + DispatchLedger.LEASE_TICKS + 1));
		assertEquals(110.0, ledger.holdOn(INVERTER).originalKw(), 0.0);
		// and letting go is the holder's to do
		ledger.letGo(INVERTER, CONTROLLER);
		assertTrue(ledger.lapsed(1000L + DispatchLedger.LEASE_TICKS + 2).isEmpty());
	}

	@Test
	void aForgottenUnitIsGone() {
		DispatchLedger ledger = new DispatchLedger();
		ledger.hold(INVERTER, CONTROLLER, 110.0, 110.0, 1000L);
		ledger.forget(INVERTER);
		assertNull(ledger.holdOn(INVERTER));
	}
}
