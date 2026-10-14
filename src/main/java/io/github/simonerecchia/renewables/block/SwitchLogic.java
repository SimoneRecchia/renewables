package io.github.simonerecchia.renewables.block;

/**
 * The rules a disconnector and a breaker follow, as numbers: when there is load on them, when a breaker trips,
 * and what a change in the redstone signal means.
 *
 * Kept out of the block so that the figures that decide a game's worth of behaviour - 2 % of the rating is
 * "load", the rating itself is the trip, twenty ticks between looks - can be tested without a world.
 */
public final class SwitchLogic {
	/** A disconnector refuses to open with more than this fraction of its rating going through it. */
	public static final double LOAD_FRACTION = 0.02;

	/** A breaker looks at what it is carrying this often, in game ticks. */
	public static final int TRIP_INTERVAL = 20;

	private SwitchLogic() {
	}

	/** Whether there is load on a switch: more than 2 % of its rating (720 kW of 36 000 kW). */
	public static boolean underLoad(double deliveredKw, double ratedKw) {
		return deliveredKw > ratedKw * LOAD_FRACTION;
	}

	/** Whether a closed breaker carrying this much trips: above its rating. */
	public static boolean overRated(double deliveredKw, double ratedKw) {
		return deliveredKw > ratedKw;
	}

	/** Whether a hand may open the switch now.  A breaker always; a disconnector only with the load off. */
	public static boolean mayOpenByHand(boolean breaksLoad, double deliveredKw, double ratedKw) {
		return breaksLoad || !underLoad(deliveredKw, ratedKw);
	}

	/** Whether this is a tick on which a breaker looks at its load. */
	public static boolean isTripTick(long gameTime) {
		return gameTime % TRIP_INTERVAL == 0;
	}

	/** What a redstone signal did since it was last looked at: +1 appeared, -1 went, 0 is the same. */
	public static int edge(boolean before, boolean now) {
		if (before == now) return 0;

		return now ? 1 : -1;
	}
}
