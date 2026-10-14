package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The end of a line: turns what reaches it into Forge Energy and powers every consumer in its field.
 *
 * STUB: owner B (grid.md section 12, grid-blocks.md section 5): the tick, the FE capability, the field of
 * {@code ServerConfig.powerBoxRadius()} blocks and the delivery to {@code IElectricPowerConsumer}s.
 */
public class PowerBoxBlockEntity extends MachineBlockEntity {
	public PowerBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.POWER_BOX.get(), pos, state);
	}

	@Override
	public String fittingType() {
		return FittingTypes.POWER_BOX;
	}

	/** Nothing leaves a kiosk by wire. */
	@Override
	public boolean feeds(InsulatorHost other) {
		return false;
	}

	/** Street bundle only. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() == ConductorSpec.VoltageClass.LOW;
	}
}
