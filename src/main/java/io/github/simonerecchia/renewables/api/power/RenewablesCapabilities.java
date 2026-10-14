package io.github.simonerecchia.renewables.api.power;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/** The mod's capabilities, for other mods to expose and to ask for. */
public final class RenewablesCapabilities {
	/** A block entity that takes power from a power box's field. */
	public static final Capability<IElectricPowerConsumer> ELECTRIC_CONSUMER = CapabilityManager.get(new CapabilityToken<>() {
	});

	private RenewablesCapabilities() {
	}
}
