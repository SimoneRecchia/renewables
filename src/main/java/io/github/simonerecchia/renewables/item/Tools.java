package io.github.simonerecchia.renewables.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The mod's tools, and the rule a machine's empty-hand gesture keeps with them. */
public final class Tools {
	private Tools() {
	}

	/** A reel of conductor, a reel of DC cable or the wrench: held in either hand, a click on a machine is for it. */
	public static boolean isTool(ItemStack stack) {
		Item item = stack.getItem();
		return item instanceof ConductorItem || item instanceof DcCableItem || item instanceof PowerWrenchItem;
	}

	/**
	 * Whether a click with {@code hand} is a machine's empty-hand gesture - open the pole's screen, throw a switch, read
	 * a nameplate: nothing in that hand, and no tool of the mod in the other.
	 *
	 * The game offers a click to the block for the main hand before it offers it to the off hand's item, so with a
	 * reel in the off hand and the main hand empty the pole opened its screen and the switch swung, and the reel never
	 * heard of the click.
	 */
	public static boolean emptyHanded(Player player, InteractionHand hand) {
		if (!player.getItemInHand(hand).isEmpty()) return false;

		InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		return !isTool(player.getItemInHand(other));
	}
}
