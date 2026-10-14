package io.github.simonerecchia.renewables.item;

import io.github.simonerecchia.renewables.api.Nameplate;
import io.github.simonerecchia.renewables.api.power.PvArraySpec;
import io.github.simonerecchia.renewables.api.power.PvModuleSpec;
import io.github.simonerecchia.renewables.api.power.TrackerSpec;
import io.github.simonerecchia.renewables.catalog.PvCatalog;
import io.github.simonerecchia.renewables.catalog.PvModuleCatalog;
import io.github.simonerecchia.renewables.catalog.TrackerCatalog;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** An array in the hand, reading like the header of a datasheet. */
public class PvArrayBlockItem extends BlockItem {
	private final PvArraySpec spec;

	public PvArrayBlockItem(Block block, Properties properties, PvArraySpec spec) {
		super(block, properties);
		this.spec = spec;
	}

	public PvArraySpec spec() {
		return spec;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		PvModuleSpec module = spec.module();

		tooltip.add(Component.literal(PvCatalog.fullName(spec)).withStyle(ChatFormatting.WHITE));
		tooltip.add(Component.translatable("tooltip.renewables.pv.mounting",
				Component.translatable("mounting.renewables." + spec.mounting().key())).withStyle(ChatFormatting.DARK_GRAY));

		tooltip.add(Component.translatable("tooltip.renewables.pv.nameplate",
				Nameplate.fmt("%.1f kW", spec.dcPowerKw()),
				spec.moduleCount(),
				PvModuleCatalog.fullName(module)).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.renewables.pv.module",
				module.cell().label(),
				Nameplate.fmt("%.0f W", module.ratedPowerW()),
				Nameplate.fmt("%.1f%%", module.efficiency() * 100.0),
				Nameplate.fmt("%.2f %%/K", module.powerCoefficient() * 100.0)).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.renewables.pv.laminate",
				Nameplate.fmt("%.0f", module.widthM() * 1000.0),
				Nameplate.fmt("%.0f", module.heightM() * 1000.0),
				Nameplate.fmt("%.0f", module.depthM() * 1000.0),
				Nameplate.fmt("%.1f", module.massKg())).withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("tooltip.renewables.pv.strings",
				spec.strings(),
				spec.modulesPerString(),
				Nameplate.fmt("%.0f V", spec.stringOpenCircuitVoltage(PvModuleSpec.STC_TEMPERATURE)),
				Nameplate.fmt("%.1f A", spec.stringCurrent(PvModuleSpec.STC_IRRADIANCE, PvModuleSpec.STC_TEMPERATURE))).withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("tooltip.renewables.pv.ground",
				Nameplate.fmt("%.0f%%", spec.groundCoverRatio() * 100.0),
				Nameplate.fmt("%.0f", spec.moduleAreaM2())).withStyle(ChatFormatting.DARK_GRAY));

		if (module.bifacial()) {
			tooltip.add(Component.translatable("tooltip.renewables.pv.bifacial",
					Nameplate.fmt("%.0f%%", module.bifaciality() * 100.0)).withStyle(ChatFormatting.DARK_GRAY));
		}

		TrackerSpec tracker = spec.tracker();
		if (tracker != null) {
			tooltip.add(Component.translatable("tooltip.renewables.pv.tracker",
					TrackerCatalog.fullName(tracker),
					tracker.axes(),
					Nameplate.fmt("%.0f", tracker.rotationLimitDeg()),
					Nameplate.fmt("%.0f", tracker.lowestTrackableSunDeg())).withStyle(ChatFormatting.DARK_GRAY));
		}

		tooltip.add(Component.translatable("tooltip.renewables.pv.needs_inverter").withStyle(ChatFormatting.DARK_GRAY));
	}

}
