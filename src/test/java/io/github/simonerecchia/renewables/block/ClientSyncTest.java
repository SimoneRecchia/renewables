package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The throttle's gates: a look every half second, nothing sent that the clients already have, and for a row only what
 * moved - a state at once, a reading past its deadband at most once a second - which a client rebuilds the whole from.
 */
class ClientSyncTest {
	private static CompoundTag tag(double angle) {
		CompoundTag tag = new CompoundTag();
		tag.putDouble("angle", angle);
		tag.putString("mode", "track");
		return tag;
	}

	private static CompoundTag reading(double poa, double rotation, String mode) {
		CompoundTag tag = new CompoundTag();
		tag.putDouble("poaBeam", poa);
		tag.putDouble("rotation", rotation);
		tag.putString("trackerMode", mode);
		tag.putLong("collector", 42L);
		return tag;
	}

	// ---- the gates every machine has

	@Test
	void aLookIsDueEveryTenTicks() {
		ClientSync sync = new ClientSync();
		assertTrue(sync.due(100));
		assertFalse(sync.due(105));
		assertFalse(sync.due(109));
		assertTrue(sync.due(110));
	}

	/** A machine standing still was sent, and its chunk marked dirty, twice a second for ever; now once. */
	@Test
	void aStateThatHasNotMovedIsNotSentAgain() {
		ClientSync sync = new ClientSync();
		assertTrue(sync.changed(tag(12.5)));
		assertFalse(sync.changed(tag(12.5)));
		assertFalse(sync.changed(tag(12.5)));
		assertTrue(sync.changed(tag(13.0)));
		assertFalse(sync.changed(tag(13.0)));
	}

	// ---- a row: only what moved

	/** The first update is the whole tag, which the client takes as it is. */
	@Test
	void theFirstUpdateIsEverything() {
		ClientSync server = ClientSync.deltas();
		CompoundTag first = server.changes(reading(800.0, 10.0, "AUTO"), ClientSync.DEADBAND);
		assertFalse(first.contains(ClientSync.DELTA));
		assertEquals(4, first.getAllKeys().size());
	}

	/** Readings that moved less than half a percent are not sent, and nothing at all goes when nothing moved. */
	@Test
	void smallMovesWaitAndNoMovesSendNothing() {
		ClientSync server = ClientSync.deltas();
		server.changes(reading(800.0, 10.0, "AUTO"), ClientSync.DEADBAND);

		assertTrue(server.changes(reading(800.0, 10.0, "AUTO"), ClientSync.DEADBAND).isEmpty());
		assertTrue(server.changes(reading(802.0, 10.02, "AUTO"), ClientSync.DEADBAND).isEmpty());

		// against what was last told, not the last tick: creeping past the band sends it
		CompoundTag moved = server.changes(reading(805.0, 10.02, "AUTO"), ClientSync.DEADBAND);
		assertTrue(moved.getBoolean(ClientSync.DELTA));
		assertEquals(805.0, moved.getDouble("poaBeam"), 0.0);
		assertFalse(moved.contains("rotation"));
		assertFalse(moved.contains("collector"));
	}

	/** A reading that moved waits for its second; a state that changed goes at the next look, readings riding with it. */
	@Test
	void statesGoAtOnceReadingsAtMostOnceASecond() {
		ClientSync server = ClientSync.deltas();
		server.changes(reading(800.0, 10.0, "AUTO"), ClientSync.DEADBAND);

		assertTrue(server.changes(reading(850.0, 10.0, "AUTO"), ClientSync.DEADBAND, false).isEmpty());

		CompoundTag stowed = server.changes(reading(850.0, 10.0, "STOW"), ClientSync.DEADBAND, false);
		assertEquals("STOW", stowed.getString("trackerMode"));
		assertEquals(850.0, stowed.getDouble("poaBeam"), 0.0);

		// and the second itself: readings are due again only a second after a delta that carried them
		assertTrue(server.readingsDue(1000L));
		server.readingsSent(1000L);
		assertFalse(server.readingsDue(1010L));
		assertFalse(server.readingsDue(1019L));
		assertTrue(server.readingsDue(1020L));
	}

	/** A setting is sent the moment it changes, and an exact update sends any change at all. */
	@Test
	void settingsAndExactUpdatesGoAtOnce() {
		ClientSync server = ClientSync.deltas();
		server.changes(reading(800.0, 10.0, "AUTO"), ClientSync.DEADBAND);

		CompoundTag mode = server.changes(reading(800.0, 10.0, "MANUAL"), ClientSync.DEADBAND);
		assertEquals("MANUAL", mode.getString("trackerMode"));

		CompoundTag exact = server.changes(reading(800.5, 10.0, "MANUAL"), 0.0);
		assertEquals(800.5, exact.getDouble("poaBeam"), 0.0);
	}

	/** A key that is gone is named, and the client forgets it: an array let go by its inverter is not wired any more. */
	@Test
	void aRemovedKeyIsRemovedOnTheClient() {
		ClientSync server = ClientSync.deltas();
		ClientSync client = ClientSync.deltas();
		client.heard(server.changes(reading(800.0, 10.0, "AUTO"), ClientSync.DEADBAND));

		CompoundTag unwired = reading(800.0, 10.0, "AUTO");
		unwired.remove("collector");
		CompoundTag whole = client.heard(server.changes(unwired, ClientSync.DEADBAND, false));
		assertFalse(whole.contains("collector"));
		assertEquals(800.0, whole.getDouble("poaBeam"), 0.0);
		assertEquals("AUTO", whole.getString("trackerMode"));
	}

	/** Deltas laid over the whole tag give what the server has, to within the deadband. */
	@Test
	void theClientRebuildsTheWholeState() {
		ClientSync server = ClientSync.deltas();
		ClientSync client = ClientSync.deltas();
		client.heard(server.changes(reading(100.0, -40.0, "AUTO"), ClientSync.DEADBAND));

		CompoundTag whole = null;
		for (int tick = 1; tick <= 200; tick++) {
			CompoundTag update = server.changes(reading(100.0 + 3.0 * tick, -40.0 + 0.4 * tick, "AUTO"), ClientSync.DEADBAND);
			if (!update.isEmpty()) whole = client.heard(update);
		}

		assertEquals(700.0, whole.getDouble("poaBeam"), 700.0 * ClientSync.DEADBAND);
		assertEquals(40.0, whole.getDouble("rotation"), 40.0 * ClientSync.DEADBAND);
		assertEquals(42L, whole.getLong("collector"));
	}
}
