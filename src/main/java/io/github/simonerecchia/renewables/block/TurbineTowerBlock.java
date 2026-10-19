package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;
import io.github.simonerecchia.renewables.wind.TurbineGeometry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
public class TurbineTowerBlock extends Block implements EntityBlock, MachineShell.Carrier {
	/** How far up or down a column is counted: past the tallest pile that can stand ({@link TurbineGeometry#SCAN_LIMIT}). */
	public static final int SCAN_LIMIT = TurbineGeometry.SCAN_LIMIT;
	/**
	 * The count the bare tower's renderer stops at, under the name it reads.  It was the safe height, 16, so a pile of
	 * 18 to 20 that stands until walked on was drawn 17 high with its top blocks solid and invisible.
	 *
	 * @deprecated the count is {@link #countAbove}; this is {@link #SCAN_LIMIT} and goes once the renderer calls that
	 */
	@Deprecated
	public static final int MAX_SEGMENTS = SCAN_LIMIT;

	/** Finest radius step the tube's staircase is cached at: a tenth of a pixel. */
	private static final double RADIUS_STEPS_PER_BLOCK = 160.0;
	/** The tube's collision at each radius, built once: a shape is asked for far more often than it changes. */
	private static final Map<Integer, VoxelShape> TUBES = new ConcurrentHashMap<>();
	/** The insulator's box at the foot of a tower that carries a turbine, by its offset and the machine's facing. */
	private static final Map<Integer, VoxelShape> INSULATORS = new ConcurrentHashMap<>();

	/**
	 * The shape follows the tower's height and the machine on it, so it is asked for every time rather than kept per state.
	 * Solid as far as fluids go: a dynamic shape is never counted solid, and water spread into a tower stood in the sea and
	 * washed its foot away, and the rest of it with it.
	 */
	public TurbineTowerBlock(Properties properties) {
		super(properties.dynamicShape().forceSolidOn());
	}

	private static VoxelShape box(double[] b) {
		return Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5]);
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

	/** A click on any segment of a tower that carries a turbine is a click on the turbine, its fitting included. */
	@Nullable
	@Override
	public BlockPos carriedMachine(BlockGetter level, BlockPos pos) {
		return findTurbineAbove(level, pos);
	}

	// ---- shape and light ----

	/**
	 * The tube as the model draws it: a cone that is 0.34 in radius at the foot and 0.19 at the head, stretched over the
	 * pile and scaled by {@link TurbineGeometry#towerScale} (a bare tower by its height alone).  This block's slice of it is
	 * a staircase of rectangles round the axis, so what the player bumps into and points at is the tube and not a box
	 * around it.  The foot of a tower that carries a turbine also has the insulator beside it, which is what a wire is
	 * strung to.
	 *
	 * Collision and selection are the same shape.  A tall tower is wider than its block, and the insulator moves out with
	 * its plinth: both leave the cell, which a dynamic shape may (it counts as a large collision shape).
	 */
	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int below = countBelow(level, pos);
		int above = 0;
		BlockState head = null;
		BlockPos.MutableBlockPos cursor = pos.mutable();
		while (above < TurbineGeometry.SCAN_LIMIT) {
			BlockState up = level.getBlockState(cursor.move(0, 1, 0));
			if (!(up.getBlock() instanceof TurbineTowerBlock)) {
				head = up;
				break;
			}

			above++;
		}

		int height = below + above + 1;
		TurbineSpec machine = head != null && head.getBlock() instanceof WindTurbineBlock turbine ? turbine.spec() : null;
		double scale = machine != null ? TurbineGeometry.towerScale(height, machine) : TurbineGeometry.bareTowerScale(height);

		int key = (int) Math.round(TurbineGeometry.towerRadius(below, height, scale) * RADIUS_STEPS_PER_BLOCK);
		VoxelShape tube = TUBES.computeIfAbsent(key, k -> {
			VoxelShape steps = Shapes.empty();
			for (double[] b : TurbineGeometry.towerBoxes(k / RADIUS_STEPS_PER_BLOCK)) steps = Shapes.or(steps, box(b));

			return steps.optimize();
		});

		if (below != 0 || machine == null) return tube;

		int quarters = ModelFacing.quarters(WindTurbineBlock.AUTHORED, head.getValue(BlockStateProperties.HORIZONTAL_FACING));
		int offset = (int) Math.round(TurbineGeometry.insulatorOffset(scale) * RADIUS_STEPS_PER_BLOCK);
		VoxelShape insulator = INSULATORS.computeIfAbsent(offset * 4 + quarters,
				k -> MachineShell.turned(box(TurbineGeometry.insulatorBox((k / 4) / RADIUS_STEPS_PER_BLOCK)), k % 4));
		return Shapes.or(tube, insulator);
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

	/**
	 * A segment stands on another segment, or is founded on a block whose top can carry it in the middle - and never on a
	 * machine of this mod.  Any collision at all used to do, so a tower stood on another turbine's nacelle, counted its
	 * own pile only, and ran a second rotor through the first one's, its foot fourteen blocks up in the wind.
	 */
	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState support = level.getBlockState(below);
		if (support.getBlock() instanceof TurbineTowerBlock) return true;

		return isFoundation(level, below, support);
	}

	/** Whether a block can take the foot of a tower: a top face that carries a load in its middle, and not a machine. */
	public static boolean isFoundation(LevelReader level, BlockPos pos, BlockState support) {
		Block block = support.getBlock();
		if (block instanceof MachineBlock || block instanceof MachineShellBlock || block instanceof TurbineTowerBlock) return false;

		return support.isFaceSturdy(level, pos, Direction.UP, SupportType.CENTER);
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
