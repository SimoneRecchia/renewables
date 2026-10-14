package io.github.simonerecchia.renewables.net.packet;

import io.github.simonerecchia.renewables.grid.WireConnection;

import net.minecraft.network.FriendlyByteBuf;

/**
 * The nine fields of a span as they travel: both fitting ids, the conductor, both block positions, both
 * fitting types and both power roles.  The reels charged for it stay on the server.
 */
final class WireCodec {
	private WireCodec() {
	}

	static void write(FriendlyByteBuf buffer, WireConnection span) {
		buffer.writeInt(span.startInsulatorId());
		buffer.writeInt(span.endInsulatorId());
		buffer.writeUtf(span.wireType());
		buffer.writeBlockPos(span.startBlockPos());
		buffer.writeBlockPos(span.endBlockPos());
		buffer.writeUtf(span.startBlockType());
		buffer.writeUtf(span.endBlockType());
		buffer.writeUtf(span.startPowerType());
		buffer.writeUtf(span.endPowerType());
	}

	static WireConnection read(FriendlyByteBuf buffer) {
		int startId = buffer.readInt();
		int endId = buffer.readInt();
		String wireType = buffer.readUtf();

		return new WireConnection(startId, endId, wireType, buffer.readBlockPos(), buffer.readBlockPos(),
				buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
	}
}
