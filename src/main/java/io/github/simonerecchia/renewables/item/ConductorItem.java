package io.github.simonerecchia.renewables.item;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.block.MachineShell;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * A reel of overhead conductor: strings a span between two fittings, or lays a run along the ground.
 *
 * <b>On a machine that carries fittings</b> (a cell standing for a machine counts as the machine) the click is the
 * first or the second end of a span.  Which fitting was aimed at, and the half-made connection, are the client's
 * ({@link ClientHooks#wireClick}); the server says success and no more, because the span arrives in its own
 * packet and the server judges that on its own - the reel in the hand, the classes of voltage, the length, the
 * cost - without believing the client about any of it.  A click that aimed at no fitting is not a use, and lays
 * nothing either: the shaft of a pole is not somewhere to put a run.
 *
 * <b>On anything else</b> the reel lays one block of ground conductor where a block would be placed, if it can
 * stand there (it wants something solid under it), and costs one item outside creative.
 *
 * <b>On nothing</b> (the crosshair past block reach) the click picks the nearest fitting the look ray passes through,
 * within sixteen blocks: the client's again, through {@link #use}.
 *
 * The spec the reel carries is also what the server reads when it charges for a span.
 */
public class ConductorItem extends Item {
	private final ConductorSpec spec;

	public ConductorItem(Properties properties, ConductorSpec spec) {
		super(properties);
		this.spec = spec;
	}

	public ConductorSpec spec() {
		return spec;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		BlockPos host = MachineShell.hostOr(level, context.getClickedPos());

		BlockEntity entity = level.isLoaded(host) ? level.getBlockEntity(host) : null;
		if (entity instanceof InsulatorHost) {
			if (!level.isClientSide || player == null) return InteractionResult.SUCCESS;

			BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), host, context.isInside());
			return ClientHooks.wireClick(level, player, context.getHand(), host, hit) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}

		return layRun(context);
	}

	/**
	 * A click on nothing: the reaching click, for a fitting out of block reach such as the top of a tower.
	 *
	 * The item is the only way in - vanilla posts its click-on-empty event only for an empty hand, so a reel in hand
	 * never sees it.  The server has nothing to do; the client answers {@link ClientHooks#wireClickInAir}, which also
	 * turns away the call that follows a {@link #useOn} that passed on a block.
	 */
	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide) return InteractionResultHolder.pass(stack);

		return new InteractionResultHolder<>(ClientHooks.wireClickInAir(level, player, hand), stack);
	}

	/** One block of this conductor on the ground, where a block would go. */
	private InteractionResult layRun(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();

		var registered = ModBlocks.GROUND_CONDUCTORS.get(spec.id());
		if (registered == null) return InteractionResult.PASS;

		BlockPlaceContext placing = new BlockPlaceContext(context);
		if (!placing.canPlace()) return InteractionResult.PASS;

		BlockPos pos = placing.getClickedPos();
		if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return InteractionResult.PASS;

		Block block = registered.get();
		BlockState state = block.getStateForPlacement(placing);
		if (state == null || !state.canSurvive(level, pos)) return InteractionResult.PASS;
		if (!level.isUnobstructed(state, pos, player == null ? CollisionContext.empty() : CollisionContext.of(player))) {
			return InteractionResult.PASS;
		}

		// the client is told it worked and the server does the work
		if (level.isClientSide) return InteractionResult.SUCCESS;

		// a reel is paid for a block that is there, and only then
		if (!level.setBlock(pos, state, Block.UPDATE_ALL_IMMEDIATE)) return InteractionResult.FAIL;

		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
		level.playSound(null, pos, state.getSoundType(level, pos, player).getPlaceSound(), SoundSource.BLOCKS, 0.8f, 1.1f);

		if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.renewables." + spec.id().getPath()).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.renewables.conductor.reach", spec.blocksPerItem(), (int) spec.maxSpan())
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
