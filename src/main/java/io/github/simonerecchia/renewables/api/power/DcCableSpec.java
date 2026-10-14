package io.github.simonerecchia.renewables.api.power;



import net.minecraft.resources.ResourceLocation;

/**
 * One direct-current cable, described the way a cable datasheet describes one.
 *
 * A solar cable to EN 50618 (H1Z2Z2-K) has a tinned class 5 copper conductor rated for 120 °C, and its current
 * tables are given at 60 °C ambient - so a cable carrying its rated current runs 60 K over the air.
 */
public record DcCableSpec(
		ResourceLocation id,
		/** Model designation. */
		String displayName,
		/** Conductor cross-section, mm². */
		double crossSectionMm2,
		/** Insulation rating, volts DC. */
		double maxSystemVolts,
		/** Continuous current of a single cable in free air at 60 °C ambient, amps. */
		double freeAirAmps,
		/** What fraction of that rating survives being buried. */
		double buriedFactor,
		/** Resistance of one conductor at 20 °C, ohms per km (IEC 60228). */
		double ohmsPerKm,
		/** Whether this is trunk cable rather than string cable. */
		boolean trunk
) {
	/** Temperature coefficient of copper's resistance at 20 °C, per kelvin (IEC 60228). */
	public static final double COPPER_ALPHA = 0.00393;
	/** The ambient the ratings are tabulated at. */
	public static final double RATED_AMBIENT_C = 60.0;
	/** The conductor temperature the insulation is rated for, which is what a rated current reaches. */
	public static final double MAX_CONDUCTOR_C = 120.0;

	public DcCableSpec {
		if (crossSectionMm2 <= 0.0) throw new IllegalArgumentException(id + ": a conductor has a cross-section");
		if (maxSystemVolts <= 0.0) throw new IllegalArgumentException(id + ": insulation has a rating");
		if (freeAirAmps <= 0.0) throw new IllegalArgumentException(id + ": a cable carries some current");
		if (buriedFactor <= 0.0 || buriedFactor > 1.0) throw new IllegalArgumentException(id + ": burying a cable does not improve it");
		if (ohmsPerKm <= 0.0) throw new IllegalArgumentException(id + ": copper has resistance");
	}

	/** What the cable will carry over a run that is partly buried, in amps. */
	public double ampacity(double buriedFraction) {
		if (buriedFraction <= 0.0) return freeAirAmps;

		return freeAirAmps * buriedFactor;
	}

	/** One conductor's resistance at a conductor temperature, ohms per km. */
	public double ohmsPerKmAt(double conductorC) {
		return ohmsPerKm * (1.0 + COPPER_ALPHA * (conductorC - 20.0));
	}

	/**
	 * The conductor's temperature carrying {@code amps} in air at {@code ambientC}: the rise goes with the square of
	 * the current, and is the rating's 60 K at the rated current.
	 */
	public double conductorTemperature(double ambientC, double amps, double buriedFraction) {
		double load = Math.max(0.0, amps) / ampacity(buriedFraction);
		return ambientC + (MAX_CONDUCTOR_C - RATED_AMBIENT_C) * load * load;
	}

	/** Resistance of the round trip over a run, ohms: out along one conductor and back along the other. */
	public double loopResistance(double metres, double conductorC) {
		return 2.0 * ohmsPerKmAt(conductorC) / 1000.0 * Math.max(0.0, metres);
	}

	/** Volts lost over the run at a given current. */
	public double voltageDrop(double amps, double metres, double conductorC) {
		return Math.max(0.0, amps) * loopResistance(metres, conductorC);
	}

	/** Power burnt in the copper as a fraction of what is passing through it: I²R over VI, which is IR over V. */
	public double lossFraction(double amps, double volts, double metres, double conductorC) {
		if (volts <= 0.0 || amps <= 0.0) return 0.0;

		return Math.min(1.0, voltageDrop(amps, metres, conductorC) / volts);
	}
}
