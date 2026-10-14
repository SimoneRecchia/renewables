package io.github.simonerecchia.renewables.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The power wrench: aim at a machine and a screen shows what it is doing.  Its whole job is on the client.
 *
 * Used on a block or in the air, the wrench asks the client to look for a machine to read (nearest to the eye
 * within {@code PanelGuard.WRENCH_REACH}, cells standing in for their machine) and to open the screen that suits
 * it - the machine's own panel where it has one, the general power card where it has not.  The server does
 * nothing: there is no packet, because there is nothing to change, and a command sent from a panel is judged
 * on its own by {@code PanelGuard}.
 *
 * The search and the screens are the client's ({@link ClientHooks#wrenchUse}); what is here is the item, which
 * says <em>when</em>: and that a click that found nothing is not a use.
 */
public class PowerWrenchItem extends TooltipItem {
	public PowerWrenchItem(Properties properties) {
		super(properties, "tooltip.renewables.power_wrench");
	}

	/** A click on a block, whatever block it is: the wrench is for machines, and finding the machine is the client's job. */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!level.isClientSide || context.getPlayer() == null) return InteractionResult.PASS;

		BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(),
				context.isInside());
		return ClientHooks.wrenchUse(level, context.getPlayer(), hit) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** A click on the air: a machine up a tower is out of the block's reach and not out of the wrench's. */
	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide) return InteractionResultHolder.pass(stack);

		return ClientHooks.wrenchUse(level, player, null) ? InteractionResultHolder.success(stack) : InteractionResultHolder.pass(stack);
	}
}
