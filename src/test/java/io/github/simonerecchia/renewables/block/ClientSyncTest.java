package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** The throttle's two gates: half a second between looks, and nothing sent that the clients already have. */
class ClientSyncTest {
	private static CompoundTag tag(double angle) {
		CompoundTag tag = new CompoundTag();
		tag.putDouble("angle", angle);
		tag.putString("mode", "track");
		return tag;
	}

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
}
