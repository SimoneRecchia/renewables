package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.WorldConditions;
import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.Dispatchable;
import io.github.simonerecchia.renewables.api.power.RedstoneMode;
import io.github.simonerecchia.renewables.api.power.Telemetry;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.catalog.TurbineCatalog;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A wind turbine: its telemetry, its control state and its single wire fitting.
 *
 * STUB: owner W (wind.md).  The physics is not here: every reading is a calm zero.  What is real is the
 * shape of the contract: the methods the panel, the computer peripheral and the control packets call, the
 * four stop reasons, and the accessors the renderer reads ({@link #towerHeight}, {@link #yawDegrees},
 * {@link #rotorAngleDegrees}, {@link #bladePitchDegrees}, {@link #isStopped}).
 */
public class WindTurbineBlockEntity extends MachineBlockEntity implements Dispatchable {
	private boolean stoppedByPlayer;
	private boolean stoppedByComputer;
	private RedstoneMode redstoneMode = RedstoneMode.DISABLED;
	private double activePowerLimitKw = Double.NaN;

	public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.WIND_TURBINE.get(), pos, state);
	}

	public TurbineSpec spec() {
		if (getBlockState().getBlock() instanceof WindTurbineBlock turbine) return turbine.spec();

		return TurbineCatalog.fallback();
	}

	// ---- what the renderer reads (docs/ARCHITECTURE.md section 3.7) ----

	/** Tower segments under the machine. */
	public int getTowerSegments() {
		return level == null ? 0 : TurbineTowerBlock.countBelow(level, worldPosition);
	}

	/** Hub height above the foot of the tower, in blocks. */
	public int towerHeight() {
		return getTowerSegments();
	}

	/** Which way the nacelle faces, in degrees, interpolated for the frame. */
	public float yawDegrees(float partialTick) {
		return 0.0f;
	}

	/** How far the rotor has turned, in degrees, interpolated for the frame. */
	public float rotorAngleDegrees(float partialTick) {
		return 0.0f;
	}

	/** How far the blades are feathered, in degrees. */
	public float bladePitchDegrees() {
		return 0.0f;
	}

	/** Whether the rotor is held still, for whatever reason. */
	public boolean isStopped() {
		return !isRunning();
	}

	// ---- what a panel and a computer read ----

	public double getReportedPowerKw() {
		return 0.0;
	}

	public double getUncappedPower() {
		return 0.0;
	}

	public boolean isWindCutOut() {
		return false;
	}

	public double getWindSpeed() {
		return 0.0;
	}

	public double getMeanWindSpeed() {
		return 0.0;
	}

	public double getGustWindSpeed() {
		return 0.0;
	}

	public double getShearExponent() {
		return 0.14;
	}

	public double getAirDensity() {
		return 1.225;
	}

	/** Hub height above the ground, in metres. */
	public double getHubHeightM() {
		return getTowerSegments() * WorldConditions.METRES_PER_BLOCK;
	}

	public Telemetry.Snapshot getTelemetry() {
		return Telemetry.Snapshot.EMPTY;
	}

	// ---- control ----

	public boolean isStoppedByPlayer() {
		return stoppedByPlayer;
	}

	public void setStoppedByPlayer(boolean stopped) {
		stoppedByPlayer = stopped;
		changed();
	}

	@Override
	public boolean isStoppedByComputer() {
		return stoppedByComputer;
	}

	@Override
	public void setStoppedByComputer(boolean stopped) {
		stoppedByComputer = stopped;
		changed();
	}

	@Override
	public boolean isStoppedByRedstone() {
		return level != null && !redstoneMode.allowsRunning(level.hasNeighborSignal(worldPosition));
	}

	@Override
	public RedstoneMode getRedstoneMode() {
		return redstoneMode;
	}

	@Override
	public void setRedstoneMode(RedstoneMode mode) {
		redstoneMode = mode;
		changed();
	}

	@Override
	public double getActivePowerLimit() {
		return Double.isNaN(activePowerLimitKw) ? spec().ratedPowerKw() : activePowerLimitKw;
	}

	@Override
	public void setActivePowerLimit(double limitKw) {
		activePowerLimitKw = Mth.clamp(limitKw, 0.0, spec().ratedPowerKw());
		changed();
	}

	@Override
	public boolean isRunning() {
		return !stoppedByPlayer && !stoppedByComputer && !isStoppedByRedstone();
	}

	@Override
	public double getNameplateKw() {
		return spec().ratedPowerKw();
	}

	private void changed() {
		setChanged();
		if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
	}

	// ---- the energy budget other mods draw from ----

	@Override
	public double getGrossJoulesPerTick() {
		return 0.0;
	}

	@Override
	public double getAvailableJoules() {
		return 0.0;
	}

	@Override
	public double getMaxJoulesPerTick() {
		return 0.0;
	}

	@Override
	public double claimJoules(double joules, boolean simulate) {
		return 0.0;
	}

	// ---- the wire fitting ----

	@Override
	public String fittingType() {
		return FittingTypes.WIND_TURBINE;
	}

	@Override
	public boolean feeds(InsulatorHost other) {
		return other instanceof ElectricCabinBlockEntity || other instanceof WindTurbineBlockEntity
				|| other instanceof PvInverterBlockEntity || other instanceof TransformerBlockEntity;
	}

	/** A turbine's output goes to the plant's collector network, which is 33 kV. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() == ConductorSpec.VoltageClass.MEDIUM;
	}

	@Override
	public String powerType(String partName) {
		return FittingTypes.POWER_OUTPUT;
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("stoppedByPlayer", stoppedByPlayer);
		tag.putBoolean("stoppedByComputer", stoppedByComputer);
		tag.putString("redstoneMode", redstoneMode.name());
		if (!Double.isNaN(activePowerLimitKw)) tag.putDouble("activePowerLimit", activePowerLimitKw);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		stoppedByPlayer = tag.getBoolean("stoppedByPlayer");
		stoppedByComputer = tag.getBoolean("stoppedByComputer");
		RedstoneMode mode = RedstoneMode.byName(tag.getString("redstoneMode"));
		redstoneMode = mode == null ? RedstoneMode.DISABLED : mode;
		activePowerLimitKw = tag.contains("activePowerLimit") ? tag.getDouble("activePowerLimit") : Double.NaN;
	}
}
