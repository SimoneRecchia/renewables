package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;
import io.github.simonerecchia.renewables.wind.TurbineDynamics;
import io.github.simonerecchia.renewables.wind.TurbineGeometry;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wind turbine of any model: the machine that sits on top of a stack of tower blocks.
 *
 * The block is only the nacelle.  The tower under it, the rotor in front of it and the cable at its foot are drawn by
 * the renderer from the block entity's readings, and only the nacelle is solid: the blades pass through whatever is
 * in their way and nothing about them can be selected.  The model's front faces the player who placed it; the nacelle
 * then turns with the wind, and its collision with it.
 */
public class WindTurbineBlock extends MachineBlock {
	/** The facing wind_turbine.obj is modelled at. */
	public static final Direction AUTHORED = Direction.SOUTH;
	/** The nacelle's collision follows its yaw in steps of this many degrees, one shape kept per step. */
	private static final int SHAPE_STEP_DEGREES = 5;
	private static final int SHAPE_STEPS = 360 / SHAPE_STEP_DEGREES;

	private final TurbineSpec spec;
	/** The nacelle as collision and as selection at each step of yaw: the hull and the cooler, turned and cut to the block. */
	private final VoxelShape[] shapes = new VoxelShape[SHAPE_STEPS];

	/** The shape follows the nacelle's yaw, which is the block entity's, so it is asked for every time rather than kept per state. */
	public WindTurbineBlock(Properties properties, TurbineSpec spec) {
		super(properties.dynamicShape());
		this.spec = spec;
	}

	public TurbineSpec spec() {
		return spec;
	}

	/**
	 * The nacelle where it is drawn: turned by the yaw the renderer turns it by, not left on the block's facing, which put
	 * the collision across the drawn nacelle once it had swung a quarter turn - air to bump into, steel to walk through.
	 * The client turns it by the yaw it was last sent, the server by its own; the step is coarser than the gap.
	 */
	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		double modelYaw = level.getBlockEntity(pos) instanceof WindTurbineBlockEntity turbine
				? TurbineDynamics.modelRotationDegrees(turbine.getNacelleDirection())
				: ModelFacing.degrees(AUTHORED, state.getValue(FACING));
		int step = Math.floorMod((int) Math.round(modelYaw / SHAPE_STEP_DEGREES), SHAPE_STEPS);

		VoxelShape shape = shapes[step];
		if (shape == null) {
			shape = Shapes.empty();
			for (double[] b : TurbineGeometry.nacelleBoxes(spec, step * (double) SHAPE_STEP_DEGREES)) {
				shape = Shapes.or(shape, Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5]));
			}

			shapes[step] = shape = shape.optimize();
		}

		return shape;
	}

	// ---- standing on a tower ----

	/** A turbine can only be where the tower under it is a height this model is sold on. */
	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return spec.acceptsTowerHeight(TurbineTowerBlock.countBelow(level, pos));
	}

	/** If the tower under it stops being one this model fits, the turbine falls off it, and drops. */
	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (direction == Direction.DOWN && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();

		return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
	}

	/**
	 * The cables leave at the foot of the tower and a computer may stand against any block of it, so when a turbine
	 * appears or goes the whole tower is told: what it offers to a neighbouring cable or computer has just changed, and a
	 * neighbour is only told to look again by being told something changed.
	 */
	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide() && !oldState.is(state.getBlock())) notifyTower(level, pos);
	}

	/**
	 * When the turbine is replaced: the tower first, then (in MachineBlock) every span hung from its fitting, and only
	 * then the block entity goes, because the spans are found through the ids it holds.
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && !level.isClientSide()) notifyTower(level, pos);

		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	/**
	 * Tells the tower under {@code machinePos} that what it carries has changed: the foot drops what a neighbour cached
	 * about its energy and nudges its neighbours the way a comparator update would, and every segment tells the blocks
	 * beside it - a computer against the tenth block of the tower otherwise went on talking to a turbine that had gone,
	 * or never saw the one put in its place.
	 */
	private static void notifyTower(Level level, BlockPos machinePos) {
		BlockPos foot = TurbineTowerBlock.footBelow(level, machinePos);

		BlockEntity footEntity = level.getBlockEntity(foot);
		if (footEntity != null) {
			footEntity.invalidateCaps();
			footEntity.reviveCaps();
		}

		level.updateNeighbourForOutputSignal(foot, level.getBlockState(foot).getBlock());
		for (BlockPos segment = foot; segment.getY() < machinePos.getY(); segment = segment.above()) {
			for (Direction side : Direction.values()) {
				BlockPos neighbour = segment.relative(side);
				// the tower's own blocks are not neighbours to tell
				if (neighbour.getX() == machinePos.getX() && neighbour.getZ() == machinePos.getZ()) continue;

				level.getBlockState(neighbour).onNeighborChange(level, neighbour, segment);
			}
		}
	}

	// ---- the block entity ----

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return ModBlockEntities.WIND_TURBINE.get().create(pos, state);
	}

	/** Both sides tick it: the server does the work, the client only turns what it was told to draw. */
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != ModBlockEntities.WIND_TURBINE.get()) return null;

		return level.isClientSide
				? (lvl, pos, blockState, entity) -> ((WindTurbineBlockEntity) entity).clientTick()
				: (lvl, pos, blockState, entity) -> ((WindTurbineBlockEntity) entity).serverTick();
	}
}
