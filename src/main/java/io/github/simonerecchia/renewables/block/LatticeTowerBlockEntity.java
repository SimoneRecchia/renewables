package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.TowerSpec;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.model.PartBox;
import io.github.simonerecchia.renewables.model.PartBoxes;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A lattice tower's six fittings.  One block entity for all three duties; the spec is read off the block.
 *
 * Its anchors differ from every other machine's: a string hangs or lies along the line, and the bundle is held
 * by the conductor clamps at its end, not by the porcelain.  So a wire lands in the middle of the phase's clamp
 * group ({@code clamp_N} of the model, in {@code fittings.json}): under a suspension string, between a tension
 * tower's two dead-ends, and at the outer end of a terminal tower's.  A model without clamps falls back to the
 * porcelain's underside.  Mending the tower's cells is its block's ticker.
 */
public class LatticeTowerBlockEntity extends MachineBlockEntity {
	public LatticeTowerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LATTICE_TOWER.get(), pos, state);
	}

	public TowerSpec spec() {
		return ((LatticeTowerBlock) getBlockState().getBlock()).spec();
	}

	@Nullable
	@Override
	protected Vec3 localAnchor(int index) {
		PartBox clamps = PartBoxes.box(getBlockState().getBlock(), "clamp_" + (index + 1) + "_fitting");
		if (clamps != null) {
			double x = (clamps.min().x() + clamps.max().x()) * 0.5;
			double y = (clamps.min().y() + clamps.max().y()) * 0.5;
			double z = spec().duty() == TowerSpec.Duty.TERMINAL ? clamps.min().z() : (clamps.min().z() + clamps.max().z()) * 0.5;
			return new Vec3(x, y, z);
		}

		PartBox box = fittingBox(index);
		if (box == null) return null;

		double x = (box.min().x() + box.max().x()) * 0.5;
		double z = (box.min().z() + box.max().z()) * 0.5;
		return switch (spec().duty()) {
			case SUSPENSION, TENSION -> new Vec3(x, box.min().y(), z);
			case TERMINAL -> new Vec3(x, (box.min().y() + box.max().y()) * 0.5, box.min().z());
		};
	}

	/**
	 * A tension tower's clamp group spans both dead-ends of a phase, and the anchor is its middle; a span ends at the
	 * dead-end on its own side, the nearer of the group's two ends to where it goes, so it does not run on along the
	 * string to the middle of the arm.
	 */
	@Nullable
	@Override
	public Vec3 getWirePosition(int index, Vec3 toward) {
		Vec3 middle = getWirePosition(index);
		if (middle == null || spec().duty() != TowerSpec.Duty.TENSION) return middle;

		PartBox clamps = PartBoxes.box(getBlockState().getBlock(), "clamp_" + (index + 1) + "_fitting");
		if (clamps == null) return middle;

		double x = (clamps.min().x() + clamps.max().x()) * 0.5;
		double y = (clamps.min().y() + clamps.max().y()) * 0.5;
		Vec3 back = modelToWorld(new Vec3(x, y, clamps.min().z()));
		Vec3 front = modelToWorld(new Vec3(x, y, clamps.max().z()));
		return back.distanceToSqr(toward) <= front.distanceToSqr(toward) ? back : front;
	}

	@Override
	public String fittingType() {
		return FittingTypes.LATTICE_TOWER;
	}

	@Override
	public boolean feeds(InsulatorHost other) {
		return other instanceof LatticeTowerBlockEntity || other instanceof ElectricCabinBlockEntity
				|| other instanceof UtilityPoleBlockEntity || other instanceof GroundConductorBlockEntity
				|| other instanceof TransformerBlockEntity;
	}

	/** The duty's ruling span: a suspension tower holds a shorter one than a tension or terminal tower. */
	@Override
	public double maxSpanBlocks() {
		return spec().maxSpan();
	}

	/** Transmission only. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() == ConductorSpec.VoltageClass.HIGH;
	}
}
