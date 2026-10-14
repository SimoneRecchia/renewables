package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The cabin's two fittings: the input collects medium-voltage power, the output feeds the line on.
 *
 * STUB: owner B (grid-blocks.md section 4).  The roles and the conductor classes below are the table of
 * section 11 and are what the grid relies on already.
 */
public class ElectricCabinBlockEntity extends MachineBlockEntity {
	public ElectricCabinBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ELECTRIC_CABIN.get(), pos, state);
	}

	@Override
	public String fittingType() {
		return FittingTypes.ELECTRIC_CABIN;
	}

	@Override
	public boolean feeds(InsulatorHost other) {
		return other instanceof UtilityPoleBlockEntity || other instanceof LatticeTowerBlockEntity
				|| other instanceof TransformerBlockEntity || other instanceof GroundConductorBlockEntity;
	}

	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() != ConductorSpec.VoltageClass.HIGH;
	}

	/** The only machine whose fittings are not both ways: the input takes power in, the output sends it out. */
	@Override
	public String powerType(String partName) {
		if (partName.contains("input")) return FittingTypes.POWER_INPUT;
		if (partName.contains("output")) return FittingTypes.POWER_OUTPUT;

		return FittingTypes.POWER_BIDIRECTIONAL;
	}
}
