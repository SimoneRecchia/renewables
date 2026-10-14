package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

/**
 * The two numbers every hop of the network is made of: how much of the power gets across a group of cables,
 * and how much of a disturbance does.  Pure arithmetic, so the solver's tests can pin them exactly.
 *
 * A span loses what its copper or aluminium heats: three phases of resistance R carrying P at line voltage U
 * burn {@code P^2 R / (U^2 cos^2 phi)}.  That is the whole of it, which is why a street bundle at 400 V loses in
 * a few hundred metres what a 400 kV line loses in a hundred kilometres.
 */
final class GridMath {
	/**
	 * The power factor the network carries its flow at.  The grid moves active power only, and a plant runs at
	 * unity unless its operator is asked for reactive power, so cos phi = 1.
	 */
	static final double POWER_FACTOR = 1.0;
	/** A disturbance loses a little more than the power does at every hop. */
	static final double DISTURBANCE_DECAY = 0.95;

	private GridMath() {
	}

	/**
	 * k in {@code loss = k P^2}, per kW: {@code 1000 R / (U^2 cos^2 phi)} for R in ohms a phase and U in volts line
	 * to line.  Zero for a span of no resistance or no known voltage, which then loses nothing.
	 */
	static double lossCoefficient(double ohms, double volts) {
		if (!(ohms > 0.0) || !(volts > 0.0)) return 0.0;

		return 1000.0 * ohms / (volts * volts * POWER_FACTOR * POWER_FACTOR);
	}

	/**
	 * What reaches the far end of a line of coefficient {@code k} for {@code sentKw} sent into it.
	 *
	 * {@code P - k P^2}, up to the transfer limit of a resistive line fed at its nominal voltage: past
	 * {@code 1 / (2k)} sent, more current only drops the far end's voltage further, so the most a line can ever
	 * deliver is {@code 1 / (4k)} and what is offered beyond what it can take does not get in at all.
	 */
	static double delivered(double sentKw, double k) {
		if (!(sentKw > 0.0)) return 0.0;
		if (!(k > 0.0)) return sentKw;

		double accepted = Math.min(sentKw, 1.0 / (2.0 * k));
		return accepted - k * accepted * accepted;
	}

	/**
	 * The share of what is sent that arrives, when the line is carrying {@code totalKw} in all.
	 *
	 * Every generator's flow down one line sees the same fraction, the one the line's whole load sets, so the loss
	 * of two generators sharing it is that of their sum - not the smaller sum of two losses each worked out alone.
	 */
	static double keptFraction(double totalKw, double k) {
		if (!(totalKw > 0.0) || !(k > 0.0)) return 1.0;

		return delivered(totalKw, k) / totalKw;
	}

	/** The same through a machine that burns {@code lossKw} carrying {@code totalKw}: a transformer's core and windings. */
	static double keptThrough(double totalKw, double lossKw) {
		if (!(totalKw > 0.0)) return 0.0;

		return Math.max(0.0, 1.0 - Math.max(0.0, lossKw) / totalKw);
	}

	/** A disturbance after one hop of efficiency {@code eta}: weaker, shorter, and a brownout only while it lasts. */
	static PowerDeliveryEvent attenuate(PowerDeliveryEvent event, double eta) {
		if (event == PowerDeliveryEvent.NONE) return event;

		double kept = Math.max(0.0, Math.min(1.0, eta));
		int duration = Math.max(0, event.durationTicks() - 1);
		boolean disconnecting = event.disconnecting() && duration > 0;
		double factor = duration > 0 ? 1.0 - (1.0 - event.voltageFactor()) * kept : 1.0;

		return new PowerDeliveryEvent(event.surgeSeverity() * kept * DISTURBANCE_DECAY, duration, disconnecting, factor);
	}

	/** Two disturbances meeting at one place: the worse of each. */
	static PowerDeliveryEvent merge(PowerDeliveryEvent a, PowerDeliveryEvent b) {
		if (a == PowerDeliveryEvent.NONE) return b;
		if (b == PowerDeliveryEvent.NONE) return a;

		return new PowerDeliveryEvent(Math.max(a.surgeSeverity(), b.surgeSeverity()),
				Math.max(a.durationTicks(), b.durationTicks()),
				a.disconnecting() || b.disconnecting(),
				Math.min(a.voltageFactor(), b.voltageFactor()));
	}
}
