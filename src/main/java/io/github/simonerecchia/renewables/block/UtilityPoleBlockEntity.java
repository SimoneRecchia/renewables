package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.model.ExtraPose;
import io.github.simonerecchia.renewables.net.packet.PoleConfigPayload;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The pole's eight fittings and its player-set offset, yaw and pitch.
 *
 * The three numbers move the pole as it is <em>drawn</em> and as its wires end; they do not move its cells, so a
 * pole shifted a block still collides where it was placed (the limits in {@link PoleConfigPayload} are what keep
 * the two near each other).  The one transform is {@link ExtraPose#toWorld}: the renderer draws with it, the
 * picker aims with it and {@link #computeAnchor} ends the wires with it, so what is drawn, what is pointed at and
 * where the conductor stops cannot disagree.
 */
public class UtilityPoleBlockEntity extends MachineBlockEntity implements ExtraPose {
	private float offsetX;
	private float offsetY;
	private float offsetZ;
	/** Radians. */
	private float yaw;
	/** Radians. */
	private float pitch;

	public UtilityPoleBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.UTILITY_POLE.get(), pos, state);
	}

	/**
	 * Sets the whole configuration, as one operation: one recount of the anchors, one save, one update to the
	 * clients.  Offsets are blocks, angles radians.
	 *
	 * The packet handler has already refused non-finite numbers and limited the rest; this limits again, because a
	 * pole configured from anywhere else (a command, another mod) must not be able to put its wires outside the
	 * range the collision still roughly covers.
	 */
	public void applyConfig(float offsetX, float offsetY, float offsetZ, float yaw, float pitch) {
		this.offsetX = limit(offsetX, PoleConfigPayload.MAX_OFFSET);
		this.offsetY = limit(offsetY, PoleConfigPayload.MAX_OFFSET);
		this.offsetZ = limit(offsetZ, PoleConfigPayload.MAX_OFFSET);
		this.yaw = limit(yaw, PoleConfigPayload.MAX_YAW);
		this.pitch = limit(pitch, PoleConfigPayload.MAX_PITCH);

		refreshAnchors();
		setChanged();
		if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	/** A finite number within +-{@code bound}; anything else is zero, which is the pole as it was placed. */
	private static float limit(float value, float bound) {
		return Float.isFinite(value) ? Mth.clamp(value, -bound, bound) : 0.0f;
	}

	public float offsetX() {
		return offsetX;
	}

	public float offsetY() {
		return offsetY;
	}

	public float offsetZ() {
		return offsetZ;
	}

	public float yawRadians() {
		return yaw;
	}

	public float pitchRadians() {
		return pitch;
	}

	// ---- the transform (ExtraPose) ----

	@Override
	public Vec3 poseOffset() {
		return new Vec3(offsetX, offsetY, offsetZ);
	}

	@Override
	public float poseYawDegrees() {
		return (float) Math.toDegrees(yaw);
	}

	@Override
	public float posePitchDegrees() {
		return (float) Math.toDegrees(pitch);
	}

	@Override
	public String fittingType() {
		return FittingTypes.UTILITY_POLE;
	}

	/** A pole passes power on to poles, a kiosk and a run laid on the ground. */
	@Override
	public boolean feeds(InsulatorHost other) {
		return other instanceof UtilityPoleBlockEntity || other instanceof PowerBoxBlockEntity
				|| other instanceof GroundConductorBlockEntity;
	}

	/** Street bundle or medium voltage, never transmission: 1.7 m between sub-conductors and 3.5 m of clearance want a tower. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() != ConductorSpec.VoltageClass.HIGH;
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putFloat("offsetX", offsetX);
		tag.putFloat("offsetY", offsetY);
		tag.putFloat("offsetZ", offsetZ);
		tag.putFloat("yaw", yaw);
		tag.putFloat("pitch", pitch);
	}

	/** A saved pole is read through the same limits: a hand-edited file must not do what a packet may not. */
	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		offsetX = limit(tag.getFloat("offsetX"), PoleConfigPayload.MAX_OFFSET);
		offsetY = limit(tag.getFloat("offsetY"), PoleConfigPayload.MAX_OFFSET);
		offsetZ = limit(tag.getFloat("offsetZ"), PoleConfigPayload.MAX_OFFSET);
		yaw = limit(tag.getFloat("yaw"), PoleConfigPayload.MAX_YAW);
		pitch = limit(tag.getFloat("pitch"), PoleConfigPayload.MAX_PITCH);
	}
}
