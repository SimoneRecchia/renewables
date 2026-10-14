package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.SwitchgearSpec;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A switch's six fittings: three on the line side, three on the load side.  One bus while closed, two while open.
 *
 * STUB: owner B (grid-blocks.md section 6).  The bus split and the pass-through are real: they are the whole
 * reason a switch is a switch to the grid.
 */
public class SwitchgearBlockEntity extends MachineBlockEntity {
	public SwitchgearBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SWITCHGEAR.get(), pos, state);
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

	/** Closed, one bus; open, the line side (the first half) and the load side are separate. */
	@Override
	public int busOf(int index) {
		if (!SwitchgearBlock.open(getBlockState())) return 0;

		return index < spec().poles() ? 0 : 1;
	}

	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() == spec().voltageClass();
	}
}
