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
 * What it keeps as its reading is the power <em>going in</em>.  What comes out is that less the losses
 * ({@link TransformerSpec#lossKw}: a core loss it burns whatever is drawn, and a share of the load), and the
 * grid applies it to the flow that crosses the transformer: {@link TransformerFlow#output} is the one place the figure is
 * worked out, so the wrench's reading and the grid's cannot differ.
 */
public class TransformerBlockEntity extends MachineBlockEntity {
	public TransformerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TRANSFORMER.get(), pos, state);
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
