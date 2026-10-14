package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.api.power.IEnergyBudget;
import io.github.simonerecchia.renewables.api.power.TickBudget;
import io.github.simonerecchia.renewables.compat.energy.EnergyBridge;
import io.github.simonerecchia.renewables.core.ServerConfig;

import java.util.function.LongSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * The energy budget of one generator, with the server's limits already applied.
 *
 * A turbine and an inverter each own one, call {@link #open} every tick once they know what they are making, and
 * answer the {@link IEnergyBudget} methods by delegating here.  Both kinds go through the same rule: the offer is
 * the production, held to the configured share of the nameplate and to the absolute ceiling.
 *
 * One joule is spent once.  The wire grid reads the generator at the end of the server tick and takes what other
 * mods left ({@link #netKw}); what it actually carried away it reports back ({@link #drawForGrid}), and that is gone
 * from the budget too.  Otherwise a cable of another mod that ticks <em>before</em> the generator would find the
 * same residue still on offer at the start of the next tick and take it a second time.  A budget that has not been
 * opened this tick or the last belongs to a machine that is not being simulated (its chunk does not tick, its level
 * is asleep), and offers nothing to anyone.
 */
public final class GeneratorBudget implements IEnergyBudget {
	/** Which family's configuration applies. */
	public enum Kind {
		TURBINE, PHOTOVOLTAIC
	}

	/** The server's export limits as plain numbers, so the budget can be tested without a configuration. */
	interface Limits {
		boolean enabled();

		/** The share of the nameplate that may be offered, 0 to 1. */
		double fraction();

		/** An absolute ceiling in Joules per tick ({@link Double#MAX_VALUE} for none). */
		double ceiling();
	}

	private static final long NEVER = Long.MIN_VALUE;

	private final Limits limits;
	private final LongSupplier clock;
	private final TickBudget budget = new TickBudget();
	private double ratedKw;
	private long openedAt = NEVER;
	/** Joules the wire grid carried away from this tick's budget. */
	private double gridJoules;

	public GeneratorBudget(Kind kind) {
		this(configured(kind), GeneratorBudget::serverTick);
	}

	GeneratorBudget(Limits limits, LongSupplier clock) {
		this.limits = limits;
		this.clock = clock;
	}

	/**
	 * Starts this tick's budget.
	 *
	 * @param producedKw the power being made now
	 * @param ratedKw    the nameplate, which the share is a share of
	 */
	public void open(double producedKw, double ratedKw) {
		this.ratedKw = ratedKw;
		openedAt = clock.getAsLong();
		gridJoules = 0.0;
		budget.open(EnergyBudgets.offeredJoules(limits.enabled(), producedKw, ratedKw, limits.fraction(), limits.ceiling()));
	}

	/**
	 * What the wire grid may carry away this tick, in kW, once other mods have taken what they took; nothing from a
	 * generator that is not being simulated.  Asking twice in a tick (a switch recalculates the grid) gives the
	 * same answer: the grid's own draw is not subtracted.
	 */
	public double netKw(double producedKw) {
		return live() ? EnergyBudgets.netKw(producedKw, budget.claimed()) : 0.0;
	}

	/**
	 * The grid carried {@code kw} away this tick: that much of the offer is spent and no other mod may claim it.  The
	 * last report of a tick stands, since a recalculation in the same tick replaces the one before.
	 */
	public void drawForGrid(double kw) {
		gridJoules = Math.max(0.0, kw) * EnergyBridge.JOULES_PER_KW;
	}

	/** Joules other mods have taken from this tick's budget. */
	public double claimedJoules() {
		return budget.claimed();
	}

	@Override
	public double getAvailableJoules() {
		if (!limits.enabled() || !live()) return 0.0;

		return Math.max(0.0, budget.available() - gridJoules);
	}

	@Override
	public double getMaxJoulesPerTick() {
		return EnergyBudgets.maxJoulesPerTick(limits.enabled(), ratedKw, limits.fraction(), limits.ceiling());
	}

	@Override
	public double claimJoules(double joules, boolean simulate) {
		double available = getAvailableJoules();
		if (!(available > 0.0)) return 0.0;

		return budget.claim(Math.min(joules, available), simulate);
	}

	/** How many joules one kilowatt is for a tick: here so a machine need not reach into the bridge for it. */
	public static double joulesPerKw() {
		return EnergyBridge.JOULES_PER_KW;
	}

	/** Opened this server tick or the one before: a cable that ticks before the generator still sees the last tick's. */
	private boolean live() {
		return openedAt != NEVER && clock.getAsLong() - openedAt <= 1;
	}

	private static long serverTick() {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		return server == null ? 0L : server.getTickCount();
	}

	private static Limits configured(Kind kind) {
		return new Limits() {
			@Override
			public boolean enabled() {
				return ServerConfig.externalEnergyEnabled();
			}

			@Override
			public double fraction() {
				return kind == Kind.TURBINE ? ServerConfig.turbineExportFraction() : ServerConfig.pvExportFraction();
			}

			@Override
			public double ceiling() {
				return kind == Kind.TURBINE ? ServerConfig.turbineMaxJoulesPerTick() : ServerConfig.pvMaxJoulesPerTick();
			}
		};
	}
}
