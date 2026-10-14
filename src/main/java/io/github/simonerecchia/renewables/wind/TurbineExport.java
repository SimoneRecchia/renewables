package io.github.simonerecchia.renewables.wind;

/**
 * The arithmetic of sharing one tick's output between the wire grid and the energy systems of other mods, which is
 * where a joule must never be counted twice.
 *
 * Every tick a generator puts a budget on the table: what it produced, in joules, but no more than the most it is
 * willing to hand to other mods.  Their cables take what they take from it, and the grid is left with the
 * production less what was taken.
 */
public final class TurbineExport {
	/** One kW for one tick: 125 J, which is 50 FE at 2.5 J per FE. */
	public static final double JOULES_PER_KW_TICK = 125.0;

	private TurbineExport() {
	}

	/**
	 * The most a turbine offers other mods in one tick: its nameplate times the configured share, under the absolute
	 * ceiling if there is one ({@link Double#MAX_VALUE} for none).
	 */
	public static double capacityJoules(double ratedKw, double exportFraction, double ceilingJoules) {
		return Math.min(ratedKw * JOULES_PER_KW_TICK * exportFraction, ceilingJoules);
	}

	/** What goes on the table this tick: the production, held to the capacity, and nothing at all if there is no capacity. */
	public static double offeredJoules(double producedKw, double capacityJoules) {
		if (capacityJoules <= 0.0) return 0.0;

		return Math.min(Math.max(0.0, producedKw) * JOULES_PER_KW_TICK, capacityJoules);
	}

	/** What the wire grid can carry away this tick, in kW: the production, less what other mods' cables claimed. */
	public static double gridKw(double producedKw, double claimedJoules) {
		return Math.max(0.0, producedKw - claimedJoules / JOULES_PER_KW_TICK);
	}
}
