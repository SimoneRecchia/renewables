package io.github.simonerecchia.renewables.client.hooks;

import io.github.simonerecchia.renewables.block.MetStationBlockEntity;
import io.github.simonerecchia.renewables.block.PlantControllerBlockEntity;
import io.github.simonerecchia.renewables.block.PvArrayBlockEntity;
import io.github.simonerecchia.renewables.block.PvCombinerBlockEntity;
import io.github.simonerecchia.renewables.block.PvInverterBlockEntity;
import io.github.simonerecchia.renewables.block.WindTurbineBlockEntity;
import io.github.simonerecchia.renewables.client.screen.MetStationScreen;
import io.github.simonerecchia.renewables.client.screen.PlantControllerScreen;
import io.github.simonerecchia.renewables.client.screen.PowerInfoScreen;
import io.github.simonerecchia.renewables.client.screen.PvArrayScreen;
import io.github.simonerecchia.renewables.client.screen.PvCombinerScreen;
import io.github.simonerecchia.renewables.client.screen.PvInverterScreen;
import io.github.simonerecchia.renewables.client.screen.WindTurbineScreen;

import java.util.List;
import java.util.function.Function;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Which screen the wrench opens for which kind of machine.
 *
 * A table, so giving a machine its own panel is one line here and not another branch in a chain; anything with no
 * panel of its own - the pole, the cabin, the kiosk, the towers, transformers, switches, a run laid on the ground -
 * gets the read-only power card.
 */
@OnlyIn(Dist.CLIENT)
public final class WrenchScreens {
	private record Entry(Class<? extends BlockEntity> type, Function<BlockPos, Screen> screen) {
	}

	private static final List<Entry> PANELS = List.of(
			new Entry(WindTurbineBlockEntity.class, WindTurbineScreen::new),
			new Entry(PvInverterBlockEntity.class, PvInverterScreen::new),
			new Entry(PvArrayBlockEntity.class, PvArrayScreen::new),
			new Entry(PvCombinerBlockEntity.class, PvCombinerScreen::new),
			new Entry(MetStationBlockEntity.class, MetStationScreen::new),
			new Entry(PlantControllerBlockEntity.class, PlantControllerScreen::new));

	private WrenchScreens() {
	}

	/** The screen for the machine at {@code pos}, which is {@code machine}. */
	public static Screen forMachine(BlockEntity machine, BlockPos pos) {
		for (Entry entry : PANELS) {
			if (entry.type().isInstance(machine)) return entry.screen().apply(pos);
		}

		return new PowerInfoScreen(pos);
	}
}
