package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

/**
 * The two numbers every hop of the network is made of: how much of the power gets across a group of cables,
 * and how much of a disturbance does.  Pure arithmetic, so the solver's tests can pin them exactly.
 */
final class GridMath {
	/** Share of the power one block of single cable loses: one percent. */
	static final double LOSS_PER_BLOCK = 0.01;
	/** What a hop never goes below: a long run still delivers a tenth of what entered it. */
	static final double FLOOR = 0.1;
	/** What each cable beyond the first takes off the penalty. */
	static final double PARALLEL_BOOST = 0.35;
	/** A disturbance loses a little more than the power does at every hop. */
	static final double DISTURBANCE_DECAY = 0.95;

	private GridMath() {
	}

	/**
	 * The fraction of power that crosses a group of parallel cables.
	 *
	 * {@code 1 - (d / 100) / (1 + 0.35 (n - 1))}, never below one tenth.  The conductor's resistance and section do
	 * not come into it: the type of conductor decides what may be strung and how far, not what is lost.
	 *
	 * @param cables     how many distinct spans join the same two buses (at least one)
	 * @param meanLength the average length of those spans, in blocks
	 */
	static double efficiency(int cables, double meanLength) {
		int n = Math.max(1, cables);
		double penalty = (Math.max(0.0, meanLength) * LOSS_PER_BLOCK) / (1.0 + PARALLEL_BOOST * (n - 1));
		return Math.max(FLOOR, 1.0 - penalty);
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
