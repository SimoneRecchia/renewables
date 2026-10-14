package io.github.simonerecchia.renewables.net.packet;

import io.github.simonerecchia.renewables.grid.WireConnection;

import net.minecraft.network.FriendlyByteBuf;

/** Server to client: one span was strung ({@code created}) or taken down, in the level the player is in. */
public record SpanChangedPayload(WireConnection span, boolean created) {
	public static void write(SpanChangedPayload msg, FriendlyByteBuf buffer) {
		WireCodec.write(buffer, msg.span);
		buffer.writeBoolean(msg.created);
	}

	public static SpanChangedPayload read(FriendlyByteBuf buffer) {
		return new SpanChangedPayload(WireCodec.read(buffer), buffer.readBoolean());
	}
}
