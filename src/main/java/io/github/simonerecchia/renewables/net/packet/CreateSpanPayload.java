package io.github.simonerecchia.renewables.net.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client to server: "join these two fittings with the conductor in my hand".
 *
 * The reel is deliberately not a field: the server reads it from the player's hands.  Every field here is a
 * claim the server checks against its own block entities.
 */
public record CreateSpanPayload(int startId, int endId, BlockPos startPos, BlockPos endPos,
		String startFittingType, String endFittingType, String startPowerType, String endPowerType) {
	/** Fitting types and power roles are short names; anything longer is not one. */
	public static final int MAX_NAME = 64;

	public static void write(CreateSpanPayload msg, FriendlyByteBuf buffer) {
		buffer.writeInt(msg.startId);
		buffer.writeInt(msg.endId);
		buffer.writeBlockPos(msg.startPos);
		buffer.writeBlockPos(msg.endPos);
		buffer.writeUtf(msg.startFittingType, MAX_NAME);
		buffer.writeUtf(msg.endFittingType, MAX_NAME);
		buffer.writeUtf(msg.startPowerType, MAX_NAME);
		buffer.writeUtf(msg.endPowerType, MAX_NAME);
	}

	public static CreateSpanPayload read(FriendlyByteBuf buffer) {
		return new CreateSpanPayload(buffer.readInt(), buffer.readInt(), buffer.readBlockPos(), buffer.readBlockPos(),
				buffer.readUtf(MAX_NAME), buffer.readUtf(MAX_NAME), buffer.readUtf(MAX_NAME), buffer.readUtf(MAX_NAME));
	}
}
