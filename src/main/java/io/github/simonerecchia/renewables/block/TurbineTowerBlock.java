package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.registry.ModBlockEntities;
import io.github.simonerecchia.renewables.wind.TurbineGeometry;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One segment of a turbine tower.  Stacked to the height a turbine wants, with the machine seated on top.
 *
 * A pile is only as good as its height.  Up to {@link TurbineGeometry#SAFE_SEGMENTS} it carries a walker; a block
 * set on top of {@link TurbineGeometry#MAX_SEGMENTS} folds the whole pile at once; in between it stands until
 * something steps on its top.  Either way the fall is {@link TowerCollapse}.  Nothing built to a turbine's own
 * limits (13 at the most) ever comes near these, so the rule is for bare towers.
 */
public class TurbineTowerBlock extends Block implements EntityBlock {
	/**
	 * Nothing stands above this many segments.  Kept under this name for the code that already reads it; it is
	 * {@link TurbineGeometry#SAFE_SEGMENTS}.
	 */
	public static final int MAX_SEGMENTS = TurbineGeometry.SAFE_SEGMENTS;

	/**
	 * Two crossed boxes that make an octagon one block tall, centred in the block: 0.693 across one way and 0.490
	 * the other, and the other way round.  The same for every tower whatever scale it is drawn at.
	 */
	private static final VoxelShape SHAPE = Shapes.or(
			Shapes.box(0.5 - TurbineGeometry.TOWER_LONG_HALF, 0.0, 0.5 - TurbineGeometry.TOWER_SHORT_HALF,
					0.5 + TurbineGeometry.TOWER_LONG_HALF, 1.0, 0.5 + TurbineGeometry.TOWER_SHORT_HALF),
			Shapes.box(0.5 - TurbineGeometry.TOWER_SHORT_HALF, 0.0, 0.5 - TurbineGeometry.TOWER_LONG_HALF,
					0.5 + TurbineGeometry.TOWER_SHORT_HALF, 1.0, 0.5 + TurbineGeometry.TOWER_LONG_HALF));

	public TurbineTowerBlock(Properties properties) {
		super(properties);
	}

	// ---- the column ----

	/** How many tower segments are directly below {@code pos}. */
	public static int countBelow(BlockGetter level, BlockPos pos) {
		int count = 0;
		BlockPos.MutableBlockPos cursor = pos.mutable().move(0, -1, 0);
		while (count < TurbineGeometry.SCAN_LIMIT && level.getBlockState(cursor).getBlock() instanceof TurbineTowerBlock) {
			count++;
			cursor.move(0, -1, 0);
		}

		return count;
	}

	/** How many tower segments are directly above {@code pos}. */
	public static int countAbove(BlockGetter level, BlockPos pos) {
		int count = 0;
		BlockPos.MutableBlockPos cursor = pos.mutable().move(0, 1, 0);
		while (count < TurbineGeometry.SCAN_LIMIT && level.getBlockState(cursor).getBlock() instanceof TurbineTowerBlock) {
			count++;
			cursor.move(0, 1, 0);
		}

		return count;
	}

	/** The turbine seated on the top of the column that {@code pos} is in, or null if there is none. */
	@Nullable
	public static BlockPos findTurbineAbove(BlockGetter level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = pos.mutable().move(0, 1, 0);
		for (int i = 0; i < TurbineGeometry.SCAN_LIMIT + 1; i++) {
			BlockState state = level.getBlockState(cursor);
			if (state.getBlock() instanceof WindTurbineBlock) return cursor.immutable();
			if (!(state.getBlock() instanceof TurbineTowerBlock)) return null;

			cursor.move(0, 1, 0);
		}

		return null;
	}

	/** The foot of the tower a machine at {@code machinePos} stands on: its lowest segment, or the machine's own position if it has none. */
	public static BlockPos footBelow(BlockGetter level, BlockPos machinePos) {
		return machinePos.below(countBelow(level, machinePos));
	}

	/** Whether this segment is the lowest of its pile. */
	public static boolean isFoot(BlockGetter level, BlockPos pos) {
		return !(level.getBlockState(pos.below()).getBlock() instanceof TurbineTowerBlock);
	}

	// ---- shape and light ----

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	/** Sky light goes down the column: a tower is a tube of air as far as the light is concerned. */
	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	// ---- standing up ----

	/** A segment stands on another segment, or on anything that has a solid shape to stand on. */
	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState support = level.getBlockState(below);

		return support.getBlock() instanceof TurbineTowerBlock || !support.getCollisionShape(level, below).isEmpty();
	}

	/**
	 * From any neighbour: a segment whose support has gone is air.  The block above sees the change in its turn,
	 * so the whole column comes down, and whatever machine sits on it with the last block.
	 */
	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (!canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();

		return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
	}

	/** A block set on top of more than twenty makes the whole pile give way. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!(level instanceof ServerLevel serverLevel)) return;

		int height = countBelow(level, pos) + 1;
		if (!TurbineGeometry.collapsesWhenPlaced(height)) return;

		fail(serverLevel, pos.below(height - 1), height, placer);
	}

	/** Anything that steps on the top of a pile taller than sixteen brings it down, a dropped item as well as a player. */
	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		super.stepOn(level, pos, state, entity);
		if (!(level instanceof ServerLevel serverLevel)) return;
		if (level.getBlockState(pos.above()).getBlock() instanceof TurbineTowerBlock) return;

		int height = countBelow(level, pos) + 1;
		if (!TurbineGeometry.collapsesWhenWalkedOn(height)) return;

		fail(serverLevel, pos.below(height - 1), height, entity);
	}

	/** Starts the fall, and tells a player who caused it, once. */
	private static void fail(ServerLevel level, BlockPos foot, int height, @Nullable Entity cause) {
		if (!TowerCollapse.begin(level, foot, height)) return;

		if (cause instanceof Player player) {
			player.displayClientMessage(Component.translatable("message.renewables.tower.collapsed").withStyle(ChatFormatting.RED), true);
		}
	}

	// ---- block entity ----

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return ModBlockEntities.TURBINE_TOWER.get().create(pos, state);
	}
}
