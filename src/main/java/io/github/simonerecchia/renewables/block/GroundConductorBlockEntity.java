package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The one fitting a length of ground-laid conductor offers a span.
 *
 * A run is laid up to a structure and the span comes off it, which is exactly what the dead-end clamp on
 * the {@code end} piece is for - so this block entity exists to give that clamp an insulator id, and a
 * span may be strung from a run to a tower or from a run to a run.
 *
 * One fitting rather than one per sub-conductor: a bundle is dead-ended in one place and the four clamps
 * are the same termination, which is also how {@code WireRenderer} draws
 * a bundle - one connection, four sub-conductors.
 *
 * A run is drawn from block models, not from an OBJ, so it has no model definition and no entry in the
 * fittings table: it says its own fitting count and where the clamp is, and everything else is the machine's.
 */
public class GroundConductorBlockEntity extends MachineBlockEntity {
	/** Where the dead-end clamp's palm is above the block's floor, from gen_conductor_models' CLAMP. */
	private static final double CLAMP_Y = 1.55 / 16.0;

	public GroundConductorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.GROUND_CONDUCTOR.get(), pos, state);
	}

	@Nullable
	public ConductorSpec spec() {
		if (getBlockState().getBlock() instanceof GroundConductorBlock block) return block.spec();

		return null;
	}

	@Override
	protected int fittingCount() {
		return 1;
	}

	/** The clamp, on the block's vertical axis whichever way the run turns. */
	@Nullable
	@Override
	protected Vec3 localAnchor(int index) {
		return index == 0 ? new Vec3(0.0, CLAMP_Y, 0.0) : null;
	}

	@Override
	public String fittingType() {
		return FittingTypes.GROUND_CONDUCTOR;
	}

	/**
	 * A run carries power on to whatever it is laid up to, and takes it from a span.
	 *
	 * Only to the same conductor where the other end is a run, for the reason the blocks do not join
	 * either: a street bundle does not splice onto a transmission phase.
	 */
	@Override
	public boolean feeds(InsulatorHost other) {
		if (other instanceof GroundConductorBlockEntity run) {
			ConductorSpec mine = spec();
			ConductorSpec theirs = run.spec();
			return mine != null && theirs != null && mine.id().equals(theirs.id());
		}

		return other instanceof LatticeTowerBlockEntity || other instanceof UtilityPoleBlockEntity
				|| other instanceof ElectricCabinBlockEntity || other instanceof PowerBoxBlockEntity
				|| other instanceof TransformerBlockEntity;
	}

	/** Its own product and nothing else: a run of street bundle is not where a transmission phase lands. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		ConductorSpec mine = spec();
		return mine == null || mine.id().equals(conductor.id());
	}
}
