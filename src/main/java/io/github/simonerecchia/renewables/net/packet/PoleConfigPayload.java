package io.github.simonerecchia.renewables.net.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client to server: the utility pole configuration screen's "Done".
 *
 * Offsets are in blocks, local to the pole; yaw and pitch are in <b>radians</b>.  Nothing here is trusted: the
 * server drops non-finite values and clamps the rest to {@link #MAX_OFFSET}, {@link #MAX_YAW} and
 * {@link #MAX_PITCH}.
 */
public record PoleConfigPayload(BlockPos blockPos, float offsetX, float offsetY, float offsetZ, float yaw, float pitch) {
	/** How far a pole may be shifted off its block, per axis, in blocks. */
	public static final float MAX_OFFSET = 1.0f;
	/** Half a turn either way. */
	public static final float MAX_YAW = (float) Math.PI;
	/** A leaning pole, not a fallen one: 45 degrees. */
	public static final float MAX_PITCH = (float) (Math.PI / 4.0);

	public static void write(PoleConfigPayload msg, FriendlyByteBuf buffer) {
		buffer.writeBlockPos(msg.blockPos);
		buffer.writeFloat(msg.offsetX);
		buffer.writeFloat(msg.offsetY);
		buffer.writeFloat(msg.offsetZ);
		buffer.writeFloat(msg.yaw);
		buffer.writeFloat(msg.pitch);
	}

	public static PoleConfigPayload read(FriendlyByteBuf buffer) {
		return new PoleConfigPayload(buffer.readBlockPos(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
				buffer.readFloat(), buffer.readFloat());
	}
}
