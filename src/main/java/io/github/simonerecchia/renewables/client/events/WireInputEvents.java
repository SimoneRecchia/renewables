package io.github.simonerecchia.renewables.client.events;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.client.hooks.WireLinker;
import io.github.simonerecchia.renewables.client.wire.ClientWires;
import io.github.simonerecchia.renewables.item.ConductorItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The housekeeping that goes with a connection left pending (docs/spec/client-net.md section 2.2): the use button
 * watched for the click gates, and a pending connection dropped when the reel is put away.
 *
 * The click in the air with a reel is not here: vanilla posts its click-on-empty event only for an empty hand, so it
 * never carries a reel, and the click arrives through {@code ConductorItem.use} instead.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WireInputEvents {
	/** A pending connection is dropped after the player has had no reel in either hand for this long: five seconds. */
	private static final int IDLE_LIMIT_TICKS = 100;

	private static int idleTicks;

	private WireInputEvents() {
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
