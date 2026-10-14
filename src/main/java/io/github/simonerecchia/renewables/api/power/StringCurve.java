package io.github.simonerecchia.renewables.api.power;

/**
 * What a string, or a set of strings in parallel, gives when its tracker holds it at a voltage other than its own
 * maximum power point.
 *
 * The I-V curve cut down to the two segments a datasheet pins: the current stays at Imp up to Vmp, then falls in a
 * straight line to nothing at open circuit.  Held below its knee a string gives V / Vmp of its maximum; held above it,
 * less and less, and nothing at all past its open-circuit voltage - which is the case that matters: on one bus with a
 * higher string a low one is pushed past its Voc and makes nothing.
 */
public final class StringCurve {
	/** Voltages a single tracker tries across its window, besides each source's own knee and the window's ends. */
	private static final int SCAN_STEPS = 64;

	private StringCurve() {
	}

	/** Fraction of its maximum power a source gives held at a bus voltage. */
	public static double powerFraction(double busVolts, double vmp, double voc) {
		if (busVolts <= 0.0 || vmp <= 0.0) return 0.0;
		if (busVolts <= vmp) return busVolts / vmp;
		if (voc <= vmp || busVolts >= voc) return 0.0;

		return busVolts / vmp * (voc - busVolts) / (voc - vmp);
	}

	/** Where a source on a tracker of its own sits: its knee, or the nearest end of the window the tracker can hold. */
	public static double ownTrackerVolts(double vmp, double minVolts, double maxVolts) {
		return Math.max(minVolts, Math.min(maxVolts, vmp));
	}

	/** What all the sources give together on one bus, in the units of {@code power}. */
	public static double totalAt(double busVolts, double[] vmp, double[] voc, double[] power) {
		double total = 0.0;
		for (int i = 0; i < vmp.length; i++) {
			total += power[i] * powerFraction(busVolts, vmp[i], voc[i]);
		}

		return total;
	}

	/**
	 * The voltage one tracker settles a shared bus at: the one inside its window where the sources together give the
	 * most.  Every knee is tried, the two ends, and a scan between them for the stretches where several sources are
	 * past their knee at once.
	 */
	public static double bestBusVoltage(double[] vmp, double[] voc, double[] power, double minVolts, double maxVolts) {
		double[] candidates = new double[SCAN_STEPS + 1 + vmp.length];
		for (int step = 0; step <= SCAN_STEPS; step++) {
			candidates[step] = minVolts + (maxVolts - minVolts) * step / SCAN_STEPS;
		}

		for (int i = 0; i < vmp.length; i++) {
			candidates[SCAN_STEPS + 1 + i] = ownTrackerVolts(vmp[i], minVolts, maxVolts);
		}

		double best = minVolts;
		double bestTotal = -1.0;
		for (double volts : candidates) {
			double total = totalAt(volts, vmp, voc, power);
			if (total > bestTotal + 1.0e-12) {
				best = volts;
				bestTotal = total;
			}
		}

		return best;
	}
}
