package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The power box (kiosk): one cell, one insulator on the roof, a Forge Energy source and a power field.
 *
 * STUB: owner B (grid-blocks.md section 5, grid.md section 12).
 */
public class PowerBoxBlock extends MachineBlock {
	/** The facing power_box.obj is modelled at. */
	public static final Direction AUTHORED = Direction.NORTH;

	public PowerBoxBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return ModBlockEntities.POWER_BOX.get().create(pos, state);
	}
}
