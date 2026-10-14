package io.github.simonerecchia.renewables.item;

import io.github.simonerecchia.renewables.block.MachineShell;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block's item with one grey line of tooltip.  Named after its block, which is where its display name comes from.
 *
 * A machine that fills more than its own block ({@link MachineShell}) is also refused where its cells will not
 * fit: a pole with an arm inside a wall is a pole whose arm cannot be touched, so the player is told there is not
 * room instead of being handed a machine that is partly not there.  That is decided here, on the item, because
 * every large machine in the mod is placed by this class, and what the machine fills is read off the state it
 * is about to be placed in.
 */
public class TooltipBlockItem extends BlockItem {
	private final String tooltipKey;

	public TooltipBlockItem(Block block, Properties properties, String tooltipKey) {
		super(block, properties);
		this.tooltipKey = tooltipKey;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
	}

	@Override
	protected boolean canPlace(BlockPlaceContext context, BlockState state) {
		if (!super.canPlace(context, state)) return false;

		BlockPos blocked = MachineShell.obstruction(context.getLevel(), context.getClickedPos(), state);
		if (blocked == null) return true;

		Player player = context.getPlayer();
		if (player != null && !context.getLevel().isClientSide) {
			player.displayClientMessage(Component.translatableWithFallback("message.renewables.machine.no_room",
					"There is not enough room here: the machine would stand inside something at (%s, %s, %s).",
					blocked.getX(), blocked.getY(), blocked.getZ()), true);
		}

		return false;
	}
}
