package io.github.simonerecchia.renewables.registry;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.catalog.ConductorCatalog;

import java.util.Collection;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The one creative tab, listing every item in the order a player meets them (76 entries, none repeated). */
public final class ModTabs {
	public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Renewables.MOD_ID);

	public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup." + Renewables.MOD_ID + ".main"))
			.icon(() -> new ItemStack(ModItems.CONDUCTORS.get(ConductorCatalog.AAAC_228.id()).get()))
			.displayItems((parameters, output) -> {
				output.accept(ModItems.POWER_WRENCH.get());
				accept(output, ModItems.CONDUCTORS.values());
				accept(output, ModItems.TRANSFORMERS.values());
				output.accept(ModItems.UTILITY_POLE.get());
				accept(output, ModItems.LATTICE_TOWERS.values());
				output.accept(ModItems.ELECTRIC_CABIN.get());
				output.accept(ModItems.POWER_BOX.get());
				accept(output, ModItems.TURBINES.values());
				output.accept(ModItems.TURBINE_TOWER.get());
				accept(output, ModItems.PV_ARRAYS.values());
				accept(output, ModItems.DC_CABLES.values());
				accept(output, ModItems.PV_COMBINERS.values());
				accept(output, ModItems.PV_INVERTERS.values());
				accept(output, ModItems.SWITCHGEARS.values());
				output.accept(ModItems.MET_STATION.get());
				output.accept(ModItems.PLANT_CONTROLLER.get());
				output.accept(ModItems.CIRCUIT_BOARD.get());
				output.accept(ModItems.CPU.get());
				output.accept(ModItems.SCREEN.get());
				output.accept(ModItems.INSULATOR.get());
				output.accept(ModItems.METAL_CASING.get());
				output.accept(ModItems.MOTOR_CORE.get());
				accept(output, ModItems.PARTS.values());
			})
			.build());

	private ModTabs() {
	}

	private static void accept(CreativeModeTab.Output output, Collection<RegistryObject<Item>> items) {
		for (Supplier<Item> item : items) {
			output.accept(item.get());
		}
	}
}
