package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.TowerSpec;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.model.ModelDefinition;
import io.github.simonerecchia.renewables.model.ModelDefinitions;
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
 * Its anchors differ from every other machine's: a chain hangs, so a wire is taken from the underside of a
 * suspension or tension tower's fitting and from the far end (smallest z) of a terminal tower's, and not from
 * the top of the porcelain.  Mending the tower's cells is its block's ticker.
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
	protected Vec3 computeAnchor(int index) {
		ModelDefinition definition = ModelDefinitions.of(getBlockState().getBlock());
		if (definition == null || index < 0 || index >= definition.insulators().size()) return null;

		PartBox box = PartBoxes.box(getBlockState().getBlock(), definition.insulators().get(index));
		if (box == null) return null;

		double x = (box.min().x() + box.max().x()) * 0.5;
		double z = (box.min().z() + box.max().z()) * 0.5;
		Vec3 local = switch (spec().duty()) {
			case SUSPENSION, TENSION -> new Vec3(x, box.min().y(), z);
			case TERMINAL -> new Vec3(x, (box.min().y() + box.max().y()) * 0.5, box.min().z());
		};

		return Vec3.atLowerCornerOf(getBlockPos()).add(0.5, 0.0, 0.5)
				.add(ModelFacing.turned(local, definition.authored(), facing(definition.authored())));
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

	/** Transmission only. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() == ConductorSpec.VoltageClass.HIGH;
	}
}
