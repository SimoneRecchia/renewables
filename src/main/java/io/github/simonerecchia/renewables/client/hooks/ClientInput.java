package io.github.simonerecchia.renewables.client.hooks;

import io.github.simonerecchia.renewables.client.hooks.FittingSelector.Selection;
import io.github.simonerecchia.renewables.client.screen.UtilityPoleConfigScreen;
import io.github.simonerecchia.renewables.client.wire.AirClick;
import io.github.simonerecchia.renewables.item.ClientHooks;
import io.github.simonerecchia.renewables.item.ConductorItem;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The client's answers to the three questions the common code asks of it, installed once at client setup into
 * {@link io.github.simonerecchia.renewables.item.ClientHooks} - which is how an item or a block, loaded on a dedicated
 * server too, reaches the input code without naming a client class:
 *
 * <ul>
 * <li>{@link #wireClick}: a reel clicked on a machine with fittings (docs/spec/client-net.md section 2.3, entry A);</li>
 * <li>{@link #wireClickInAir}: a reel clicked on nothing (entry B), which reaches the item's {@code use} - the
 *     vanilla click-on-empty event is posted only for an empty hand, so it never carries a reel;</li>
 * <li>{@link #wrenchUse}: the power wrench clicked on a block or in the air (section 4);</li>
 * <li>{@link #openPoleConfig}: an empty hand on a utility pole (section 7.2).</li>
 * </ul>
 * None of this talks to the server except the request for a span, which the server validates in full.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientInput {
	private ClientInput() {
	}

	/** Puts the client's handlers where the items and the pole look for them. */
	public static void install() {
		ClientHooks.installWireClick(ClientInput::wireClick);
		ClientHooks.installWireClickInAir(ClientInput::wireClickInAir);
		ClientHooks.installWrenchUse(ClientInput::wrenchUse);
		ClientHooks.installPoleScreen(ClientInput::openPoleConfig);
	}

	/**
	 * A reel was clicked on the machine at {@code host} (the cell already resolved to the machine).
	 *
	 * @return true when the click did something - began, cancelled or completed a connection; false when it did not (no
	 *         insulator under the crosshair, a repeat of a held button, a fitting the server has not numbered yet),
	 *         which the item answers with FAIL
	 */
	public static boolean wireClick(Level level, Player player, InteractionHand hand, BlockPos host, BlockHitResult hit) {
		if (!level.isClientSide()) return false;
		if (!WireLinker.admitPress(level.getGameTime())) return false;

		Selection selection = FittingSelector.select(level, host, player.getEyePosition(1.0f), player.getViewVector(1.0f));
		if (selection == null) {
			WireLinker.aimHint(player);
			return false;
		}

		return WireLinker.accept(player, selection) != null;
	}

	/**
	 * A reel in {@code hand} was used with the crosshair on nothing: the insulator, within reach of the player, that
	 * the look ray passes through nearest the eye is picked.  Swings on starting and on completing; not on cancelling.
	 *
	 * @return PASS when the press was not this reel's (crosshair on a block, the main hand's reel already had it, a
	 *         repeat of a held button) or picked nothing, so the other hand may still use it; otherwise what
	 *         {@link AirClick#answer} says
	 */
	public static InteractionResult wireClickInAir(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) return InteractionResult.PASS;

		HitResult crosshair = Minecraft.getInstance().hitResult;
		boolean onNothing = crosshair == null || crosshair.getType() == HitResult.Type.MISS;
		boolean mainHoldsReel = player.getMainHandItem().getItem() instanceof ConductorItem;
		if (!AirClick.takes(onNothing, hand == InteractionHand.OFF_HAND, mainHoldsReel)) return InteractionResult.PASS;
		if (!WireLinker.admitPress(level.getGameTime())) return InteractionResult.PASS;

		Selection selection = FittingSelector.nearestInReach(level, player.blockPosition(), player.getEyePosition(1.0f),
				player.getViewVector(1.0f));
		if (selection == null) return InteractionResult.PASS;

		return AirClick.answer(WireLinker.accept(player, selection));
	}

	/**
	 * The wrench was used, on a block ({@code hit} set) or in the air (null): opens a screen for what it is pointed at.
	 * The target is found from the look ray, not from {@code hit}, so a click on the ground still reads the machine
	 * the player is looking through to.
	 */
	public static boolean wrenchUse(Level level, Player player, @Nullable BlockHitResult hit) {
		if (!level.isClientSide()) return false;

		// use() after a useOn on a block that found nothing: the same click, and the same cube was just scanned
		HitResult crosshair = Minecraft.getInstance().hitResult;
		if (hit == null && crosshair != null && crosshair.getType() == HitResult.Type.BLOCK) return false;

		BlockPos target = WrenchTargets.find(level, player);
		if (target == null) return false;

		BlockEntity machine = level.getBlockEntity(target);
		if (machine == null) return false;

		Minecraft.getInstance().setScreen(WrenchScreens.forMachine(machine, target));
		return true;
	}

	/** Opens the configuration screen of the utility pole at {@code pos}, the pole's own block. */
	public static void openPoleConfig(BlockPos pos) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) return;

		minecraft.setScreen(new UtilityPoleConfigScreen(pos));
	}
}
