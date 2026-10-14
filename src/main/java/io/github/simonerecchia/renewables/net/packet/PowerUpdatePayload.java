package io.github.simonerecchia.renewables.net.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server to client: what the grid is delivering to the machine at a position, in kW.
 *
 * Sent to the players tracking that chunk, and only when the value moved (or as a slow heartbeat); a
 * position that drops out of the network gets one last message carrying zero.
 */
public record PowerUpdatePayload(BlockPos blockPos, double powerKw) {
	public static void write(PowerUpdatePayload msg, FriendlyByteBuf buffer) {
		buffer.writeBlockPos(msg.blockPos);
		buffer.writeDouble(msg.powerKw);
	}

	public static PowerUpdatePayload read(FriendlyByteBuf buffer) {
		return new PowerUpdatePayload(buffer.readBlockPos(), buffer.readDouble());
	}
}
