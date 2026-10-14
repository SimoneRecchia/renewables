package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.SwitchgearSpec;
import io.github.simonerecchia.renewables.grid.Grid;
import io.github.simonerecchia.renewables.item.Tools;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * A disconnector or a breaker: one cell, six insulators (line side first), an open/closed state.
 *
 * <b>By hand</b> (an empty hand only, and no tool of the mod in the other; otherwise the click goes to the item -
 * {@link Tools#emptyHanded}): a breaker opens and
 * closes whenever it is asked.  A disconnector closes whenever it is asked but will not open with load on it -
 * an arc across bare blades in air does not go out - and says so; open the breaker first.
 *
 * <b>By redstone</b>, on the <em>edge</em> of the signal: a signal appearing opens the switch and one going
 * closes it, wherever it is that the switch is by then.  A switch worked by hand is left as it was by every
 * neighbour that merely changes, which is what level-reading did not do.  The command from the redstone
 * overrides the disconnector's interlock, the way a trip coil does.
 *
 * <b>Tripping</b>: a closed breaker that is carrying more than it is rated for opens itself, once a second
 * at most.
 *
 * Every change of state recalculates the grid at once, so the power reading that follows is the one with the
 * switch in its new position.
 */
public class SwitchgearBlock extends MachineBlock {
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

	/** The facing the mv_*.obj files are modelled at. */
	public static final Direction AUTHORED = Direction.NORTH;

	// ---- 1 cell(s), printed by tools/check_hitboxes.py --java ----
	private static final List<Cell> DISCONNECTOR_CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(0.80, 0.00, 4.16, 15.20, 1.95, 11.84),
					Block.box(2.98, 1.28, 9.78, 4.38, 5.70, 11.18),
					Block.box(2.98, 1.28, 4.82, 4.38, 5.73, 6.22),
					Block.box(3.33, 1.28, 10.00, 14.85, 5.73, 11.15),
					Block.box(7.30, 1.28, 9.78, 8.70, 5.70, 11.18),
					Block.box(7.30, 1.28, 4.82, 8.70, 5.73, 6.22),
					Block.box(11.62, 1.28, 9.78, 13.02, 5.70, 11.18),
					Block.box(11.62, 1.28, 4.82, 13.02, 5.73, 6.22))));

	// ---- 1 cell(s), printed by tools/check_hitboxes.py --java ----
	private static final List<Cell> BREAKER_CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(0.80, 0.00, 4.16, 15.20, 8.08, 11.84),
					Block.box(2.81, 6.89, 5.14, 4.55, 8.69, 10.86),
					Block.box(7.13, 6.89, 5.14, 8.87, 8.69, 10.86),
					Block.box(11.45, 6.89, 5.14, 13.19, 8.69, 10.86))));

	private final SwitchgearSpec spec;

	public SwitchgearBlock(Properties properties, SwitchgearSpec spec) {
		super(properties);
		this.spec = spec;
		registerDefaultState(defaultBlockState().setValue(OPEN, false));
	}

	public SwitchgearSpec spec() {
		return spec;
	}

	/** Whether the switch is open, which is what the renderer swings the blade for. */
	public static boolean open(BlockState state) {
		return state.getValue(OPEN);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(OPEN);
	}

	@Override
	public List<Cell> shellCells(BlockState state) {
		return spec.breaksLoad() ? BREAKER_CELLS : DISCONNECTOR_CELLS;
	}

	@Override
	public Direction shellAuthored() {
		return AUTHORED;
	}

	// ---- by hand ----

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!Tools.emptyHanded(player, hand)) return InteractionResult.PASS;
		if (level.isClientSide) return InteractionResult.SUCCESS;

		boolean wantOpen = !open(state);
		if (wantOpen && level.getBlockEntity(pos) instanceof SwitchgearBlockEntity gear
				&& !SwitchLogic.mayOpenByHand(spec.breaksLoad(), gear.getCurrentPower(), spec.ratedKw())) {
			player.displayClientMessage(Component.translatable("message.renewables.switch.under_load"), true);
			return InteractionResult.CONSUME;
		}

		setOpen(level, pos, state, wantOpen);
		player.displayClientMessage(Component.translatable(wantOpen ? "message.renewables.switch.opened" : "message.renewables.switch.closed"), true);
		return InteractionResult.CONSUME;
	}

	/**
	 * Puts the switch in a position: the state, the sound and an immediate recount of the grid.  A switch
	 * already there does nothing.
	 *
	 * @return whether it moved
	 */
	public static boolean setOpen(Level level, BlockPos pos, BlockState state, boolean open) {
		if (!(state.getBlock() instanceof SwitchgearBlock block) || open(state) == open) return false;

		level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);

		SoundEvent sound = block.spec.breaksLoad() ? SoundEvents.PISTON_CONTRACT : SoundEvents.IRON_TRAPDOOR_OPEN;
		level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.7f, block.spec.breaksLoad() ? 0.6f : 1.1f);

		// the reading a player takes next must be of the switch where it is now, not where it was a tick ago
		if (level instanceof ServerLevel serverLevel) Grid.recalcNow(serverLevel);
		return true;
	}

	// ---- by redstone ----

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moved) {
		super.neighborChanged(state, level, pos, block, fromPos, moved);

		if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof SwitchgearBlockEntity gear) {
			gear.onNeighbourChanged(serverLevel);
		}
	}

	/** Placed beside a signal that is already there: the switch starts the way the signal says. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);

		if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof SwitchgearBlockEntity gear) {
			gear.onNeighbourChanged(serverLevel);
		}
	}

	// ---- the machine ----

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return ModBlockEntities.SWITCHGEAR.get().create(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide || !spec.breaksLoad() || type != ModBlockEntities.SWITCHGEAR.get()) return null;

		return (lvl, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof SwitchgearBlockEntity gear && lvl instanceof ServerLevel serverLevel) gear.serverTick(serverLevel);
		};
	}
}
