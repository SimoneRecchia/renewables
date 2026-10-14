package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.grid.WireLedger;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;
import io.github.simonerecchia.renewables.wind.TurbineGeometry;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
 * in their way and nothing about them can be selected.  The model's front faces the player who placed it.
 */
public class WindTurbineBlock extends MachineBlock {
	/** The facing wind_turbine.obj is modelled at. */
	public static final Direction AUTHORED = Direction.SOUTH;

	private final TurbineSpec spec;
	/** The nacelle as collision and as selection, at each of the four facings: the hull and the cooler, cut to the block. */
	private final MachineShell.Cell shape;

	public WindTurbineBlock(Properties properties, TurbineSpec spec) {
		super(properties);
		this.spec = spec;

		VoxelShape authored = Shapes.empty();
		for (double[] b : TurbineGeometry.nacelleBoxes(spec)) {
			authored = Shapes.or(authored, Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5]));
		}

		this.shape = new MachineShell.Cell(0, 0, 0, authored);
	}

	public TurbineSpec spec() {
		return spec;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape.shape(ModelFacing.quarters(AUTHORED, state.getValue(FACING)));
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
	 * The cables leave at the foot of the tower, so when a turbine appears or goes the foot is told: what it offers to
	 * a neighbouring cable has just changed, and the neighbour is only told to look again by being told something
	 * changed.
	 */
	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide() && !oldState.is(state.getBlock())) notifyFoot(level, pos);
	}

	/**
	 * When the turbine is replaced: the foot first, then every span hung from its fitting, and only then the block
	 * entity goes, because the spans are found through the ids it holds.
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
			notifyFoot(level, pos);
			if (level.getBlockEntity(pos) instanceof WindTurbineBlockEntity turbine) {
				WireLedger.removeAttachedTo(serverLevel, turbine.getInsulatorIds());
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	/**
	 * Tells the foot of the tower under {@code machinePos} that what it carries has changed: drops what a neighbour
	 * cached about its energy, and nudges the six neighbours the way a comparator update would.
	 */
	private static void notifyFoot(Level level, BlockPos machinePos) {
		BlockPos foot = TurbineTowerBlock.footBelow(level, machinePos);

		BlockEntity footEntity = level.getBlockEntity(foot);
		if (footEntity != null) {
			footEntity.invalidateCaps();
			footEntity.reviveCaps();
		}

		level.updateNeighbourForOutputSignal(foot, level.getBlockState(foot).getBlock());
		for (Direction side : Direction.values()) {
			BlockPos neighbour = foot.relative(side);
			level.getBlockState(neighbour).onNeighborChange(level, neighbour, foot);
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
