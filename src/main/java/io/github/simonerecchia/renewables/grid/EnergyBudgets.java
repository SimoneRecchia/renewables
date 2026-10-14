package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.api.power.Dispatchable;
import io.github.simonerecchia.renewables.compat.energy.EnergyBridge;

/**
 * The arithmetic of the per-tick energy budget a generator offers to other mods' cables, and of what is left for
 * the wire grid once they have taken theirs.
 *
 * The rule that keeps one joule from being spent twice: every tick a generator opens a budget; whatever other
 * mods' machines claim from it is gone; the wire grid is handed only what remains, {@code produced - claimed / 125}
 * kW.  The limits (a fraction of the nameplate, an optional absolute ceiling, a switch for the whole thing) are
 * configuration and arrive here as plain numbers, so this class is testable without a server.
 */
public final class EnergyBudgets {
	private EnergyBudgets() {
	}

	/**
	 * The most a generator may offer in one tick, in Joules.
	 *
	 * @param enabled  whether other mods may draw from generators at all
	 * @param ratedKw  the machine's nameplate power
	 * @param fraction the share of the nameplate that may be offered, 0 to 1
	 * @param ceiling  an absolute ceiling in Joules per tick ({@link Double#MAX_VALUE} for none)
	 */
	public static double maxJoulesPerTick(boolean enabled, double ratedKw, double fraction, double ceiling) {
		if (!enabled) return 0.0;

		return Math.max(0.0, Math.min(ratedKw * EnergyBridge.JOULES_PER_KW * fraction, ceiling));
	}

	/**
	 * What a generator puts on the table this tick: what it makes, but not more than the most it may offer.  Zero when
	 * the budget is off or its maximum is not positive.
	 */
	public static double offeredJoules(boolean enabled, double producedKw, double ratedKw, double fraction, double ceiling) {
		double max = maxJoulesPerTick(enabled, ratedKw, fraction, ceiling);
		if (!(max > 0.0)) return 0.0;

		return Math.min(Math.max(0.0, producedKw) * EnergyBridge.JOULES_PER_KW, max);
	}

	/** What is left for the wire grid: the production less what was claimed, never below zero. */
	public static double netKw(double producedKw, double claimedJoules) {
		return Math.max(0.0, producedKw - Math.max(0.0, claimedJoules) / EnergyBridge.JOULES_PER_KW);
	}

	/**
	 * What the wire grid may carry away from a generator that has no figure of its own for it, worked out from what
	 * the {@link Dispatchable} contract does expose: its gross production, its maximum offer and what is still
	 * unclaimed.  The offer is the lesser of the two first; what has gone is the offer less the rest.
	 */
	public static double netKw(Dispatchable generator) {
		double grossJoules = Math.max(0.0, generator.getGrossJoulesPerTick());
		double offered = Math.min(grossJoules, Math.max(0.0, generator.getMaxJoulesPerTick()));
		double claimed = Math.max(0.0, offered - Math.max(0.0, generator.getAvailableJoules()));

		return netKw(grossJoules / EnergyBridge.JOULES_PER_KW, claimed);
	}
}
