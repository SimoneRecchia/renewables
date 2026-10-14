package io.github.simonerecchia.renewables.client.events;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.client.hooks.ClientHooks;
import io.github.simonerecchia.renewables.client.hooks.WireLinker;
import io.github.simonerecchia.renewables.client.wire.ClientWires;
import io.github.simonerecchia.renewables.item.ConductorItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The input side of wiring that is not a click on a block: a click in the air with a reel, and the housekeeping that
 * goes with a connection left pending (docs/spec/client-net.md sections 2.2 and 2.3).
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WireInputEvents {
	/** A pending connection is dropped after the player has had no reel in either hand for this long: five seconds. */
	private static final int IDLE_LIMIT_TICKS = 100;

	private static int idleTicks;

	private WireInputEvents() {
	}

	/**
	 * A click on nothing.  The event comes once for each hand, so one press with a reel in the main hand and something
	 * else (or nothing) in the other would run twice and the second pass would cancel the first.  Only the hand that
	 * holds the reel is looked at, the main hand first, and the click gates in {@link WireLinker} are the rest of the
	 * guarantee that one press is one step.
	 */
	@SubscribeEvent
	public static void onClickInAir(PlayerInteractEvent.RightClickEmpty event) {
		InteractionHand hand = event.getHand();
		if (!(event.getItemStack().getItem() instanceof ConductorItem)) return;

		boolean mainHoldsReel = event.getEntity().getMainHandItem().getItem() instanceof ConductorItem;
		if (hand == InteractionHand.OFF_HAND && mainHoldsReel) return;

		ClientHooks.conductorInAir(event.getEntity(), hand);
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;

		Minecraft minecraft = Minecraft.getInstance();
		WireLinker.observeUseButton(minecraft.options.keyUse.isDown());

		LocalPlayer player = minecraft.player;
		if (player == null) {
			idleTicks = 0;
			return;
		}

		if (ClientWires.pending() == null || holdsReel(player)) {
			idleTicks = 0;
			return;
		}

		// put the reel away and walk off, and the half-made connection does not stay hanging in the air for ever
		if (++idleTicks > IDLE_LIMIT_TICKS) {
			ClientWires.cancelPending();
			idleTicks = 0;
		}
	}

	private static boolean holdsReel(LocalPlayer player) {
		return player.getMainHandItem().getItem() instanceof ConductorItem
				|| player.getOffhandItem().getItem() instanceof ConductorItem;
	}
}
