package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.WorldConditions;
import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.Dispatchable;
import io.github.simonerecchia.renewables.api.power.RedstoneMode;
import io.github.simonerecchia.renewables.api.power.Telemetry;
import io.github.simonerecchia.renewables.api.power.TickBudget;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.catalog.TurbineCatalog;
import io.github.simonerecchia.renewables.compat.energy.EnergyBridge;
import io.github.simonerecchia.renewables.core.ServerConfig;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.model.ExtraPose;
import io.github.simonerecchia.renewables.model.ModelDefinition;
import io.github.simonerecchia.renewables.model.ModelDefinitions;
import io.github.simonerecchia.renewables.model.PartBox;
import io.github.simonerecchia.renewables.model.PartBoxes;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;
import io.github.simonerecchia.renewables.telemetry.TurbineTelemetrySimulator;
import io.github.simonerecchia.renewables.weather.LevelWeather;
import io.github.simonerecchia.renewables.weather.WeatherSnapshot;
import io.github.simonerecchia.renewables.wind.TurbineDynamics;
import io.github.simonerecchia.renewables.wind.TurbineExport;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.joml.Vector3f;

/**
 * A wind turbine: the weather at its hub, the curve of its model, the brake, the curtailment and the redstone
 * that hold it back, the energy it offers, the telemetry it publishes, and its single wire fitting.
 *
 * <b>Every server tick, in this order:</b> sample the weather at the hub; turn the nacelle towards the wind; work
 * out how much of the wind the rotor faces; trip or release the wind cut-out; read the redstone; work out the power;
 * turn the rotor's speed towards what that power implies; open the tick's energy budget and push it to the
 * neighbours; publish the telemetry; sync the client if it is time.  All of that happens in the level tick, before
 * the grid solves at the end of the server tick, so the grid sees what is left of the power after the cables of other
 * mods have taken theirs.
 *
 * <b>Angles.</b> The yaw kept here is in the wind's own system (the heading the air travels towards: 0 east, 90
 * south), so that it can be compared with the wind direction as the telemetry does.  A rotor faces <em>into</em> the
 * wind, and the renderer is given the rotation to turn the model by, already converted: see {@link #yawDegrees}.
 *
 * <b>The foot.</b> The machine is the top block of a stack of tower blocks, but its cables, its redstone and its
 * energy are all at the bottom: the fitting is by the foot, the signal is read there, and neighbouring energy
 * systems meet it there (the tower's foot forwards the capability to this block entity).
 */
public class WindTurbineBlockEntity extends MachineBlockEntity implements Dispatchable, ExtraPose {
	/** How often the height of the tower under the machine is looked at again, in ticks. */
	private static final int TOWER_RECHECK_TICKS = 20;
	/** The yaw has to have moved this far from the last one the client was told before it is worth telling it sooner. */
	private static final double YAW_SYNC_THRESHOLD = 1.0;
	/** Fastest the client turns its drawn nacelle onto the synced yaw, in degrees per tick. */
	private static final double DRAWN_YAW_MAX_STEP = 1.5;
	/** A fraction of the remaining gap the client closes each tick. */
	private static final double DRAWN_YAW_CATCH_UP = 0.25;
	/** Fastest the drawn blades feather or unfeather, in degrees per tick. */
	private static final double DRAWN_PITCH_STEP = 2.0;

	// ---- control: what a player, a computer or a signal has set ----

	private volatile boolean stoppedByPlayer;
	private volatile boolean stoppedByComputer;
	private volatile RedstoneMode redstoneMode = RedstoneMode.DISABLED;
	/** The signal last read at the machine or at its foot. */
	private volatile boolean redstonePowered;
	/** The curtailment setpoint in kW: the nameplate until somebody lowers it, and never zero by default. */
	private volatile double activePowerLimitKw;

	// ---- state: what the weather and the machine are doing, saved and sent to the client ----

	private double meanWind;
	private double gustWind;
	private double instantWind;
	private double alignedWind;
	private double shearExponent = 0.14;
	private double ambientTempC = 15.0;
	private double pressureHpa = WorldConditions.SEA_LEVEL_PRESSURE;
	private double airDensity = WorldConditions.REFERENCE_AIR_DENSITY;
	private double windDirection;
	private double turbulence;
	private boolean cutOut;
	/** Which way the nacelle points, in the wind's system; meaningful once {@link #yawInitialised}. */
	private double yawWind;
	private boolean yawInitialised;
	private double cableTwist;
	private double producedKw;
	private double uncappedKw;
	/** The rotor's real speed, in degrees per tick. */
	private double rotorSpeed;

	// ---- not saved ----

	private boolean yawing;
	private double lastSentYaw;
	private int towerSegments = -1;
	private int towerRecheck = TOWER_RECHECK_TICKS;

	private final TickBudget budget = new TickBudget();
	private final ClientSync clientSync = new ClientSync();
	private final LazyOptional<IEnergyStorage> forgeEnergy = LazyOptional.of(() -> EnergyBridge.forgeEnergyView(this));
	private final LazyOptional<?> mekanismEnergy = EnergyBridge.createMekanismHandler(this);
	private final TurbineTelemetrySimulator telemetrySimulator = new TurbineTelemetrySimulator();
	private volatile Telemetry.Snapshot telemetry = Telemetry.Snapshot.EMPTY;

	// ---- client only: what is drawn, which lags what is true ----

	private boolean drawnReady;
	/** The rotation to give the model about Y, last tick and now. */
	private double drawnYawBefore;
	private double drawnYaw;
	/** How far the rotor has turned in its direction of travel, last tick and now, in degrees. */
	private double rotorProgressBefore;
	private double rotorProgress;
	/** The rotor's speed as the client carries it between syncs, in degrees per tick. */
	private double clientSpeed;
	private double drawnPitch;

	public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.WIND_TURBINE.get(), pos, state);
		this.activePowerLimitKw = spec().ratedPowerKw();
	}

	/** Which model this is; the C130 if the block does not say. */
	public TurbineSpec spec() {
		if (getBlockState().getBlock() instanceof WindTurbineBlock turbine) return turbine.spec();

		return TurbineCatalog.fallback();
	}

	// ---- the tower ----

	/** Tower segments under the machine, kept and looked at again every second rather than on every query. */
	public int getTowerSegments() {
		if (level == null) return 0;
		if (towerSegments < 0) towerSegments = TurbineTowerBlock.countBelow(level, worldPosition);

		return towerSegments;
	}

	/** Hub height above the foot of the tower, in blocks. */
	public int towerHeight() {
		return getTowerSegments();
	}

	/** Where the foot of the tower is: its lowest segment. */
	public BlockPos footPos() {
		return worldPosition.below(getTowerSegments());
	}

	private void recheckTower() {
		if (level == null || --towerRecheck > 0) return;

		towerRecheck = TOWER_RECHECK_TICKS;
		int now = TurbineTowerBlock.countBelow(level, worldPosition);
		if (now == towerSegments) return;

		towerSegments = now;
		refreshAnchors();
		if (!level.isClientSide()) {
			setChanged();
			ClientSync.now(this);
		}
	}

	// ---- what the renderer reads (docs/ARCHITECTURE.md section 3.7) ----

	/**
	 * Which way the nacelle faces: the rotation about Y, in degrees, to give the model, in the same system as the
	 * block's own facing rotation ({@code ModelFacing.degrees}: 0 = the front faces south, 90 = east).  The rotor
	 * looks into the wind, so this is not the wind direction but its opposite, and it already is the finished
	 * rotation: the renderer subtracts the facing it has applied itself and turns the machine by the rest.
	 *
	 * On the client it is a smoothed value, interpolated between ticks; the unsmoothed target is
	 * {@link #nacelleDirection()} converted by {@link TurbineDynamics#modelRotationDegrees}.
	 *
	 * @return degrees in (-180, 180]
	 */
	public float yawDegrees(float partialTick) {
		if (level == null || !level.isClientSide()) return (float) TurbineDynamics.modelRotationDegrees(currentYawWind());

		ensureDrawn();
		double step = TurbineDynamics.shortestAngle(drawnYawBefore, drawnYaw);
		return (float) TurbineDynamics.wrapSigned(drawnYawBefore + step * partialTick);
	}

	/**
	 * How far the rotor has turned, in degrees in [0, 360), to give the model about its own +Z axis.  Seen from in
	 * front a real rotor turns clockwise, which is a negative rotation about +Z, so this runs backwards: the number
	 * falls as the rotor spins.
	 */
	public float rotorAngleDegrees(float partialTick) {
		if (level == null || !level.isClientSide()) return 0.0f;

		ensureDrawn();
		double progress = rotorProgressBefore + (rotorProgress - rotorProgressBefore) * partialTick;
		return (float) TurbineDynamics.wrapCompass(-progress);
	}

	/** How far the blades are feathered, in degrees: 0 working, 90 edge on to the wind. */
	public float bladePitchDegrees() {
		if (level != null && level.isClientSide()) return (float) drawnPitch;

		return (float) TurbineDynamics.collectivePitch(isBraked(), alignedWind, spec());
	}

	/** Whether the rotor is held still by a command or by redstone.  A machine cut out by the wind still idles. */
	public boolean isStopped() {
		return isParked();
	}

	private void ensureDrawn() {
		if (drawnReady) return;

		drawnReady = true;
		drawnYaw = drawnYawBefore = TurbineDynamics.modelRotationDegrees(currentYawWind());
		clientSpeed = rotorSpeed;
		drawnPitch = TurbineDynamics.collectivePitch(isBraked(), alignedWind, spec());
	}

	// ---- ExtraPose: the model is drawn from the foot of the tower up ----

	@Override
	public Vec3 poseOffset() {
		return new Vec3(0.0, -getTowerSegments(), 0.0);
	}

	@Override
	public float poseYawDegrees() {
		return 0.0f;
	}

	@Override
	public float posePitchDegrees() {
		return 0.0f;
	}

	// ---- what a panel and a computer read ----

	/** What the machine is producing, in kW. */
	public double getReportedPowerKw() {
		return producedKw;
	}

	/** What the wind offers at the rotor, in kW, before the curtailment: zero while braked. */
	public double getUncappedPower() {
		return uncappedKw;
	}

	/** Whether the machine has stopped itself because the wind is over its cut-out. */
	public boolean isWindCutOut() {
		return cutOut;
	}

	/** The instantaneous wind at the hub, in m/s, whichever way the nacelle points. */
	public double getWindSpeed() {
		return instantWind;
	}

	/** The ten-minute mean at the hub: what the machine supervises on. */
	public double getMeanWindSpeed() {
		return meanWind;
	}

	/** The three-second gust. */
	public double getGustWindSpeed() {
		return gustWind;
	}

	/** The wind the rotor actually faces: the instantaneous wind, less what a nacelle that is off the wind loses. */
	public double getAlignedWindSpeed() {
		return alignedWind;
	}

	/** The site's wind-profile exponent, for estimating what one more block of tower would be worth. */
	public double getShearExponent() {
		return shearExponent;
	}

	public double getAirDensity() {
		return airDensity;
	}

	/** Turbulence intensity at the hub: the standard deviation of the wind over its mean. */
	public double getTurbulence() {
		return turbulence;
	}

	/** The direction the wind is blowing towards, in degrees: 0 east, 90 south. */
	public double getWindDirection() {
		return windDirection;
	}

	/** The nacelle's heading in the same system as {@link #getWindDirection}: they are equal when it is aligned. */
	public double getNacelleDirection() {
		return currentYawWind();
	}

	/** Hub height above the ground, in metres. */
	public double getHubHeightM() {
		return getTowerSegments() * WorldConditions.METRES_PER_BLOCK;
	}

	/** The rotor's real speed, in rpm. */
	public double getRotorRpm() {
		return TurbineDynamics.rpmOf(rotorSpeed);
	}

	public Telemetry.Snapshot getTelemetry() {
		return telemetry;
	}

	// ---- what the grid asks ----

	/** What the wire network may carry away this tick, in kW: what is made, less what other mods' cables took. */
	public double getGeneratedPower() {
		return TurbineExport.gridKw(producedKw, budget.claimed());
	}

	/** Whether this generator is putting a disturbance onto the network. */
	public boolean isSurging() {
		return TurbineDynamics.surging(turbulence, meanWind, isBraked(), spec());
	}

	/** A generator reads what it is sending out; what the grid delivered back to its terminal is not a quantity a turbine has. */
	@Override
	public double getCurrentPower() {
		return producedKw;
	}

	// ---- the tick ----

	public void serverTick() {
		if (!(level instanceof ServerLevel serverLevel)) return;

		recheckTower();
		TurbineSpec spec = spec();

		// the weather at the hub, for a tower that is within what the model is sold on
		int mast = spec.clampTowerSegments(getTowerSegments());
		WeatherSnapshot weather = LevelWeather.get(serverLevel).sampleWind(worldPosition, mast);
		meanWind = weather.meanWind();
		gustWind = weather.gustWind();
		instantWind = weather.instantWind();
		windDirection = weather.direction();
		turbulence = weather.turbulence();
		shearExponent = weather.shearExponent();
		airDensity = weather.airDensity();
		ambientTempC = weather.temperatureC();
		pressureHpa = weather.pressureHpa();

		TurbineDynamics.YawStep yaw = TurbineDynamics.stepYaw(currentYawWind(), windDirection);
		yawWind = yaw.yaw();
		yawInitialised = true;
		yawing = yaw.yawing();
		cableTwist += yaw.twist();

		alignedWind = instantWind * TurbineDynamics.alignment(TurbineDynamics.shortestAngle(windDirection, yawWind));
		cutOut = TurbineDynamics.updateCutOut(cutOut, meanWind, gustWind, spec);
		pollRedstone();

		// the power first and then the speed, because the speed reads the limit against what the wind offers now
		boolean braked = isBraked();
		uncappedKw = TurbineDynamics.uncappedKw(braked, alignedWind, airDensity, spec);
		producedKw = TurbineDynamics.producedKw(braked, uncappedKw, activePowerLimitKw);
		rotorSpeed = TurbineDynamics.lagSpeed(rotorSpeed,
				TurbineDynamics.targetDegreesPerTick(isParked(), cutOut, alignedWind, activePowerLimitKw, uncappedKw, spec));

		budget.open(TurbineExport.offeredJoules(producedKw, getMaxJoulesPerTick()));
		EnergyBridge.emit(this, this, footPos(), energyFaces());

		publishTelemetry(serverLevel, spec, braked);
		syncIfDue();
	}

	/**
	 * The machine reads the redstone at the foot of its tower, where a player can reach a lever, as well as where it
	 * stands.  A change matters at once unless redstone is being ignored, in which case it is only kept.
	 */
	private void pollRedstone() {
		if (level == null) return;

		boolean signal = level.hasNeighborSignal(worldPosition) || level.hasNeighborSignal(footPos());
		if (signal == redstonePowered) return;

		redstonePowered = signal;
		if (redstoneMode != RedstoneMode.DISABLED) {
			onControlChanged();
		} else {
			setChanged();
		}
	}

	private void publishTelemetry(ServerLevel serverLevel, TurbineSpec spec, boolean braked) {
		boolean raining = serverLevel.isRainingAt(worldPosition.above());

		telemetry = telemetrySimulator.sample(new TurbineTelemetrySimulator.Sample(
				producedKw, spec.ratedPowerKw(), activePowerLimitKw,
				TurbineDynamics.powerLimited(braked, alignedWind, uncappedKw, producedKw, spec),
				meanWind, alignedWind, windDirection, yawWind, turbulence, rotorSpeed,
				braked, cutOut, stoppedByComputer, stoppedByPlayer, isStoppedByRedstone(), yawing,
				ambientTempC, pressureHpa, raining, raining && serverLevel.isThundering(),
				serverLevel.getGameTime(), Math.abs(worldPosition.hashCode() % 1024), cableTwist,
				spec.ratedSpeed(), spec.stormOnsetSpeed(), spec.cutOutSpeed(), spec.gearboxRatio()));
	}

	/** Tells the clients every half second, and sooner when the nacelle has turned noticeably since they last heard. */
	private void syncIfDue() {
		if (clientSync.throttled(this)) {
			lastSentYaw = yawWind;
		} else if (Math.abs(TurbineDynamics.shortestAngle(lastSentYaw, yawWind)) > YAW_SYNC_THRESHOLD) {
			ClientSync.now(this);
			lastSentYaw = yawWind;
		}
	}

	/** The client's half of the tick: it only moves what it draws, towards what it was told. */
	public void clientTick() {
		if (level == null || !level.isClientSide()) return;

		recheckTower();
		TurbineSpec spec = spec();
		ensureDrawn();

		// the nacelle turns onto the synced heading, not faster than a yaw drive could
		double target = TurbineDynamics.modelRotationDegrees(currentYawWind());
		double gap = TurbineDynamics.shortestAngle(drawnYaw, target);
		drawnYawBefore = drawnYaw;
		drawnYaw = TurbineDynamics.wrapSigned(drawnYaw + Mth.clamp(gap * DRAWN_YAW_CATCH_UP, -DRAWN_YAW_MAX_STEP, DRAWN_YAW_MAX_STEP));

		// the rotor carries on from the speed it was last told, closing on the speed the synced state implies
		clientSpeed = TurbineDynamics.lagSpeed(clientSpeed,
				TurbineDynamics.targetDegreesPerTick(isParked(), cutOut, alignedWind, activePowerLimitKw, uncappedKw, spec));
		rotorProgressBefore = rotorProgress;
		rotorProgress += spec.drawnRotation(clientSpeed);
		if (rotorProgress >= 360.0) {
			rotorProgress -= 360.0;
			rotorProgressBefore -= 360.0;
		}

		double pitchTarget = TurbineDynamics.collectivePitch(isBraked(), alignedWind, spec);
		drawnPitch += Mth.clamp(pitchTarget - drawnPitch, -DRAWN_PITCH_STEP, DRAWN_PITCH_STEP);
	}

	/** The nacelle's heading in the wind's system: what it was, or what the block's facing gives a machine that has not moved yet. */
	private double currentYawWind() {
		if (yawInitialised) return yawWind;

		return TurbineDynamics.windYawOfModelRotation(ModelFacing.degrees(WindTurbineBlock.AUTHORED, facing(WindTurbineBlock.AUTHORED)));
	}

	// ---- control ----

	public boolean isStoppedByPlayer() {
		return stoppedByPlayer;
	}

	public void setStoppedByPlayer(boolean stopped) {
		if (stoppedByPlayer == stopped) return;

		stoppedByPlayer = stopped;
		onControlChanged();
	}

	@Override
	public boolean isStoppedByComputer() {
		return stoppedByComputer;
	}

	@Override
	public void setStoppedByComputer(boolean stopped) {
		if (stoppedByComputer == stopped) return;

		stoppedByComputer = stopped;
		onControlChanged();
	}

	@Override
	public boolean isStoppedByRedstone() {
		return !redstoneMode.allowsRunning(redstonePowered);
	}

	@Override
	public RedstoneMode getRedstoneMode() {
		return redstoneMode;
	}

	@Override
	public void setRedstoneMode(RedstoneMode mode) {
		if (mode == null || mode == redstoneMode) return;

		redstoneMode = mode;
		onControlChanged();
	}

	@Override
	public double getActivePowerLimit() {
		return activePowerLimitKw;
	}

	/** Holds the output to a setpoint between nothing and the nameplate.  A number that is not a number is not a setpoint. */
	@Override
	public void setActivePowerLimit(double limitKw) {
		if (!Double.isFinite(limitKw)) return;

		double clamped = Mth.clamp(limitKw, 0.0, spec().ratedPowerKw());
		if (clamped == activePowerLimitKw) return;

		activePowerLimitKw = clamped;
		onControlChanged();
	}

	/** Whether the rotor is held by a command or by redstone, as opposed to a protection of the machine's own. */
	private boolean isParked() {
		return stoppedByComputer || stoppedByPlayer || isStoppedByRedstone();
	}

	/** Braked for any of the four reasons, which are kept apart in the telemetry: the wind cut-out, the player, a computer, redstone. */
	public boolean isBraked() {
		return cutOut || isParked();
	}

	/** Whether the machine is turning and allowed to generate: it is not braked, for any reason. */
	@Override
	public boolean isRunning() {
		return !isBraked();
	}

	@Override
	public double getNameplateKw() {
		return spec().ratedPowerKw();
	}

	/** Saves and tells every client at once: somebody changed a setting and is watching for it to take. */
	private void onControlChanged() {
		ClientSync.now(this);
	}

	// ---- the energy budget other mods draw from ----

	private List<Direction> energyFaces() {
		return List.of(Direction.DOWN, facing(WindTurbineBlock.AUTHORED).getOpposite());
	}

	private boolean isEnergyFace(@Nullable Direction side) {
		return side == null || energyFaces().contains(side);
	}

	/** Joules produced this tick, before anything was claimed. */
	@Override
	public double getGrossJoulesPerTick() {
		return producedKw * TurbineExport.JOULES_PER_KW_TICK;
	}

	@Override
	public double getAvailableJoules() {
		if (level == null || level.isClientSide() || !ServerConfig.externalEnergyEnabled()) return 0.0;

		return budget.available();
	}

	/** The most the turbine offers other mods in one tick: its nameplate times the configured share, under the absolute ceiling if there is one. */
	@Override
	public double getMaxJoulesPerTick() {
		if (level == null || level.isClientSide() || !ServerConfig.externalEnergyEnabled()) return 0.0;

		return TurbineExport.capacityJoules(spec().ratedPowerKw(), ServerConfig.turbineExportFraction(), ServerConfig.turbineMaxJoulesPerTick());
	}

	@Override
	public double claimJoules(double joules, boolean simulate) {
		return budget.claim(joules, simulate);
	}

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		if (isEnergyFace(side)) {
			if (cap == ForgeCapabilities.ENERGY) return forgeEnergy.cast();
			if (EnergyBridge.isMekanismEnergyCapability(cap)) return mekanismEnergy.cast();
		}

		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		forgeEnergy.invalidate();
		mekanismEnergy.invalidate();
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

	/**
	 * Where the wire ends: the top of the fitting's porcelain, which sits at the foot of the tower and not up at the
	 * machine, so the model's frame is lowered by the tower under it exactly as the renderer lowers it.  Until the
	 * model's table says where the fitting is, the middle of the foot block.
	 */
	@Nullable
	@Override
	protected Vec3 computeAnchor(int index) {
		ModelDefinition definition = ModelDefinitions.of(getBlockState().getBlock());
		String group = FittingTypes.nameOf(definition, index);
		PartBox box = group == null ? null : PartBoxes.box(getBlockState().getBlock(), group);
		if (definition == null || box == null) return Vec3.atCenterOf(footPos());

		Vector3f top = box.top();
		return ExtraPose.toWorld(new Vec3(top.x(), top.y(), top.z()), worldPosition, definition.authored(), facing(definition.authored()), this);
	}

	// ---- persistence and syncing ----

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);

		tag.putBoolean("stoppedByPlayer", stoppedByPlayer);
		tag.putBoolean("stoppedByComputer", stoppedByComputer);
		tag.putString("redstoneMode", redstoneMode.name());
		tag.putBoolean("redstonePowered", redstonePowered);
		tag.putDouble("activePowerLimit", activePowerLimitKw);

		tag.putDouble("meanWind", meanWind);
		tag.putDouble("gustWind", gustWind);
		tag.putDouble("instantWind", instantWind);
		tag.putDouble("alignedWind", alignedWind);
		tag.putDouble("shearExponent", shearExponent);
		tag.putDouble("ambientTemp", ambientTempC);
		tag.putDouble("pressure", pressureHpa);
		tag.putDouble("airDensity", airDensity);
		tag.putDouble("windDirection", windDirection);
		tag.putDouble("turbulence", turbulence);
		tag.putBoolean("cutOut", cutOut);
		if (yawInitialised) tag.putDouble("yaw", yawWind);
		tag.putDouble("cableTwist", cableTwist);
		tag.putDouble("produced", producedKw);
		tag.putDouble("uncapped", uncappedKw);
		tag.putDouble("rotorSpeed", rotorSpeed);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);

		stoppedByPlayer = tag.getBoolean("stoppedByPlayer");
		stoppedByComputer = tag.getBoolean("stoppedByComputer");
		RedstoneMode mode = RedstoneMode.byName(tag.getString("redstoneMode"));
		redstoneMode = mode == null ? RedstoneMode.DISABLED : mode;
		redstonePowered = tag.getBoolean("redstonePowered");
		// a machine saved before it had a setpoint is unlimited, not limited to nothing, which would switch it off
		double limit = tag.contains("activePowerLimit") ? tag.getDouble("activePowerLimit") : spec().ratedPowerKw();
		activePowerLimitKw = Double.isFinite(limit) ? Mth.clamp(limit, 0.0, spec().ratedPowerKw()) : spec().ratedPowerKw();

		instantWind = tag.getDouble("instantWind");
		// the readings a save without them is given: the mean and the gust are the instantaneous wind, calm air at sea level
		meanWind = tag.contains("meanWind") ? tag.getDouble("meanWind") : instantWind;
		gustWind = tag.contains("gustWind") ? tag.getDouble("gustWind") : instantWind;
		alignedWind = tag.contains("alignedWind") ? tag.getDouble("alignedWind") : instantWind;
		shearExponent = tag.contains("shearExponent") ? tag.getDouble("shearExponent") : 0.14;
		ambientTempC = tag.contains("ambientTemp") ? tag.getDouble("ambientTemp") : 15.0;
		pressureHpa = tag.contains("pressure") ? tag.getDouble("pressure") : WorldConditions.SEA_LEVEL_PRESSURE;
		airDensity = tag.contains("airDensity") ? tag.getDouble("airDensity") : WorldConditions.REFERENCE_AIR_DENSITY;
		windDirection = tag.getDouble("windDirection");
		turbulence = tag.getDouble("turbulence");
		cutOut = tag.getBoolean("cutOut");
		yawInitialised = tag.contains("yaw");
		yawWind = yawInitialised ? tag.getDouble("yaw") : 0.0;
		lastSentYaw = yawWind;
		cableTwist = tag.getDouble("cableTwist");
		producedKw = tag.getDouble("produced");
		uncappedKw = tag.getDouble("uncapped");
		rotorSpeed = tag.getDouble("rotorSpeed");
	}

	/** The client takes everything the server knows, then carries its animation on from it. */
	@Override
	public void handleUpdateTag(CompoundTag tag) {
		towerSegments = -1;
		super.handleUpdateTag(tag);

		// the rotor goes on from the speed it was told; the nacelle and the blades are caught up by their own easing
		clientSpeed = rotorSpeed;
	}

	@Override
	public void onLoad() {
		towerSegments = -1;
		super.onLoad();
	}
}
