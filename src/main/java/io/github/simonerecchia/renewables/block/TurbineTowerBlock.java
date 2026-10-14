package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One segment of a turbine tower.  Stacked to the height a turbine wants, with the machine seated on top.
 *
 * STUB: owner W (wind.md): collision, the tower-height limit of sixteen, collapse.  The two column queries are
 * real, because the machine, its item and the computer peripheral already depend on them.
 */
public class TurbineTowerBlock extends Block implements EntityBlock {
	/** Nothing stands above this many segments. */
	public static final int MAX_SEGMENTS = 16;

	public TurbineTowerBlock(Properties properties) {
		super(properties);
	}

	/** How many tower segments are directly below {@code pos}. */
	public static int countBelow(BlockGetter level, BlockPos pos) {
		int count = 0;
		BlockPos.MutableBlockPos cursor = pos.mutable().move(0, -1, 0);
		while (count < MAX_SEGMENTS && level.getBlockState(cursor).getBlock() instanceof TurbineTowerBlock) {
			count++;
			cursor.move(0, -1, 0);
		}

		return count;
	}

	/** The turbine seated on the top of the column that {@code pos} is in, or null if there is none. */
	@Nullable
	public static BlockPos findTurbineAbove(BlockGetter level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = pos.mutable().move(0, 1, 0);
		for (int i = 0; i < MAX_SEGMENTS + 1; i++) {
			BlockState state = level.getBlockState(cursor);
			if (state.getBlock() instanceof WindTurbineBlock) return cursor.immutable();
			if (!(state.getBlock() instanceof TurbineTowerBlock)) return null;

			cursor.move(0, 1, 0);
		}

		return null;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return ModBlockEntities.TURBINE_TOWER.get().create(pos, state);
	}
}
