package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

import java.util.function.DoubleSupplier;
import javax.annotation.Nullable;

/**
 * Where surges, brownouts and drop-outs come from: a turbine in gusty or violent wind.
 *
 * One event is drawn when none is running, and once drawn it is <em>held</em> and counted down, so a storm is a
 * run of several ticks of the same trouble rather than a fresh dice roll every tick.  (A roll per tick at the
 * storm probability would drop a consumer out for about half of all ticks.)  The caller keeps the last event per
 * turbine and hands it back; this class has no memory of its own.
 */
final class DisturbanceModel {
	/** Calm air gives a severity of about zero: below this turbulence intensity a site is not gusty at all. */
	private static final double CALM_TURBULENCE = 0.14;
	private static final double GUSTY_WIND_FROM = 8.0;
	private static final double GUSTY_WIND_SPAN = 12.0;
	/** A severity above this is an event that lasts. */
	private static final double LASTING_SEVERITY = 0.25;
	private static final double STORM_WIND = 18.0;
	private static final double STORM_TURBULENCE = 0.24;
	private static final double MAX_DROP_OUT_CHANCE = 0.5;

	private DisturbanceModel() {
	}

	/**
	 * The event this tick.
	 *
	 * @param previous the event returned last tick for the same turbine, or null for the first
	 * @param random   uniform in [0, 1)
	 */
	static PowerDeliveryEvent next(@Nullable PowerDeliveryEvent previous, double meanWind, double turbulence, DoubleSupplier random) {
		if (previous != null && previous.durationTicks() > 0) {
			int left = previous.durationTicks() - 1;
			// an event that has run its course is over, and with it the drop-out and the sag it carried
			return new PowerDeliveryEvent(previous.surgeSeverity(), left, previous.disconnecting() && left > 0,
					left > 0 ? previous.voltageFactor() : 1.0);
		}

		return draw(meanWind, turbulence, random);
	}

	/** A fresh event from the weather. */
	static PowerDeliveryEvent draw(double meanWind, double turbulence, DoubleSupplier random) {
		double gust = clamp((meanWind - GUSTY_WIND_FROM) / GUSTY_WIND_SPAN, 0.0, 1.0);
		double base = Math.max(0.0, (turbulence - CALM_TURBULENCE) * 1.7 + gust * 0.4);
		double severity = Math.max(0.0, base + random.getAsDouble() * 0.05);
		int duration = severity > LASTING_SEVERITY ? 4 + (int) (random.getAsDouble() * 5.0) : 0;
		boolean disconnecting = false;
		double sag = 1.0;

		if (meanWind > STORM_WIND || turbulence > STORM_TURBULENCE) {
			double chance = clamp((meanWind - STORM_WIND) * 0.04 + (turbulence - STORM_TURBULENCE) * 0.7, 0.0, MAX_DROP_OUT_CHANCE);
			if (random.getAsDouble() < chance) {
				disconnecting = true;
				duration = Math.max(duration, 6 + (int) (random.getAsDouble() * 5.0));
				sag = 0.4 + random.getAsDouble() * 0.2;
			}
		}

		return new PowerDeliveryEvent(severity, duration, disconnecting, sag);
	}

	private static double clamp(double value, double low, double high) {
		return Math.max(low, Math.min(high, value));
	}
}
