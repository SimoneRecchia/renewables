package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.CombinerSpec;
import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.Dispatchable;
import io.github.simonerecchia.renewables.api.power.InverterSpec;
import io.github.simonerecchia.renewables.api.power.MpptInputs;
import io.github.simonerecchia.renewables.api.power.PvArraySpec;
import io.github.simonerecchia.renewables.api.power.PvModuleSpec;
import io.github.simonerecchia.renewables.api.power.RedstoneMode;
import io.github.simonerecchia.renewables.api.power.StringCurve;
import io.github.simonerecchia.renewables.api.power.Telemetry;
import io.github.simonerecchia.renewables.catalog.CableCatalog;
import io.github.simonerecchia.renewables.catalog.CombinerCatalog;
import io.github.simonerecchia.renewables.catalog.InverterCatalog;
import io.github.simonerecchia.renewables.compat.energy.EnergyBridge;
import io.github.simonerecchia.renewables.core.ServerConfig;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.GeneratorBudget;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.model.PartBox;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;
import io.github.simonerecchia.renewables.telemetry.SolarTelemetrySimulator;
import io.github.simonerecchia.renewables.weather.LevelWeather;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.LongTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.joml.Vector3f;

/** The inverter: the whole electrical side of a photovoltaic plant, in one block. */
public class PvInverterBlockEntity extends MachineBlockEntity implements Dispatchable {
	/** How often the plant is surveyed for arrays again, in ticks. */
	private static final int RESCAN_TICKS = 40;
	/** Ticks in a day, for the energy counters that reset with it. */
	private static final long TICKS_PER_DAY = 24000L;

	private final List<BlockPos> arrays = new ArrayList<>();
	/** The combiner boxes wired to this cabinet on trunk cable. */
	private final List<BlockPos> combiners = new ArrayList<>();
	private int rescanCountdown = 0;
	/** The box fitted inside the cabinet, if any. */
	private CombinerSpec integrated = null;

	private double availableDcKw = 0.0;
	private double dcPowerKw = 0.0;
	private double acPowerKw = 0.0;
	private double dcVoltage = 0.0;
	private double dcCurrent = 0.0;
	private double cabinetTempC = 15.0;
	private double ambientTempC = 15.0;
	private double efficiency = 0.0;
	private boolean clipping = false;
	private boolean derating = false;
	/** The strings are lit but at a voltage this machine cannot track. */
	private boolean stringsOutOfWindow = false;
	private int stringsConnected = 0;
	/** Direct-current input the machine has left, in amps. */
	private double stringCurrentHeadroom = 0.0;
	/** Why the last array or box was turned away, for the panel to name. */
	private Refusal refusal = Refusal.NONE;
	/** Nameplate of everything wired in, arrays and boxes alike, kW. */
	private double dcNameplateKw = 0.0;
	/** Held under what it could make by somebody else's limit, rather than by its own ceiling. */
	private boolean curtailed = false;
	/** The knee of the biggest source the MPPT window is holding off its maximum, volts. */
	private double outOfWindowVolts = 0.0;
	/** The arrays as of the last scan, published whole for a computer's thread to read. */
	private volatile List<BlockPos> publishedArrays = List.of();

	private double energyTodayKwh = 0.0;
	private double energyLifetimeKwh = 0.0;
	private long lastDay = -1L;

	private volatile boolean stoppedByComputer = false;
	private volatile boolean stoppedByPlayer = false;
	private final RedstoneStop redstone = new RedstoneStop();
	private volatile double activePowerLimitKw;
	private volatile double powerFactorSetpoint = 1.0;

	private final GeneratorBudget budget = new GeneratorBudget(GeneratorBudget.Kind.PHOTOVOLTAIC);
	private final LazyOptional<IEnergyStorage> forgeEnergy = LazyOptional.of(() -> EnergyBridge.forgeEnergyView(this));
	private final LazyOptional<?> mekanismEnergy = EnergyBridge.createMekanismHandler(this);

	private final ClientSync clientSync = new ClientSync();

	private final SolarTelemetrySimulator telemetrySimulator = new SolarTelemetrySimulator();
	private volatile Telemetry.Snapshot telemetry = Telemetry.Snapshot.EMPTY;
	/** The reference array's readings, which are the plant's as far as its SCADA is concerned. */
	private double referenceIrradiance = 0.0;
	private double referenceModuleTempC = 15.0;

	/** What turned an array or a box away from this machine, most informative first. */
	public enum Refusal {
		NONE,
		/** Its cold-morning open-circuit voltage is over the machine's input rating. */
		INPUT_VOLTAGE,
		/** The fitted box is not insulated for it. */
		INSULATION,
		/** No fuse the fitted box holds, or none the module allows, protects its strings. */
		FUSE,
		/** Every string terminal, or every way of the fitted box, is taken. */
		TERMINALS,
		/** The direct-current power rating is used up. */
		DC_POWER,
		/** No tracker has the current left for one of its strings, or the busbars for its trunk. */
		CURRENT;

		static Refusal of(CombinerSpec.Refusal box) {
			return switch (box) {
				case NONE -> NONE;
				case INSULATION -> INSULATION;
				case FUSE -> FUSE;
				case WAYS -> TERMINALS;
				case OUTPUT_CURRENT -> CURRENT;
			};
		}

		static Refusal worse(Refusal current, Refusal candidate) {
			if (current == NONE) return candidate;

			return current.ordinal() <= candidate.ordinal() ? current : candidate;
		}

		static Refusal byName(String name) {
			for (Refusal candidate : values()) {
				if (candidate.name().equals(name)) return candidate;
			}

			return NONE;
		}
	}

	public PvInverterBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PV_INVERTER.get(), pos, state);
		this.activePowerLimitKw = spec().acPowerKw();
	}

	/** Which machine this is. */
	public InverterSpec spec() {
		if (getBlockState().getBlock() instanceof PvInverterBlock inverter) return inverter.spec();

		return InverterCatalog.fallback();
	}

	// ---- the wire fitting ----

	/**
	 * The middle of the porcelain on the roof, where the drawn cabinet has it: the whole model is scaled about the
	 * middle of its footprint by {@link InverterSpec#renderScale}, so the fitting comes down and in with it.
	 */
	@Nullable
	@Override
	protected Vec3 localAnchor(int index) {
		PartBox box = fittingBox(index);
		if (box == null) return null;

		Vector3f centre = box.center();
		return new Vec3(centre.x(), centre.y(), centre.z()).scale(spec().renderScale());
	}

	// ---- the tick ----

	public void serverTick() {
		if (!(level instanceof ServerLevel serverLevel)) return;

		InverterSpec spec = spec();
		if (--rescanCountdown <= 0) {
			rescan(serverLevel, spec);
			rescanCountdown = RESCAN_TICKS;
		}

		redstone.poll(this, this::onControlChanged);
		ambientTempC = LevelWeather.get(serverLevel).sample(worldPosition, 1).temperatureC();

		gatherAndConvert(serverLevel, spec);
		accumulateEnergy(serverLevel);
		updateTelemetry(serverLevel, spec);

		budget.open(Math.max(0.0, acPowerKw), spec.acPowerKw());
		EnergyBridge.emit(this, this, worldPosition, energyFaces());

		clientSync.throttled(this);
	}

	/** Works out what the plant is doing and pushes the operating point back to the arrays. */
	private void gatherAndConvert(ServerLevel serverLevel, InverterSpec spec) {
		List<PvArrayBlockEntity> live = new ArrayList<>();
		for (BlockPos pos : arrays) {
			PvArrayBlockEntity array = LoadedBlockEntities.find(serverLevel, pos, PvArrayBlockEntity.class);
			// an array whose ground has gone out of memory is a part of the plant that is not there for
			// the moment, and a claim it cannot renew lapses at its end
			if (array == null) continue;
			// a shorter run may have taken it since the last scan, and until this one rescans it would
			// otherwise go on counting an array it no longer owns
			if (!worldPosition.equals(array.collectorPos())) continue;

			live.add(array);
		}

		List<PvCombinerBlockEntity> liveBoxes = new ArrayList<>();
		for (BlockPos pos : combiners) {
			PvCombinerBlockEntity box = LoadedBlockEntities.find(serverLevel, pos, PvCombinerBlockEntity.class);
			if (box == null) continue;
			if (!worldPosition.equals(box.inverterPos())) continue;

			liveBoxes.add(box);
		}

		// the first array in the list is the nearest, because that is the order the scan claimed them
		// in - which makes it the one a real plant would have bolted its reference instruments to
		if (!live.isEmpty()) {
			referenceIrradiance = live.get(0).poaFront();
			referenceModuleTempC = live.get(0).moduleTempC();
		} else if (!liveBoxes.isEmpty() && liveBoxes.get(0).hasReference()) {
			referenceIrradiance = liveBoxes.get(0).referenceIrradiance();
			referenceModuleTempC = liveBoxes.get(0).referenceModuleTempC();
		}

		// every array and every box is one source: its knee, its open-circuit voltage and what it offers at the
		// cabinet - what arrives, not what the modules made, since the difference is burnt in the run
		int count = live.size() + liveBoxes.size();
		double[] knee = new double[count];
		double[] open = new double[count];
		double[] offered = new double[count];
		for (int i = 0; i < live.size(); i++) {
			knee[i] = live.get(i).stringVoltageAtCollector();
			open[i] = live.get(i).stringOpenCircuitVoltage();
			offered[i] = live.get(i).offeredDcKw();
		}

		for (int i = 0; i < liveBoxes.size(); i++) {
			knee[live.size() + i] = liveBoxes.get(i).busVoltageAtInverter();
			open[live.size() + i] = liveBoxes.get(i).openCircuitVolts();
			offered[live.size() + i] = liveBoxes.get(i).offeredDcKw();
		}

		// where the trackers hold each source, and what it gives there
		double[] volts = new double[count];
		double[] held = new double[count];
		holdSources(spec, knee, open, offered, volts, held);

		double highestKnee = 0.0;
		double worstOffered = 0.0;
		availableDcKw = 0.0;
		stringsOutOfWindow = false;
		outOfWindowVolts = 0.0;
		for (int i = 0; i < count; i++) {
			highestKnee = Math.max(highestKnee, knee[i]);
			availableDcKw += offered[i] * held[i];
			// named on the panel, because it is the one mismatch a player cannot see: the biggest source held off
			if (offered[i] > 0.0 && knee[i] > 0.0 && !spec.withinMpptWindow(knee[i]) && offered[i] > worstOffered) {
				stringsOutOfWindow = true;
				outOfWindowVolts = knee[i];
				worstOffered = offered[i];
			}
		}

		// below the startup voltage there is nothing to wake up for: the strings are lit but not lit enough
		boolean awake = isRunning() && highestKnee >= spec.startupVolts() && availableDcKw > 0.0;

		// the bridge is rated in kVA: at a power factor under one the reactive part takes its share of the current
		double ownCeiling = Math.min(spec.thermalLimitKw(ambientTempC), spec.activePowerCeilingKw(powerFactorSetpoint));
		double limit = Math.max(0.0, activePowerLimitKw);
		double ceiling = Math.min(ownCeiling, limit);
		derating = spec.derating(ambientTempC);

		if (!awake) {
			dcPowerKw = 0.0;
			// the night draw, reported as a negative output: a reading only, the grid does not charge it
			acPowerKw = -spec.nightDrawKw();
			efficiency = 0.0;
			clipping = false;
			curtailed = false;
			dcVoltage = highestKnee;
			dcCurrent = 0.0;
			for (PvArrayBlockEntity array : live) {
				array.setMpptFraction(0.0);
			}

			for (PvCombinerBlockEntity box : liveBoxes) {
				box.setMpptFraction(0.0);
			}

			cabinetTempC = spec.cabinetTemperature(ambientTempC, 0.0);
			return;
		}

		// clipping is the machine's own ceiling binding, curtailment somebody else's limit binding below it
		double unclipped = spec.unclippedAcKw(availableDcKw);
		clipping = unclipped > ownCeiling + 1.0e-6 && ownCeiling <= limit + 1.0e-6;
		curtailed = unclipped > limit + 1.0e-6 && limit < ownCeiling - 1.0e-6;

		double wantedAc = spec.acPowerFromDcKw(availableDcKw, ceiling);
		// the direct current the machine actually pulls: what it needs to make that output.
		efficiency = wantedAc <= 0.0 ? 0.0 : spec.efficiencyAt(wantedAc / spec.acPowerKw());
		dcPowerKw = wantedAc <= 0.0 ? 0.0 : Math.min(availableDcKw, wantedAc + spec.lossKwAt(wantedAc));
		acPowerKw = wantedAc;

		// held back for the ceiling, every source moves off its knee by the same share
		double fraction = availableDcKw <= 0.0 ? 0.0 : Mth.clamp(dcPowerKw / availableDcKw, 0.0, 1.0);
		for (int i = 0; i < live.size(); i++) {
			live.get(i).setMpptFraction(held[i] * fraction);
		}

		// the boxes hand it on to their own strings, visible in the current of every module behind them
		for (int i = 0; i < liveBoxes.size(); i++) {
			liveBoxes.get(i).setMpptFraction(held[live.size() + i] * fraction);
		}

		double weighted = 0.0;
		dcCurrent = 0.0;
		for (int i = 0; i < count; i++) {
			double kw = offered[i] * held[i] * fraction;
			weighted += kw * volts[i];
			if (volts[i] > 0.0) dcCurrent += kw * 1000.0 / volts[i];
		}

		dcVoltage = dcPowerKw <= 0.0 ? highestKnee : weighted / dcPowerKw;
		cabinetTempC = spec.cabinetTemperature(ambientTempC, acPowerKw / spec.acPowerKw());
	}

	/**
	 * Where the trackers hold each source and what share of its maximum it gives there.  A machine with one tracker
	 * holds every source on one bus, at whatever voltage makes the most between them; a machine with several gives
	 * each its own, held inside the window.
	 */
	private static void holdSources(InverterSpec spec, double[] knee, double[] open, double[] offered, double[] volts, double[] held) {
		if (spec.mpptCount() == 1) {
			double bus = StringCurve.bestBusVoltage(knee, open, offered, spec.mpptMinVolts(), spec.mpptMaxVolts());
			for (int i = 0; i < knee.length; i++) {
				volts[i] = bus;
				held[i] = StringCurve.powerFraction(bus, knee[i], open[i]);
			}

			return;
		}

		for (int i = 0; i < knee.length; i++) {
			volts[i] = StringCurve.ownTrackerVolts(knee[i], spec.mpptMinVolts(), spec.mpptMaxVolts());
			held[i] = StringCurve.powerFraction(volts[i], knee[i], open[i]);
		}
	}

	/** Finds the arrays this inverter is wired to, by following the cable. */
	private void rescan(ServerLevel serverLevel, InverterSpec spec) {
		releaseArrays();
		arrays.clear();
		combiners.clear();
		stringsConnected = 0;
		refusal = Refusal.NONE;

		// the trackers' terminals and currents, string by string; the fitted box's ways, and the machine's own total
		MpptInputs inputs = new MpptInputs(spec);
		int directStrings = 0;
		double directCurrent = 0.0;
		double dcConnected = 0.0;

		for (DcNetwork.Reach reach : reachableArrays(serverLevel, spec)) {
			PvArrayBlockEntity array = LoadedBlockEntities.find(serverLevel, reach.pos(), PvArrayBlockEntity.class);
			if (array == null) continue;

			PvArraySpec wanted = array.spec();
			int strings = wanted.strings();
			double perString = wanted.stringCurrent(PvModuleSpec.STC_IRRADIANCE, PvModuleSpec.STC_TEMPERATURE);

			Refusal why = refuseStrings(spec, wanted, directStrings, directCurrent, dcConnected);
			MpptInputs landed = inputs.copy();
			if (why == Refusal.NONE && !landed.landStrings(strings, perString)) why = Refusal.CURRENT;
			if (why != Refusal.NONE) {
				refusal = Refusal.worse(refusal, why);
				continue;
			}

			if (!array.claim(worldPosition, reach, CableCatalog.STRING_6)) continue;

			inputs = landed;
			arrays.add(array.getBlockPos().immutable());
			stringsConnected += strings;
			directStrings += strings;
			directCurrent += perString * strings;
			dcConnected += wanted.dcPowerKw();
		}

		// then the boxes, on the other cable: their trunks go on the busbars, not on a string terminal's fuse
		for (DcNetwork.Reach reach : reachableCombiners(serverLevel, spec)) {
			PvCombinerBlockEntity box = LoadedBlockEntities.find(serverLevel, reach.pos(), PvCombinerBlockEntity.class);
			if (box == null) continue;

			Refusal why = Refusal.NONE;
			if (box.coldOpenCircuitVolts() > spec.maxDcVolts()) {
				why = Refusal.INPUT_VOLTAGE;
			} else if (stringsConnected + box.stringsConnected() > spec.stringInputs()) {
				why = Refusal.TERMINALS;
			} else if (dcConnected + box.nameplateDcKw() > spec.maxDcPowerKw()) {
				why = Refusal.DC_POWER;
			}

			MpptInputs landed = inputs.copy();
			if (why == Refusal.NONE && !landed.landTrunk(box.designAmps())) why = Refusal.CURRENT;
			if (why != Refusal.NONE) {
				refusal = Refusal.worse(refusal, why);
				continue;
			}

			if (!box.claim(worldPosition, reach, CableCatalog.TRUNK_240)) continue;

			inputs = landed;
			combiners.add(box.getBlockPos().immutable());
			stringsConnected += box.stringsConnected();
			dcConnected += box.nameplateDcKw();
		}

		stringCurrentHeadroom = inputs.freeAmps();
		dcNameplateKw = dcConnected;
		publishedArrays = List.copyOf(arrays);
	}

	/**
	 * Why one array's strings would not go on this machine's own terminals, or NONE.  The first check is the one
	 * that would wreck the input stage: a string whose cold-morning open-circuit voltage is over the rating.  The
	 * trackers' currents are checked by the caller, string by string.
	 */
	private Refusal refuseStrings(InverterSpec spec, PvArraySpec wanted, int directStrings, double directCurrent, double dcConnected) {
		if (wanted.stringOpenCircuitVoltage(PvArraySpec.COLDEST_DESIGN_CELL_C) > spec.maxDcVolts()) return Refusal.INPUT_VOLTAGE;

		// the fitted box's own four limit only what is wired into it; the cabinet's total is the cabinet's
		if (integrated != null) {
			Refusal box = Refusal.of(integrated.accepts(wanted, directStrings, directCurrent, integrated.outputAmps()));
			if (box != Refusal.NONE) return box;
		}

		if (stringsConnected + wanted.strings() > spec.stringInputs()) return Refusal.TERMINALS;
		if (dcConnected + wanted.dcPowerKw() > spec.maxDcPowerKw()) return Refusal.DC_POWER;

		return Refusal.NONE;
	}

	/** Every set of leads a run of string cable reaches from this cabinet */
	private List<DcNetwork.Reach> reachableArrays(ServerLevel serverLevel, InverterSpec spec) {
		if (!spec.stringTerminals() && integrated == null) return List.of();

		return PvStrings.reachable(serverLevel, worldPosition, CableCatalog.STRING_6,
				ServerConfig.maxCableRun());
	}

	/** Every combiner box a run of trunk cable reaches */
	private List<DcNetwork.Reach> reachableCombiners(ServerLevel serverLevel, InverterSpec spec) {
		if (!spec.trunkTerminals()) return List.of();

		return DcNetwork.reachable(serverLevel, worldPosition, CableCatalog.TRUNK_240,
				state -> state.getBlock() instanceof PvCombinerBlock, ServerConfig.maxCableRun());
	}

	/** The box fitted inside the cabinet, or null. */
	@Nullable
	public CombinerSpec integratedCombiner() {
		return integrated;
	}

	/** Records the box a player has just worked into the cabinet. */
	public void fitCombiner(CombinerSpec spec) {
		integrated = spec;
		rescanCountdown = 0;
		setChanged();
		ClientSync.now(this);
	}

	/** Runs the energy meters. */
	private void accumulateEnergy(ServerLevel serverLevel) {
		long day = serverLevel.getDayTime() / TICKS_PER_DAY;
		if (lastDay != day) {
			if (lastDay >= 0L) energyTodayKwh = 0.0;
			lastDay = day;
		}

		if (acPowerKw <= 0.0) return;

		// one tick is a twentieth of a second, and an hour is 3600 of them
		double kwh = acPowerKw / 20.0 / 3600.0;
		energyTodayKwh += kwh;
		energyLifetimeKwh += kwh;
	}

	/** The latest published snapshot. */
	public Telemetry.Snapshot getTelemetry() {
		return telemetry;
	}

	private void updateTelemetry(ServerLevel serverLevel, InverterSpec spec) {
		telemetry = telemetrySimulator.sampleInverter(new SolarTelemetrySimulator.InverterSample(
				spec, acPowerKw, dcPowerKw, availableDcKw, dcVoltage, dcCurrent, efficiency, cabinetTempC,
				ambientTempC, referenceModuleTempC, referenceIrradiance, energyTodayKwh, energyLifetimeKwh,
				activePowerLimitKw, powerFactorSetpoint, isRunning(), clipping, derating,
				stoppedByComputer, stoppedByPlayer, isStoppedByRedstone(), arrays.size(), stringsConnected, combiners.size(),
				stringCapacity(), stringCurrentHeadroom, dcAcRatio(), serverLevel.isRainingAt(worldPosition.above())));
	}

	// ---- readings ----

	public double availableDcKw() {
		return availableDcKw;
	}

	public double dcPowerKw() {
		return dcPowerKw;
	}

	/** Active power at the terminals, kW. */
	public double acPowerKw() {
		return acPowerKw;
	}

	/** A machine that makes power rather than passing it reads what it is sending out. */
	@Override
	public double getCurrentPower() {
		return acPowerKw;
	}

	public double dcVoltage() {
		return dcVoltage;
	}

	public double dcCurrent() {
		return dcCurrent;
	}

	public double cabinetTempC() {
		return cabinetTempC;
	}

	public double ambientTempC() {
		return ambientTempC;
	}

	public double efficiency() {
		return efficiency;
	}

	public boolean clipping() {
		return clipping;
	}

	public boolean derating() {
		return derating;
	}

	/** Direct-current input still free, in amps. */
	public double stringCurrentHeadroom() {
		return stringCurrentHeadroom;
	}

	public boolean stringsOutOfWindow() {
		return stringsOutOfWindow;
	}

	/** The voltage of the biggest source the window is holding off its knee, for the panel to name. */
	public double outOfWindowVolts() {
		return outOfWindowVolts;
	}

	/** Whether a limit set from outside, rather than the machine's own ceiling, is what it is held to. */
	public boolean curtailed() {
		return curtailed;
	}

	/** Why the last array or box was turned away, or NONE. */
	public Refusal refusal() {
		return refusal;
	}

	public int combinersConnected() {
		return combiners.size();
	}

	public int arraysConnected() {
		return arrays.size();
	}

	/** Where the arrays this inverter is wired to are, as of the last scan: safe to read from any thread. */
	public List<BlockPos> arrayPositions() {
		return publishedArrays;
	}

	public int stringsConnected() {
		return stringsConnected;
	}

	/** Strings the cabinet takes in all, through its own terminals, a fitted box and the boxes on its trunk together. */
	public int stringCapacity() {
		return spec().stringInputs();
	}

	public double energyTodayKwh() {
		return energyTodayKwh;
	}

	public double energyLifetimeKwh() {
		return energyLifetimeKwh;
	}

	/** DC nameplate of everything wired in, through boxes too, over the AC nameplate: the plant's own loading ratio. */
	public double dcAcRatio() {
		return spec().acPowerKw() <= 0.0 ? 0.0 : dcNameplateKw / spec().acPowerKw();
	}

	public double apparentKva() {
		return spec().apparentKva(Math.max(0.0, acPowerKw), powerFactorSetpoint);
	}

	public double reactiveKvar() {
		return spec().reactiveKvar(Math.max(0.0, acPowerKw), powerFactorSetpoint);
	}

	public double phaseCurrentA() {
		return spec().phaseCurrentA(apparentKva());
	}

	public double powerFactor() {
		return powerFactorSetpoint;
	}

	// ---- control ----

	public boolean isRunning() {
		return !stoppedByComputer && !stoppedByPlayer && !redstone.stopping();
	}

	public boolean isStoppedByComputer() {
		return stoppedByComputer;
	}

	public boolean isStoppedByPlayer() {
		return stoppedByPlayer;
	}

	public boolean isStoppedByRedstone() {
		return redstone.stopping();
	}

	public RedstoneMode getRedstoneMode() {
		return redstone.mode();
	}

	@Override
	public double getNameplateKw() {
		return spec().acPowerKw();
	}

	@Override
	public double getActivePowerLimit() {
		return activePowerLimitKw;
	}

	public void setStoppedByComputer(boolean stopped) {
		if (stoppedByComputer == stopped) return;

		stoppedByComputer = stopped;
		onControlChanged();
	}

	public void setStoppedByPlayer(boolean stopped) {
		if (stoppedByPlayer == stopped) return;

		stoppedByPlayer = stopped;
		onControlChanged();
	}

	public void setRedstoneMode(RedstoneMode mode) {
		if (redstone.mode(mode)) {
			onControlChanged();
		}
	}

	public void setActivePowerLimit(double limitKw) {
		double clamped = Mth.clamp(limitKw, 0.0, spec().acPowerKw());
		if (activePowerLimitKw == clamped) return;

		activePowerLimitKw = clamped;
		onControlChanged();
	}

	/** Sets the power factor the machine is asked to hold. */
	public void setPowerFactor(double factor) {
		double clamped = Mth.clamp(Math.abs(factor), spec().minPowerFactor(), 1.0);
		if (powerFactorSetpoint == clamped) return;

		powerFactorSetpoint = clamped;
		onControlChanged();
	}

	private void onControlChanged() {
		setChanged();
		ClientSync.now(this);
	}

	// ---- energy out ----

	private List<Direction> energyFaces() {
		return List.of(Direction.DOWN, getBlockState().getValue(PvInverterBlock.FACING).getOpposite());
	}

	private boolean isEnergyFace(@Nullable Direction side) {
		return side == null || energyFaces().contains(side);
	}

	/** Gross production this tick in Joules */
	public double getGrossJoulesPerTick() {
		return Math.max(0.0, acPowerKw) * EnergyBridge.JOULES_PER_KW;
	}

	/** What the wire network may carry away this tick, in kW: the output less what other mods' cables took. */
	@Override
	public double getGeneratedPower() {
		return budget.netKw(Math.max(0.0, acPowerKw));
	}

	/** Whether this generator is putting a disturbance onto the network. */
	public boolean isSurging() {
		return false;
	}

	/** What other mods' cables may still take this tick: the output, held to pvExportFraction and pvMaxJoulesPerTick. */
	@Override
	public double getAvailableJoules() {
		return level == null || level.isClientSide() ? 0.0 : budget.getAvailableJoules();
	}

	@Override
	public double getMaxJoulesPerTick() {
		return level == null || level.isClientSide() ? 0.0 : budget.getMaxJoulesPerTick();
	}

	@Override
	public double claimJoules(double joules, boolean simulate) {
		return level == null || level.isClientSide() ? 0.0 : budget.claimJoules(joules, simulate);
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

	// ---- persistence and syncing ----

	@Override
	protected void saveAdditional(@Nonnull CompoundTag tag) {
		super.saveAdditional(tag);

		// the meters are real accumulated state and have to survive a reload
		// reading, written only so a freshly loaded chunk shows a number rather than a zero
		tag.putDouble("energyToday", energyTodayKwh);
		tag.putDouble("energyLifetime", energyLifetimeKwh);
		tag.putLong("lastDay", lastDay);

		tag.putDouble("availableDc", availableDcKw);
		tag.putDouble("dcPower", dcPowerKw);
		tag.putDouble("acPower", acPowerKw);
		tag.putDouble("dcVoltage", dcVoltage);
		tag.putDouble("dcCurrent", dcCurrent);
		tag.putDouble("cabinetTemp", cabinetTempC);
		tag.putDouble("ambientTemp", ambientTempC);
		tag.putDouble("efficiency", efficiency);
		tag.putBoolean("clipping", clipping);
		tag.putBoolean("derating", derating);
		tag.putBoolean("stringsOutOfWindow", stringsOutOfWindow);
		tag.putInt("stringsConnected", stringsConnected);
		tag.putDouble("headroom", stringCurrentHeadroom);
		tag.putString("refusal", refusal.name());
		tag.putDouble("dcNameplate", dcNameplateKw);
		tag.putBoolean("curtailed", curtailed);
		tag.putDouble("outOfWindowVolts", outOfWindowVolts);

		tag.putBoolean("stoppedByComputer", stoppedByComputer);
		tag.putBoolean("stoppedByPlayer", stoppedByPlayer);
		redstone.save(tag);
		tag.putDouble("activePowerLimitKw", activePowerLimitKw);
		tag.putDouble("powerFactor", powerFactorSetpoint);

		ListTag positions = new ListTag();
		for (BlockPos pos : arrays) {
			positions.add(LongTag.valueOf(pos.asLong()));
		}

		tag.put("arrays", positions);

		long[] boxes = new long[combiners.size()];
		for (int i = 0; i < boxes.length; i++) {
			boxes[i] = combiners.get(i).asLong();
		}

		tag.putLongArray("combiners", boxes);
		if (integrated != null) tag.putString("combiner", integrated.id().toString());
	}

	@Override
	public void load(@Nonnull CompoundTag tag) {
		super.load(tag);

		energyTodayKwh = tag.getDouble("energyToday");
		energyLifetimeKwh = tag.getDouble("energyLifetime");
		lastDay = tag.contains("lastDay") ? tag.getLong("lastDay") : -1L;

		availableDcKw = tag.getDouble("availableDc");
		dcPowerKw = tag.getDouble("dcPower");
		acPowerKw = tag.getDouble("acPower");
		dcVoltage = tag.getDouble("dcVoltage");
		dcCurrent = tag.getDouble("dcCurrent");
		cabinetTempC = tag.getDouble("cabinetTemp");
		ambientTempC = tag.getDouble("ambientTemp");
		efficiency = tag.getDouble("efficiency");
		clipping = tag.getBoolean("clipping");
		derating = tag.getBoolean("derating");
		stringsOutOfWindow = tag.getBoolean("stringsOutOfWindow");
		stringsConnected = tag.getInt("stringsConnected");
		stringCurrentHeadroom = tag.getDouble("headroom");
		refusal = Refusal.byName(tag.getString("refusal"));
		dcNameplateKw = tag.getDouble("dcNameplate");
		curtailed = tag.getBoolean("curtailed");
		outOfWindowVolts = tag.getDouble("outOfWindowVolts");

		stoppedByComputer = tag.getBoolean("stoppedByComputer");
		stoppedByPlayer = tag.getBoolean("stoppedByPlayer");
		redstone.load(tag);
		// an inverter saved before this field existed is uncurtailed rather than limited to nothing,
		// which would silently switch it off on load
		activePowerLimitKw = tag.contains("activePowerLimitKw") ? tag.getDouble("activePowerLimitKw") : spec().acPowerKw();
		powerFactorSetpoint = tag.contains("powerFactor") ? tag.getDouble("powerFactor") : 1.0;

		arrays.clear();
		ListTag positions = tag.getList("arrays", 4);
		for (int i = 0; i < positions.size(); i++) {
			arrays.add(BlockPos.of(((LongTag) positions.get(i)).getAsLong()));
		}

		publishedArrays = List.copyOf(arrays);

		combiners.clear();
		for (long packed : tag.getLongArray("combiners")) {
			combiners.add(BlockPos.of(packed));
		}

		integrated = tag.contains("combiner") ? CombinerCatalog.byId(new ResourceLocation(tag.getString("combiner"))) : null;
	}

	/** Tells the arrays this inverter is letting them go. */
	void releaseArrays() {
		for (BlockPos pos : arrays) {
			PvArrayBlockEntity array = LoadedBlockEntities.find(level, pos, PvArrayBlockEntity.class);
			if (array != null) {
				array.releaseClaim(worldPosition);
			}
		}

		for (BlockPos pos : combiners) {
			PvCombinerBlockEntity box = LoadedBlockEntities.find(level, pos, PvCombinerBlockEntity.class);
			if (box != null) {
				box.releaseClaim(worldPosition);
			}
		}
	}

	@Override
	public String fittingType() {
		return FittingTypes.PV_INVERTER;
	}

	/** A generator, like a turbine. */
	@Override
	public boolean feeds(InsulatorHost other) {
		return other instanceof ElectricCabinBlockEntity || other instanceof PvInverterBlockEntity
				|| other instanceof TransformerBlockEntity;
	}

	/** An inverter's output goes to the plant's collector network, which is 33 kV. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() == ConductorSpec.VoltageClass.MEDIUM;
	}

	@Override
	public String powerType(String partName) {
		return "output";
	}

	/** Nothing to tell it: an inverter's reading is what it makes, not what reaches it. */
	@Override
	public void deliverPower(double power) {
	}
}
