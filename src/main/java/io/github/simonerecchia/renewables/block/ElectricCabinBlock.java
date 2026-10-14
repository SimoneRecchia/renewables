package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The electric cabin: nine cells, one input and one output insulator.
 *
 * STUB: owner B (grid-blocks.md section 4).
 */
public class ElectricCabinBlock extends MachineBlock {
	/** The facing cab.obj is modelled at. */
	public static final Direction AUTHORED = Direction.NORTH;

	public ElectricCabinBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return ModBlockEntities.ELECTRIC_CABIN.get().create(pos, state);
	}
}
