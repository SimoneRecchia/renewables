package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The utility pole: fifteen cells tall and wide, eight pin insulators, and a configuration screen.
 *
 * STUB: owner B.  This is the minimum that registers, places and holds a block entity; the shell cells,
 * shape, configuration screen, breaking and the tick that mends the cells are grid-blocks.md section 3.
 */
public class UtilityPoleBlock extends MachineBlock {
	/** The facing utility_pole.obj is modelled at. */
	public static final Direction AUTHORED = Direction.NORTH;

	public UtilityPoleBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return ModBlockEntities.UTILITY_POLE.get().create(pos, state);
	}
}
