package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.TransformerSpec;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A transformer's bushings, low-voltage side first.
 *
 * STUB: owner B (grid-blocks.md section 7).  The conductor classes and the {@code feeds} list are the table
 * of section 11; applying the losses to the flow is the grid's (owner G).
 */
public class TransformerBlockEntity extends MachineBlockEntity {
	public TransformerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TRANSFORMER.get(), pos, state);
	}

	public TransformerSpec spec() {
		return ((TransformerBlock) getBlockState().getBlock()).spec();
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
