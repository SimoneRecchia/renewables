package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.SwitchgearSpec;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A switch's six fittings: three on the line side, three on the load side, two buses that the contacts join
 * while it is closed.
 *
 * The bus split and the pass-through are the whole reason a switch is a switch to the grid: closed, power crosses
 * it as it crosses any machine; open, the two sides are two nodes at one position with nothing between them.  Its
 * reading is what crosses the contacts, so a disconnector whose terminals carry a line on to somewhere else, or one
 * in front of an open breaker, has no load on it.
 *
 * It also remembers what the redstone signal was the last time it was looked at, so that only a <em>change</em>
 * moves the switch, and a breaker, which is the only one with a ticker, looks at what it carries once a second.
 */
public class SwitchgearBlockEntity extends MachineBlockEntity {
	/** The signal at the last neighbour update; the edge is measured from here. */
	private boolean lastSignal;

	public SwitchgearBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SWITCHGEAR.get(), pos, state);
	}

	/**
	 * A neighbour changed: if the redstone signal changed with it, the switch opens (it appeared) or closes (it
	 * went).  Otherwise nothing, so a switch worked by hand is not undone by the next block placed beside it.
	 */
	public void onNeighbourChanged(ServerLevel level) {
		boolean signal = level.hasNeighborSignal(worldPosition);
		int edge = SwitchLogic.edge(lastSignal, signal);
		if (edge == 0) return;

		lastSignal = signal;
		setChanged();
		SwitchgearBlock.setOpen(level, worldPosition, getBlockState(), edge > 0);
	}

	/** A breaker, closed and carrying more than it is rated for, opens itself, with the sound of it. */
	public void serverTick(ServerLevel level) {
		if (!SwitchLogic.isTripTick(level.getGameTime())) return;
		if (SwitchgearBlock.open(getBlockState()) || !spec().breaksLoad()) return;
		if (!SwitchLogic.overRated(getCurrentPower(), spec().ratedKw())) return;

		if (SwitchgearBlock.setOpen(level, worldPosition, getBlockState(), true)) {
			level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.4f);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("lastSignal", lastSignal);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		lastSignal = tag.getBoolean("lastSignal");
	}

	public SwitchgearSpec spec() {
		return ((SwitchgearBlock) getBlockState().getBlock()).spec();
	}

	@Override
	public String fittingType() {
		return FittingTypes.SWITCHGEAR;
	}

	/** A switch takes the role of the line it is in, so it may be fed from anywhere its voltage allows. */
	@Override
	public boolean feeds(InsulatorHost other) {
		return true;
	}

	@Override
	public boolean passesThrough() {
		return true;
	}

	/** The line side (the first half) and the load side, which the contacts join while the switch is closed. */
	@Override
	public int busOf(int index) {
		return index < spec().poles() ? 0 : 1;
	}

	/** What the grid then delivers here is what crosses the contacts, which is what the interlock and the trip read. */
	@Override
	public boolean closed() {
		return !SwitchgearBlock.open(getBlockState());
	}

	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() == spec().voltageClass();
	}
}
