package io.github.simonerecchia.renewables.client.hooks;

import io.github.simonerecchia.renewables.block.MachineShell;
import io.github.simonerecchia.renewables.client.hooks.FittingSelector.Selection;
import io.github.simonerecchia.renewables.client.screen.UtilityPoleConfigScreen;
import io.github.simonerecchia.renewables.client.wire.LinkMachine;
import io.github.simonerecchia.renewables.grid.InsulatorHost;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The door the common code uses to reach the client's input: the items and the pole block call these, from inside a
 * {@code DistExecutor} on the client side, and nothing else here is theirs to know.
 *
 * <ul>
 * <li>{@link #conductorUseOn}: a reel clicked on a block (docs/spec/client-net.md section 2.3, entry A);</li>
 * <li>{@link #wrenchUseOn} and {@link #wrenchUse}: the power wrench clicked on a block and in the air (section 4);</li>
 * <li>{@link #openPoleConfig}: an empty hand on a utility pole (section 7.2).</li>
 * </ul>
 * The click in the air with a reel (entry B) is not here: it arrives as a Forge event, see
 * {@code WireInputEvents}.  None of this talks to the server except the request for a span, which the server
 * validates in full.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientHooks {
	private ClientHooks() {
	}

	/**
	 * A reel was used on a block, on the client.
	 *
	 * @return {@link InteractionResult#PASS} when the block is not a machine with fittings, which is the item's cue to
	 *         lay a run on the ground; otherwise SUCCESS when the click did something (it begins, cancels or
	 *         completes a connection) and FAIL when it did not (no insulator under the crosshair, a repeat of a held
	 *         button, the fitting not numbered yet)
	 */
	public static InteractionResult conductorUseOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (!level.isClientSide() || player == null) return InteractionResult.PASS;

		// the cells that make up most of a machine's body are not the machine: go to the one that is
		BlockPos host = MachineShell.hostOr(level, context.getClickedPos());
		if (!(level.getBlockEntity(host) instanceof InsulatorHost)) return InteractionResult.PASS;

		if (!WireLinker.admitPress(level.getGameTime())) return InteractionResult.FAIL;

		Selection selection = FittingSelector.select(level, host, player.getEyePosition(1.0f), player.getViewVector(1.0f));
		if (selection == null) {
			WireLinker.aimHint(player);
			return InteractionResult.FAIL;
		}

		return WireLinker.accept(player, selection) == null ? InteractionResult.FAIL : InteractionResult.SUCCESS;
	}

	/**
	 * A click in the air with a reel in {@code hand}: the insulator, within reach of the player, the look ray passes
	 * through nearest the eye is picked.  Swings on starting and on completing; not on cancelling.
	 */
	public static void conductorInAir(Player player, InteractionHand hand) {
		Level level = player.level();
		if (!level.isClientSide() || !WireLinker.admitPress(level.getGameTime())) return;

		Selection selection = FittingSelector.nearestInReach(level, player.blockPosition(), player.getEyePosition(1.0f),
				player.getViewVector(1.0f));
		if (selection == null) return;

		LinkMachine.Step step = WireLinker.accept(player, selection);
		if (step == LinkMachine.Step.STARTED || step == LinkMachine.Step.COMPLETED) player.swing(hand);
	}

	/** The wrench was used on a block, on the client: opens a screen if it is pointed at something that has one. */
	public static InteractionResult wrenchUseOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (!context.getLevel().isClientSide() || player == null) return InteractionResult.PASS;

		return openWrenchScreen(context.getLevel(), player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** The wrench was used in the air, on the client. */
	public static InteractionResultHolder<ItemStack> wrenchUse(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide()) return InteractionResultHolder.pass(stack);

		return openWrenchScreen(level, player) ? InteractionResultHolder.success(stack) : InteractionResultHolder.pass(stack);
	}

	/** Opens the configuration screen of the utility pole at {@code pos}, which is the pole's own block. */
	public static void openPoleConfig(BlockPos pos) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) return;

		minecraft.setScreen(new UtilityPoleConfigScreen(pos));
	}

	private static boolean openWrenchScreen(Level level, Player player) {
		BlockPos target = WrenchTargets.find(level, player);
		if (target == null) return false;

		BlockEntity machine = level.getBlockEntity(target);
		if (machine == null) return false;

		Minecraft.getInstance().setScreen(WrenchScreens.forMachine(machine, target));
		return true;
	}
}
