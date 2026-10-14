package io.github.simonerecchia.renewables.item;

import io.github.simonerecchia.renewables.api.Nameplate;
import io.github.simonerecchia.renewables.api.power.InverterSpec;
import io.github.simonerecchia.renewables.catalog.InverterCatalog;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** An inverter in the hand, reading like the front page of its datasheet. */
public class PvInverterBlockItem extends BlockItem {
	private final InverterSpec spec;

	public PvInverterBlockItem(Block block, Properties properties, InverterSpec spec) {
		super(block, properties);
		this.spec = spec;
	}

	public InverterSpec spec() {
		return spec;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal(InverterCatalog.fullName(spec)).withStyle(ChatFormatting.WHITE));

		tooltip.add(Component.translatable("tooltip.renewables.inverter.nameplate",
				Nameplate.rating(spec.acPowerKw()),
				Nameplate.fmt("%.0f kVA", spec.apparentPowerKva())).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.renewables.inverter.dc",
				Nameplate.rating(spec.maxDcPowerKw()),
				Nameplate.fmt("%.2f", spec.nominalDcAcRatio())).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.renewables.inverter.mppt",
				spec.mpptCount(),
				Nameplate.fmt("%.0f", spec.mpptMinVolts()),
				Nameplate.fmt("%.0f", spec.mpptMaxVolts()),
				Nameplate.fmt("%.0f", spec.startupVolts())).withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("tooltip.renewables.inverter.efficiency",
				Nameplate.fmt("%.1f%%", spec.peakEfficiency() * 100.0),
				Nameplate.fmt("%.1f%%", spec.europeanEfficiency() * 100.0)).withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("tooltip.renewables.inverter.thermal",
				Nameplate.fmt("%.0f", spec.derateOnsetC()),
				Component.translatable("cooling.renewables." + spec.cooling().key()),
				Nameplate.fmt("%.0f W", spec.nightWatts())).withStyle(ChatFormatting.DARK_GRAY));
	}

}
