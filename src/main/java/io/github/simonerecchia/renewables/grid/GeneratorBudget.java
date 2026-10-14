package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.api.power.IEnergyBudget;
import io.github.simonerecchia.renewables.api.power.TickBudget;
import io.github.simonerecchia.renewables.compat.energy.EnergyBridge;
import io.github.simonerecchia.renewables.core.ServerConfig;

/**
 * The energy budget of one generator, with the server's limits already applied.
 *
 * A turbine and an inverter each own one, call {@link #open} every tick once they know what they are making, and
 * answer the {@link IEnergyBudget} methods by delegating here.  Both kinds go through the same rule: the offer is
 * the production, held to the configured share of the nameplate and to the absolute ceiling.  (The inverter used to
 * open its budget with the whole of its AC output and so ignore both.)
 */
public final class GeneratorBudget implements IEnergyBudget {
	/** Which family's configuration applies. */
	public enum Kind {
		TURBINE, PHOTOVOLTAIC
	}

	private final Kind kind;
	private final TickBudget budget = new TickBudget();
	private double ratedKw;

	public GeneratorBudget(Kind kind) {
		this.kind = kind;
	}

	/**
	 * Starts this tick's budget.
	 *
	 * @param producedKw the power being made now
	 * @param ratedKw    the nameplate, which the share is a share of
	 */
	public void open(double producedKw, double ratedKw) {
		this.ratedKw = ratedKw;
		budget.open(EnergyBudgets.offeredJoules(ServerConfig.externalEnergyEnabled(), producedKw, ratedKw, fraction(), ceiling()));
	}

	/** What the wire grid may carry away this tick, in kW, once other mods have taken what they took. */
	public double netKw(double producedKw) {
		return EnergyBudgets.netKw(producedKw, budget.claimed());
	}

	/** Joules other mods have taken from this tick's budget. */
	public double claimedJoules() {
		return budget.claimed();
	}

	@Override
	public double getAvailableJoules() {
		return ServerConfig.externalEnergyEnabled() ? budget.available() : 0.0;
	}

	@Override
	public double getMaxJoulesPerTick() {
		return EnergyBudgets.maxJoulesPerTick(ServerConfig.externalEnergyEnabled(), ratedKw, fraction(), ceiling());
	}

	@Override
	public double claimJoules(double joules, boolean simulate) {
		return ServerConfig.externalEnergyEnabled() ? budget.claim(joules, simulate) : 0.0;
	}

	/** How many joules one kilowatt is for a tick: here so a machine need not reach into the bridge for it. */
	public static double joulesPerKw() {
		return EnergyBridge.JOULES_PER_KW;
	}

	private double fraction() {
		return kind == Kind.TURBINE ? ServerConfig.turbineExportFraction() : ServerConfig.pvExportFraction();
	}

	private double ceiling() {
		return kind == Kind.TURBINE ? ServerConfig.turbineMaxJoulesPerTick() : ServerConfig.pvMaxJoulesPerTick();
	}
}
