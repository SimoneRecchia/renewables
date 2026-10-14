package io.github.simonerecchia.renewables.client.wire;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The first fitting of a connection, picked and waiting for the second: where it is and what the server needs to be
 * told about it.
 *
 * Two of these are the same fitting when they are the same machine block and the same index in it, whatever the
 * point the click happened to land on: that is what makes a second click on the first fitting a cancel.
 */
@OnlyIn(Dist.CLIENT)
public final class PendingLink {
	private final BlockPos pos;
	private final int index;
	private final int insulatorId;
	private final String fittingType;
	private final String powerType;
	private final Vec3 point;

	public PendingLink(BlockPos pos, int index, int insulatorId, String fittingType, String powerType, Vec3 point) {
		this.pos = pos.immutable();
		this.index = index;
		this.insulatorId = insulatorId;
		this.fittingType = fittingType;
		this.powerType = powerType;
		this.point = point;
	}

	/** The machine's block. */
	public BlockPos pos() {
		return pos;
	}

	/** Which of the machine's fittings, in the order of its model definition. */
	public int index() {
		return index;
	}

	/** The fitting's persistent id, as the server assigned it. */
	public int insulatorId() {
		return insulatorId;
	}

	/** The saved name of the kind of machine ({@code FittingTypes}). */
	public String fittingType() {
		return fittingType;
	}

	/** {@code input}, {@code output} or {@code bidirectional}. */
	public String powerType() {
		return powerType;
	}

	/** Where the preview starts when it is not following the live anchor: the click's point. */
	public Vec3 point() {
		return point;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof PendingLink that && index == that.index && pos.equals(that.pos);
	}

	@Override
	public int hashCode() {
		return Objects.hash(pos, index);
	}
}
