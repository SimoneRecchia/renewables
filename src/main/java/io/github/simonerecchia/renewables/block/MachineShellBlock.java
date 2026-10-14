package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.item.ConductorItem;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One cell of a machine that is bigger than its block: invisible, carrying that cell's exact collision, and
 * standing in for the machine for clicks, picking and breaking.  See {@link MachineShell} for the cell tables
 * and for how a machine lays and mends its cells.
 *
 * <b>State.</b> Three integers say where the owning machine is: the offset from the machine to this cell,
 * stored with a bias so each is non-negative.  Changing the reach in {@link MachineShell} renumbers every shell
 * in every world; the stale ones then fail {@link #hostOf} and vanish, and their machine puts them back.
 *
 * <b>No block entity, no item, no loot table.</b>  Breaking any cell breaks the machine (with its drop unless
 * the player is in creative or has the wrong tool), and the machine takes its other cells with it.
 */
public class MachineShellBlock extends Block {
	/** Offset east of the machine to this cell, plus {@link MachineShell#REACH_SIDE}. */
	public static final IntegerProperty OFFSET_X = IntegerProperty.create("dx", 0, 2 * MachineShell.REACH_SIDE);
	/** Offset above the machine to this cell. */
	public static final IntegerProperty OFFSET_Y = IntegerProperty.create("dy", 0, MachineShell.REACH_UP);
	/** Offset south of the machine to this cell, plus {@link MachineShell#REACH_SIDE}. */
	public static final IntegerProperty OFFSET_Z = IntegerProperty.create("dz", 0, 2 * MachineShell.REACH_SIDE);

	public MachineShellBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(OFFSET_X, MachineShell.REACH_SIDE)
				.setValue(OFFSET_Y, 0).setValue(OFFSET_Z, MachineShell.REACH_SIDE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(OFFSET_X, OFFSET_Y, OFFSET_Z);
	}

	// ---- which machine this cell belongs to ----

	/** The shell that points from the cell at {@code target} back to the machine at {@code host}. */
	public static BlockState pointingAt(BlockPos host, BlockPos target) {
		BlockState shell = ModBlocks.MACHINE_SHELL.get().defaultBlockState();
		return shell
				.setValue(OFFSET_X, Mth.clamp(target.getX() - host.getX() + MachineShell.REACH_SIDE, 0, 2 * MachineShell.REACH_SIDE))
				.setValue(OFFSET_Y, Mth.clamp(target.getY() - host.getY(), 0, MachineShell.REACH_UP))
				.setValue(OFFSET_Z, Mth.clamp(target.getZ() - host.getZ() + MachineShell.REACH_SIDE, 0, 2 * MachineShell.REACH_SIDE));
	}

	/** Where the machine is, according to the shell at {@code pos}: its own back-pointer, no questions asked of the machine. */
	public static BlockPos pointsAt(BlockPos pos, BlockState state) {
		return pos.offset(-(state.getValue(OFFSET_X) - MachineShell.REACH_SIDE), -state.getValue(OFFSET_Y),
				-(state.getValue(OFFSET_Z) - MachineShell.REACH_SIDE));
	}

	// ---- shape ----

	/** The cell's own collision, read off the machine's table: it depends on another block, so it is never cached per state. */
	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		BlockPos host = MachineShell.hostOf(level, pos);
		if (host == null) return Shapes.block();

		BlockState hostState = level.getBlockState(host);
		MachineShell machine = (MachineShell) hostState.getBlock();
		int quarters = machine.shellTurns(hostState);
		for (MachineShell.Cell cell : machine.shellCells(hostState)) {
			if (!cell.own() && host.offset(cell.at(quarters)).equals(pos)) return cell.shape(quarters);
		}

		return Shapes.block();
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
		return false;
	}

	// ---- standing in for the machine ----

	/** A click on any cell is a click on the machine, from the same point; a reel is the exception, so a wire can be strung. */
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (player.getItemInHand(hand).getItem() instanceof ConductorItem) return InteractionResult.PASS;

		BlockPos host = MachineShell.hostOf(level, pos);
		if (host == null) return InteractionResult.PASS;

		return level.getBlockState(host).use(level, player, hand, hit.withPosition(host));
	}

	/** Middle-click picks the machine, and nothing at all from a cell whose machine is gone. */
	@Override
	public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
		BlockPos host = MachineShell.hostOf(level, pos);
		if (host == null) return ItemStack.EMPTY;

		BlockState hostState = level.getBlockState(host);
		return hostState.getBlock().getCloneItemStack(level, host, hostState);
	}

	/**
	 * A player who breaks any cell breaks the machine, with its drop on the machine's own rule: not in creative,
	 * and only with the tool the <em>machine</em> wants.  That is read off the machine's state and not this
	 * cell's, so a shell that drifted out of a tool tag would not make a machine drop nothing for a pickaxe
	 * that is right for it.
	 */
	@Override
	public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide) {
			BlockPos host = MachineShell.hostOf(level, pos);
			if (host != null) {
				BlockState hostState = level.getBlockState(host);
				level.destroyBlock(host, !player.isCreative() && player.hasCorrectToolForDrops(hostState), player);
			}
		}

		super.playerWillDestroy(level, pos, state, player);
	}

	/**
	 * Taking a cell apart goes at the speed of taking the machine apart.  A shell's own hardness is the
	 * smallest of the machines' (a tower is 3.0), so left to itself a player digging at the steel of a tower
	 * would finish a third sooner than digging at the block that holds it.
	 */
	@Override
	public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		BlockPos host = MachineShell.hostOf(level, pos);
		if (host == null) return super.getDestroyProgress(state, player, level, pos);

		return level.getBlockState(host).getDestroyProgress(player, level, host);
	}

	/** A cell whose machine is gone or does not claim it goes too, without a drop. */
	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		if (MachineShell.hostOf(level, pos) == null) return Blocks.AIR.defaultBlockState();

		return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
	}
}
