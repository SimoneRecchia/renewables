package io.github.simonerecchia.renewables.item;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A reel of overhead conductor: strings a span between two fittings, or lays a run along the ground.
 *
 * STUB: owner B (grid-blocks.md section 9.3).  It carries the spec the server reads the reel from when it
 * charges for a span; the placing of ground runs and the two-line tooltip are still to do.
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
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.renewables." + spec.id().getPath()).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.renewables.conductor.reach", spec.blocksPerItem(), (int) spec.maxSpan())
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
