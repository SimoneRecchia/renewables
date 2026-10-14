package io.github.simonerecchia.renewables.api.power;

import net.minecraft.util.Mth;

/**
 * The disturbance that travels with a delivery of power: a surge, a brownout or a disconnection.
 *
 * Only turbines make them; an inverter always delivers {@link #NONE}.  A consumer is free to react or to
 * ignore it - nothing in the mod does.
 *
 * @param surgeSeverity  how big the peak is, 0 to 1
 * @param durationTicks  ticks the disturbance has left, never negative
 * @param disconnecting  the supply is being dropped right now
 * @param voltageFactor  1 for none, down to 0 for a total brownout
 */
public record PowerDeliveryEvent(double surgeSeverity, int durationTicks, boolean disconnecting, double voltageFactor) {
	/** Nothing is wrong. */
	public static final PowerDeliveryEvent NONE = new PowerDeliveryEvent(0.0, 0, false, 1.0);

	public PowerDeliveryEvent {
		surgeSeverity = Mth.clamp(surgeSeverity, 0.0, 1.0);
		durationTicks = Math.max(0, durationTicks);
		voltageFactor = Mth.clamp(voltageFactor, 0.0, 1.0);
	}
}
