package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.TransformerSpec;

/**
 * What carrying more than its rating does to a transformer: it heats, and its protection takes it off the network.
 *
 * A transformer does not limit the power through it - it passes what the line pushes and the load draws - so the
 * real answer to an overload is thermal.  The temperatures are those of the loading guide IEC 60076-7 (the difference
 * equations of its section 8.2.3, with the thermal characteristics its table gives for each kind of unit and the loss
 * ratio R of this unit's own Ecodesign figures): top oil follows the losses with the oil's time constant, the winding's
 * hot spot runs above it with the winding's.  The relays trip where the guide stops allowing any loading at all:
 *
 * <ul>
 * <li>the winding thermometer at a hot spot of {@value #TRIP_HOT_SPOT_C} °C and the oil thermometer at
 *     {@value #TRIP_TOP_OIL_C} °C, the limits of long-time emergency loading;</li>
 * <li>the overcurrent relay, within the second, above the current of long-time emergency loading: 1.8 times the
 *     rating for a distribution transformer, 1.5 times for a medium-power one.</li>
 * </ul>
 *
 * A tripped transformer stays out until a hand resets it, and that is refused until it is back inside the limits of
 * normal cyclic loading ({@value #RESET_HOT_SPOT_C} °C hot spot, {@value #RESET_TOP_OIL_C} °C top oil), as a
 * lockout relay would.  At its rating in the guide's 20 °C it settles at the 98 °C hot spot it is designed for and
 * runs for ever; a 20 % overload settles near 123 °C and also runs; the C130's 4 MW through the 2.5 MVA pad unit
 * trips it after some three hours; 26 MW trips it at once.
 *
 * Plain arithmetic, stepped by the block entity once a second and tested without a world.
 */
public final class TransformerThermal {
	/** The ambient the guide rates a transformer's life at, °C. */
	public static final double AMBIENT_C = 20.0;
	/** Long-time emergency loading: the hot spot and top oil no load may take a transformer past, °C. */
	public static final double TRIP_HOT_SPOT_C = 140.0;
	public static final double TRIP_TOP_OIL_C = 115.0;
	/** Normal cyclic loading: below both, a tripped transformer may be put back, °C. */
	public static final double RESET_HOT_SPOT_C = 120.0;
	public static final double RESET_TOP_OIL_C = 105.0;

	/** Why the protection opened; NONE while it is in service. */
	public enum Trip {
		NONE, OVERCURRENT, HOT_SPOT, TOP_OIL
	}

	/**
	 * One kind of unit's thermal characteristics, IEC 60076-7 table 5.
	 *
	 * @param oilExponent      x, of the total losses in the top oil's rise
	 * @param windingExponent  y, of the current in the hot spot's gradient
	 * @param k11              k11, on the oil time constant
	 * @param k21              k21, the hot spot's overshoot
	 * @param k22              k22, on the winding time constant
	 * @param oilMinutes       the oil time constant
	 * @param windingMinutes   the winding time constant
	 * @param oilRiseK         top-oil rise over ambient at rated losses
	 * @param gradientK        hot spot over top oil at rated current
	 * @param currentLimit     the current of long-time emergency loading, per unit of the rating
	 */
	public record Kind(double oilExponent, double windingExponent, double k11, double k21, double k22, double oilMinutes,
			double windingMinutes, double oilRiseK, double gradientK, double currentLimit) {
		/** A distribution transformer, ONAN: up to 2500 kVA. */
		public static final Kind DISTRIBUTION = new Kind(0.8, 1.6, 1.0, 1.0, 2.0, 180.0, 4.0, 55.0, 23.0, 1.8);
		/** A medium-power transformer, ONAF: up to 100 MVA, fans running. */
		public static final Kind MEDIUM_ONAF = new Kind(0.8, 1.3, 0.5, 2.0, 2.0, 150.0, 7.0, 52.0, 26.0, 1.5);

		public static Kind of(TransformerSpec spec) {
			return spec.ratingKva() <= 2500.0 ? DISTRIBUTION : MEDIUM_ONAF;
		}
	}

	private final Kind kind;
	/** R: load losses at rated current over no-load losses. */
	private final double lossRatio;

	/** Top oil over ambient, and the hot spot's two gradient terms, K. */
	private double oilRise;
	private double gradient1;
	private double gradient2;
	private Trip trip = Trip.NONE;

	public TransformerThermal(TransformerSpec spec) {
		this(Kind.of(spec), spec.coreLossKw() > 0.0 ? spec.loadLossKw() / spec.coreLossKw() : 0.0);
	}

	public TransformerThermal(Kind kind, double lossRatio) {
		this.kind = kind;
		this.lossRatio = lossRatio;
	}

	/**
	 * Runs the temperatures on by {@code seconds} at a load of {@code load} times the rating, and trips if a limit is
	 * passed.  A tripped transformer carries nothing, whatever it is told, and cools.
	 *
	 * @return whether the protection opened on this step
	 */
	public boolean step(double load, double seconds) {
		double k = trip == Trip.NONE && Double.isFinite(load) ? Math.abs(load) : 0.0;
		double minutes = seconds / 60.0;

		double oilTarget = Math.pow((1.0 + k * k * lossRatio) / (1.0 + lossRatio), kind.oilExponent()) * kind.oilRiseK();
		double gradient = kind.gradientK() * Math.pow(k, kind.windingExponent());
		oilRise += minutes / (kind.k11() * kind.oilMinutes()) * (oilTarget - oilRise);
		gradient1 += minutes / (kind.k22() * kind.windingMinutes()) * (kind.k21() * gradient - gradient1);
		gradient2 += minutes / (kind.oilMinutes() / kind.k22()) * ((kind.k21() - 1.0) * gradient - gradient2);

		if (trip != Trip.NONE) return false;

		if (k > kind.currentLimit()) {
			trip = Trip.OVERCURRENT;
		} else if (hotSpot() >= TRIP_HOT_SPOT_C) {
			trip = Trip.HOT_SPOT;
		} else if (topOil() >= TRIP_TOP_OIL_C) {
			trip = Trip.TOP_OIL;
		}

		return trip != Trip.NONE;
	}

	public double topOil() {
		return AMBIENT_C + oilRise;
	}

	public double hotSpot() {
		return topOil() + gradient1 - gradient2;
	}

	public Trip trip() {
		return trip;
	}

	public boolean tripped() {
		return trip != Trip.NONE;
	}

	/** Whether it is cool enough to be put back: inside the limits of normal cyclic loading. */
	public boolean mayReset() {
		return hotSpot() < RESET_HOT_SPOT_C && topOil() < RESET_TOP_OIL_C;
	}

	/** Puts a tripped transformer back in service if it may be; answers whether it is in service now. */
	public boolean reset() {
		if (trip != Trip.NONE && mayReset()) trip = Trip.NONE;

		return trip == Trip.NONE;
	}

	// ---- saving ----

	public double[] state() {
		return new double[] {oilRise, gradient1, gradient2};
	}

	public void restore(double[] state, Trip trip) {
		if (state.length == 3) {
			oilRise = finite(state[0]);
			gradient1 = finite(state[1]);
			gradient2 = finite(state[2]);
		}

		this.trip = trip == null ? Trip.NONE : trip;
	}

	private static double finite(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}
}
