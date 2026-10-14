package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * How a power box divides what it has among the consumers in its field, once a tick.
 *
 * Each consumer asks for a power and has a least it can run on; its <em>demand</em> is the larger of the two.
 * What the box has is shared in proportion to demand, a consumer is never given more than a tenth over what it
 * wants, and 8 % of what is given is lost on the way: so a box with exactly enough gives each consumer 0.92 of its
 * demand and the consumer sees a voltage factor of 0.92, and for a consumer to get all it asks for the box needs
 * about 1.087 times as much as the field asks.
 *
 * The event riding on the supply - a surge, a brownout, a disconnection - is what can trip the box's own breaker
 * (a severe surge that lasts), after which every consumer gets nothing and a disconnection until the breaker
 * re-arms.  That is rolled <em>once</em> a tick, not once a consumer: with a lot of consumers in a field the
 * chance of a trip would otherwise grow with their number rather than with the surge.
 *
 * Pure: no Minecraft state, the dice are passed in, so every figure here is a test.
 */
public final class PowerShare {
	/** What reaches a consumer of what was sent: the rest is lost in the field. */
	public static final double DELIVERY = 0.92;
	/** A consumer is not given more than this much over its demand. */
	public static final double OVERSUPPLY = 1.1;
	/** Ticks the box's breaker stays tripped. */
	public static final int TRIP_TICKS = 60;
	/** A surge this severe, for longer than {@link #SURGE_TICKS}, can trip the breaker. */
	public static final double SURGE_SEVERITY = 0.85;
	public static final int SURGE_TICKS = 4;
	public static final int LONG_SURGE_TICKS = 8;
	/** The chance of a trip at the severity threshold itself; more severity, a longer surge and a starved consumer add to it. */
	private static final double SURGE_THRESHOLD_BASE = 0.1;

	private PowerShare() {
	}

	/** What one consumer wants: power for full efficiency and the least it will run on. */
	public record Demand(double requiredKw, double minimumKw) {
		/** The larger of the two, never negative and never not-a-number. */
		double demandKw() {
			return Math.max(clean(requiredKw), clean(minimumKw));
		}
	}

	/** What one consumer is told: what reached it, whether that is enough to run on, and the event it rode in on. */
	public record Supply(double suppliedKw, boolean meetsMinimum, PowerDeliveryEvent event) {
	}

	/**
	 * The result of one tick's sharing.
	 *
	 * @param supplies   one per demand, in order
	 * @param tripped    whether the breaker is tripped after this tick (it was already, or it tripped now)
	 * @param newTrip    whether the dice tripped it <em>now</em>, which starts its {@link #TRIP_TICKS} over - also when it
	 *                   was tripped already, so a surge that goes on keeps the breaker out
	 * @param unusedKw   what was available and not drawn by anybody: the box banks it as Forge Energy
	 */
	public record Outcome(List<Supply> supplies, boolean tripped, boolean newTrip, double unusedKw) {
	}

	/**
	 * Shares {@code availableKw} among {@code demands}.
	 *
	 * @param availableKw    what the box has after the neighbours that take Forge Energy have had theirs
	 * @param incoming       the event the grid delivered with it
	 * @param alreadyTripped whether the breaker is tripped coming in
	 * @param roll           a number in [0, 1), asked for at most once and only if a surge is bad enough to need it
	 */
	public static Outcome share(double availableKw, List<Demand> demands, PowerDeliveryEvent incoming,
			boolean alreadyTripped, DoubleSupplier roll) {
		double available = clean(availableKw);
		double total = 0.0;
		for (Demand demand : demands) total += demand.demandKw();

		boolean tripped = alreadyTripped;
		boolean newTrip = false;
		boolean surge = incoming.surgeSeverity() > SURGE_SEVERITY && incoming.durationTicks() > SURGE_TICKS;
		double dice = Double.NaN;
		double drawn = 0.0;

		List<Supply> supplies = new ArrayList<>(demands.size());
		for (Demand demand : demands) {
			double wanted = demand.demandKw();
			double minimum = clean(demand.minimumKw());
			double quota = total <= 0.0 ? available : available * wanted / total;
			double regulated = Math.min(quota, wanted * OVERSUPPLY) * DELIVERY;

			if (surge) {
				double chance = SURGE_THRESHOLD_BASE + (incoming.surgeSeverity() - SURGE_SEVERITY) * 0.4;
				if (incoming.durationTicks() > LONG_SURGE_TICKS) chance += 0.1;
				if (quota < minimum * 0.5) chance += 0.05;

				if (Double.isNaN(dice)) dice = roll.getAsDouble();
				if (dice < chance) {
					tripped = true;
					newTrip = true;
				}
			}

			boolean cut = tripped;
			if (incoming.disconnecting()) {
				regulated = 0.0;
				cut = true;
			} else if (incoming.voltageFactor() < 1.0) {
				regulated *= Math.max(0.0, Math.min(1.0, incoming.voltageFactor()));
			}

			PowerDeliveryEvent told = incoming;
			if (cut) {
				regulated = 0.0;
				told = new PowerDeliveryEvent(incoming.surgeSeverity(), Math.max(incoming.durationTicks(), SURGE_TICKS), true, 0.0);
			} else if (wanted > 0.0 && regulated < wanted) {
				told = new PowerDeliveryEvent(incoming.surgeSeverity(), incoming.durationTicks(), false,
						Math.max(0.0, Math.min(1.0, regulated / wanted)));
			}

			drawn += regulated / DELIVERY;
			supplies.add(new Supply(regulated, regulated >= minimum, told));
		}

		return new Outcome(supplies, tripped, newTrip, Math.max(0.0, available - drawn));
	}

	private static double clean(double value) {
		return Double.isFinite(value) && value > 0.0 ? value : 0.0;
	}
}
