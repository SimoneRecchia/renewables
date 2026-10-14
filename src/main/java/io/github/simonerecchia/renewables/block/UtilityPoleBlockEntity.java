package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The pole's eight fittings and its player-set offset, yaw and pitch.
 *
 * STUB: owner B (grid-blocks.md sections 1.5 and 3).  What is here is the contract the network handler calls:
 * {@link #applyConfig} takes all five values at once, so one change is one save and one update to the clients.
 * The real class must also move the wire anchors with the same transform the renderer draws with
 * (offset, then yaw, then pitch) - {@link #computeAnchor} currently ignores them.
 */
public class UtilityPoleBlockEntity extends MachineBlockEntity {
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
	 * Sets the whole configuration.  The values have already been checked finite and within limits by the packet
	 * handler; offsets are blocks, angles radians.
	 */
	public void applyConfig(float offsetX, float offsetY, float offsetZ, float yaw, float pitch) {
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.offsetZ = offsetZ;
		this.yaw = yaw;
		this.pitch = pitch;

		refreshAnchors();
		setChanged();
		if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
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

	/** Street bundle or medium voltage, never transmission. */
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

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		offsetX = tag.getFloat("offsetX");
		offsetY = tag.getFloat("offsetY");
		offsetZ = tag.getFloat("offsetZ");
		yaw = tag.getFloat("yaw");
		pitch = tag.getFloat("pitch");
	}
}
