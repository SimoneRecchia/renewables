package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.grid.WireLedger;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shared body of a machine that a block entity renderer draws: a horizontal facing, placed turned towards
 * the player, invisible to the block renderer, letting light through, and fitted with the cells it fills.
 *
 * What a machine gets by extending this, so that it does not have to say it again:
 * <ul>
 * <li>its shape, and its collision, are its own cell of the table {@link #shellCells} returns, turned with the
 *     facing (a machine of one cell has a table of one, and a table that does not turn is wrong at three
 *     facings out of four - the tool that prints it checks);</li>
 * <li>the other cells are laid when it is placed, taken back when it goes, and mended every
 *     {@link MachineShell#HEAL_INTERVAL} ticks by a machine that has any;</li>
 * <li>the wires hung on it are taken down, and the conductor refunded, when it goes - a change of
 *     <em>state</em> (a switch swinging open) is not going, and does neither.</li>
 * </ul>
 *
 * The family blocks that were written before this keep their own copy of these few lines; new machines extend
 * this one.
 */
public abstract class MachineBlock extends Block implements EntityBlock, MachineShell {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	protected MachineBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** The model's front faces the player, so the block faces back at them. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	// ---- the cells ----

	/** A machine that fits its own block: one cell, all of it, which is what a block without a table has always been. */
	private static final List<Cell> OWN_CELL_ONLY = List.of(new Cell(0, 0, 0, Shapes.block()));

	/** What the machine fills.  Overridden by every machine that is not simply a block; the default is the whole of its own cell. */
	@Override
	public List<Cell> shellCells(BlockState state) {
		return OWN_CELL_ONLY;
	}

	/** Which way the model was drawn; a machine with a table of cells says, and the default is north like every model in the mod. */
	@Override
	public Direction shellAuthored() {
		return Direction.NORTH;
	}

	@Override
	public Direction shellFacing(BlockState state) {
		return state.getValue(FACING);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shellShape(state);
	}

	/** Lays the cells.  Runs for a change of state as well as a placement; laying is idempotent. */
	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		MachineShell.place(level, pos, state);
	}

	/**
	 * Takes back the cells and the wires when the block <em>changes kind</em>; a change of state only
	 * is neither.  The wires are read off the block entity before it is removed.
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			MachineShell.clear(level, pos, state);
			if (!level.isClientSide && level instanceof ServerLevel serverLevel
					&& level.getBlockEntity(pos) instanceof InsulatorHost host) {
				WireLedger.removeAttachedTo(serverLevel, pos, host.getInsulatorIds());
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	/** A machine with more than its own cell has cells that can go missing; one with just its own has nothing to mend. */
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide || shellCells(state).size() < 2) return null;

		return (lvl, pos, blockState, blockEntity) -> MachineShell.heal(lvl, pos, blockState);
	}

	// ---- turning ----

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	// ---- looks ----

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}
}
