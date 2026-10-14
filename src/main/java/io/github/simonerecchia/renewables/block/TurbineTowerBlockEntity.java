package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import io.github.simonerecchia.renewables.Renewables;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

/** A tower block's presence, and nothing else. */
public class TurbineTowerBlockEntity extends BlockEntity {
	/** Last machine found above this block, so the walk up is not repeated on every query. */
	@Nullable
	private BlockPos machinePos;

	public TurbineTowerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TURBINE_TOWER.get(), pos, state);
	}

	/** The foot of a tower carries its machine's cables. */
	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
		WindTurbineBlockEntity machine = machine();
		if (machine != null) return machine.getCapability(cap, side);

		return super.getCapability(cap, side);
	}

	/**
	 * The machine this block carries the cables for, or null if it carries none.  The remembered position is only
	 * believed while the turbine there still has this block for its foot: a turbine there is not enough, because the
	 * pile under it may have been rebuilt to another height, and the foot of one pile would then be offering the cables
	 * the energy of a machine standing on another.
	 */
	@Nullable
	private WindTurbineBlockEntity machine() {
		if (level == null || !TurbineTowerBlock.isFoot(level, worldPosition)) return null;

		WindTurbineBlockEntity turbine = machinePos != null && level.getBlockEntity(machinePos) instanceof WindTurbineBlockEntity at ? at : null;
		if (turbine == null || !TurbineTowerBlock.footBelow(level, machinePos).equals(worldPosition)) {
			machinePos = TurbineTowerBlock.findTurbineAbove(level, worldPosition);
			turbine = machinePos != null && level.getBlockEntity(machinePos) instanceof WindTurbineBlockEntity at ? at : null;
		}

		return turbine;
	}

	@Override
	public void onLoad() {
		super.onLoad();
		ClientTracking.track(this);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		ClientTracking.untrack(this);
	}
}
