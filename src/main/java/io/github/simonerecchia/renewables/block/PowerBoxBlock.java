package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The power box (the kiosk): one cell, one insulator on the roof, a Forge Energy source and a power field.
 *
 * <b>Two ways to stand.</b>  On the ground it is the whole cabinet on its plinth.  Clicked onto the side of a
 * block it is {@link #MOUNTED}: facing away from the wall, with no plinth and no conduit, and pushed back by
 * {@link #BACKSET} so that its back sits on the wall.  The collision follows: it is the ground shape without the
 * plinth's layer, moved back by the same amount, and the wires and the picking follow the model by being an
 * {@link io.github.simonerecchia.renewables.model.ExtraPose} (see the block entity).
 *
 * A mounted kiosk lives on its wall: lose the wall and it comes off as an item, like any block that hangs.
 */
public class PowerBoxBlock extends MachineBlock {
	/** The facing power_box.obj is modelled at: the doors are on the -z side. */
	public static final Direction AUTHORED = Direction.NORTH;

	/** Whether it hangs on a wall rather than standing on the ground. */
	public static final BooleanProperty MOUNTED = BooleanProperty.create("mounted");

	/** How far a mounted kiosk is moved back, in blocks along the model's z: the plinth's depth taken out of the block. */
	public static final double BACKSET = 0.28;

	/** How tall the plinth is, in pixels: the layer a mounted kiosk does not have. */
	private static final double PLINTH_PIXELS = 0.88;

	// ---- 1 cell(s), printed by tools/check_hitboxes.py --java ----
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(2.53, 0.00, 4.61, 13.47, 11.81, 11.52),
					Block.box(6.67, 12.19, 6.67, 9.33, 14.26, 9.33),
					Block.box(6.80, 11.23, 6.80, 9.20, 12.18, 9.20))));

	/** The mounted shape: the ground one with the plinth's layer cut off, then moved back to the wall. */
	private static final List<Cell> WALL_CELLS = List.of(
			new Cell(0, 0, 0, mounted(CELLS.get(0).shape(0))));

	public PowerBoxBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(MOUNTED, false));
	}

	/**
	 * The ground kiosk's shape with everything below the top of the plinth removed (a wall-mounted one has
	 * none), moved back.  The whole layer comes off, not just the plinth's own box: the cabinet's box starts at
	 * the ground, and cutting only the slab would have left a frame a fraction of a pixel wide round the cabinet
	 * base.
	 */
	static VoxelShape mounted(VoxelShape ground) {
		VoxelShape plinth = Block.box(0.0, 0.0, 0.0, 16.0, PLINTH_PIXELS, 16.0);
		return Shapes.join(ground, plinth, BooleanOp.ONLY_FIRST).move(0.0, 0.0, BACKSET);
	}

	public static boolean mounted(BlockState state) {
		return state.getValue(MOUNTED);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(MOUNTED);
	}

	@Override
	public List<Cell> shellCells(BlockState state) {
		return mounted(state) ? WALL_CELLS : CELLS;
	}

	@Override
	public Direction shellAuthored() {
		return AUTHORED;
	}

	// ---- placing ----

	/**
	 * A click on the side of a block hangs the kiosk there, doors outward; a click on the top or the underside
	 * stands it on the ground facing the player (the underside gives a kiosk with nothing under it, which is a
	 * ground kiosk and not a hanging one).  Null - no placement - where it would be hung on something that will
	 * not hold it.
	 */
	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		BlockState state = face.getAxis().isHorizontal()
				? defaultBlockState().setValue(FACING, face).setValue(MOUNTED, true)
				: defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(MOUNTED, false);

		return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
	}

	/** Mounted, the block behind must be solid on the side the kiosk is on; standing, nothing is asked. */
	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (!mounted(state)) return true;

		Direction facing = state.getValue(FACING);
		BlockPos wall = pos.relative(facing.getOpposite());
		return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
	}

	/**
	 * Loses its wall: it goes, and the machine's own removal takes the wires down and puts the item on the ground.
	 * Returning air from here is how a hanging block lets go - the drop is made by the caller.
	 */
	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
			BlockPos pos, BlockPos neighbourPos) {
		if (mounted(state) && direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}

		return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
	}

	// ---- the machine ----

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return ModBlockEntities.POWER_BOX.get().create(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide || type != ModBlockEntities.POWER_BOX.get()) return null;

		return (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof PowerBoxBlockEntity box && lvl instanceof ServerLevel serverLevel) box.serverTick(serverLevel);
		};
	}

	/** Gone: the lamps it lit go dark, and the consumers are told the power has stopped. */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
				&& level.getBlockEntity(pos) instanceof PowerBoxBlockEntity box) {
			box.fieldOff(serverLevel);
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
