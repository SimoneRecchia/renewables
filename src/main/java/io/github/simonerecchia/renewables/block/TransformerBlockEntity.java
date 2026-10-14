package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.TransformerSpec;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A transformer's bushings, low-voltage side first.
 *
 * What it keeps as its reading is the power <em>going in</em>.  What comes out is that less the losses
 * ({@link TransformerSpec#lossKw}: a core loss it burns whatever is drawn, and the windings' loss, which goes with the
 * square of the load), and the grid applies the same figure to the flow that crosses the transformer through
 * {@link #throughLossKw}, so the wrench's reading and the grid's cannot differ.
 *
 * Past its rating it does not limit anything: it heats, and its protection takes it off the network
 * ({@link TransformerThermal}).  Tripped, every bushing is a bus of its own, so nothing crosses it until a hand puts
 * it back.
 */
public class TransformerBlockEntity extends MachineBlockEntity {
	/** How often the temperatures are worked out, in ticks: a second, which is nothing to a time constant of minutes. */
	private static final int THERMAL_TICKS = 20;
	/** The hot spot has to move this far, K, before the chunk is marked to be saved for it. */
	private static final double SAVE_DRIFT_K = 1.0;

	private final TransformerThermal thermal;
	private double savedHotSpot = TransformerThermal.AMBIENT_C;

	public TransformerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TRANSFORMER.get(), pos, state);
		thermal = new TransformerThermal(((TransformerBlock) state.getBlock()).spec());
	}

	public TransformerSpec spec() {
		return ((TransformerBlock) getBlockState().getBlock()).spec();
	}

	/** What it costs to carry the power that is going in now, in kW. */
	public double lossKw() {
		return spec().lossKw(getCurrentPower());
	}

	/** What comes out for what goes in now: {@link TransformerFlow#output} of the delivered power. */
	public double outputKw() {
		return TransformerFlow.output(spec(), getCurrentPower());
	}

	public TransformerThermal thermal() {
		return thermal;
	}

	/** Once a second: the temperatures at what is going in, and the protection on them. */
	public void serverTick(ServerLevel level) {
		if (level.getGameTime() % THERMAL_TICKS != 0) return;

		if (thermal.step(getCurrentPower() / spec().ratingKva(), THERMAL_TICKS / 20.0)) {
			level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.9f, 0.8f);
			markThermal();
		} else if (Math.abs(thermal.hotSpot() - savedHotSpot) > SAVE_DRIFT_K) {
			markThermal();
		}
	}

	/**
	 * A hand on a tripped transformer: back in service if it has cooled enough.
	 *
	 * @return whether it is in service now
	 */
	public boolean resetProtection() {
		boolean wasTripped = thermal.tripped();
		boolean inService = thermal.reset();
		if (wasTripped && inService) markThermal();

		return inService;
	}

	private void markThermal() {
		savedHotSpot = thermal.hotSpot();
		setChanged();
	}

	/** Every bushing is one bus in service; tripped, each is a bus of its own and nothing crosses the tank. */
	@Override
	public int busOf(int index) {
		return thermal.tripped() ? index + 1 : 0;
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		ListTag temperatures = new ListTag();
		for (double value : thermal.state()) temperatures.add(DoubleTag.valueOf(value));
		tag.put("thermal", temperatures);
		tag.putString("trip", thermal.trip().name());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		ListTag temperatures = tag.getList("thermal", Tag.TAG_DOUBLE);
		double[] state = new double[temperatures.size()];
		for (int i = 0; i < state.length; i++) state[i] = temperatures.getDouble(i);

		TransformerThermal.Trip trip = TransformerThermal.Trip.NONE;
		for (TransformerThermal.Trip each : TransformerThermal.Trip.values()) {
			if (each.name().equals(tag.getString("trip"))) trip = each;
		}

		thermal.restore(state, trip);
		savedHotSpot = thermal.hotSpot();
	}

	/** The one machine on the grid that burns something carrying power across itself. */
	@Override
	public boolean losesInPassing() {
		return true;
	}

	@Override
	public double throughLossKw(double throughKw) {
		return spec().lossKw(throughKw);
	}

	@Override
	public String fittingType() {
		return FittingTypes.TRANSFORMER;
	}

	@Override
	public boolean feeds(InsulatorHost other) {
		if (spec().duty() == TransformerSpec.Duty.MACHINE) {
			return other instanceof ElectricCabinBlockEntity || other instanceof TransformerBlockEntity
					|| other instanceof GroundConductorBlockEntity;
		}

		return other instanceof LatticeTowerBlockEntity || other instanceof GroundConductorBlockEntity;
	}

	/** A machine unit is medium voltage on both sides of its tank; a substation takes medium on its first three bushings and high on the rest. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		if (spec().duty() == TransformerSpec.Duty.MACHINE) return conductor.voltageClass() == ConductorSpec.VoltageClass.MEDIUM;

		return conductor.voltageClass() == (index < 3 ? ConductorSpec.VoltageClass.MEDIUM : ConductorSpec.VoltageClass.HIGH);
	}
}
