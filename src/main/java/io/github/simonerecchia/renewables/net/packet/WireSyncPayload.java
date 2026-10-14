package io.github.simonerecchia.renewables.net.packet;

import io.github.simonerecchia.renewables.grid.WireConnection;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server to client: the spans of the level the player is in.
 *
 * One packet cannot carry thousands of them (a play packet tops out at 1 MiB), so the server sends them in
 * batches: the first has {@code replace} set and swaps out whatever the client knew, the rest add to it.
 */
public record WireSyncPayload(boolean replace, List<WireConnection> spans) {
	/** How many spans one packet carries.  A span is a few dozen bytes, so this stays far under the cap. */
	public static final int BATCH = 500;

	public static void write(WireSyncPayload msg, FriendlyByteBuf buffer) {
		buffer.writeBoolean(msg.replace);
		buffer.writeVarInt(msg.spans.size());
		for (WireConnection span : msg.spans) {
			WireCodec.write(buffer, span);
		}
	}

	public static WireSyncPayload read(FriendlyByteBuf buffer) {
		boolean replace = buffer.readBoolean();
		int count = buffer.readVarInt();
		// the count is the sender's word: a batch is never bigger than BATCH, so do not allocate for more
		if (count < 0 || count > BATCH) throw new IllegalArgumentException("span batch of " + count);

		List<WireConnection> spans = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			spans.add(WireCodec.read(buffer));
		}

		return new WireSyncPayload(replace, spans);
	}
}
