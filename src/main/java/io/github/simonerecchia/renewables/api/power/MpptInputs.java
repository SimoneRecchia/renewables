package io.github.simonerecchia.renewables.api.power;

/**
 * An inverter's direct-current inputs, tracker by tracker, as a plant is wired into them.
 *
 * A string is landed on one tracker's terminals and cannot be shared between two, so it has to fit under that
 * tracker's own current limit and find a free terminal there: two 17.3 A strings do not go on a 26 A input, however
 * much room the other eight have.  Each string goes to the tracker it fits most tightly, which keeps the roomier ones
 * for the strings that need them.  A combiner box's trunk is lugged onto busbars that feed the trackers together, so
 * it only has to fit under what they have left between them.
 */
public final class MpptInputs {
	/** Rounding slack on a current limit, in amps. */
	private static final double SLACK = 1.0e-6;

	private final int inputsPerTracker;
	private final double ampsPerTracker;
	private final int[] inputsUsed;
	private final double[] ampsUsed;

	public MpptInputs(InverterSpec spec) {
		this(spec.mpptCount(), spec.inputsPerMppt(), spec.maxCurrentPerMppt());
	}

	public MpptInputs(int trackers, int inputsPerTracker, double ampsPerTracker) {
		this.inputsPerTracker = inputsPerTracker;
		this.ampsPerTracker = ampsPerTracker;
		this.inputsUsed = new int[trackers];
		this.ampsUsed = new double[trackers];
	}

	/** The same inputs as they stand, to try a landing on without committing to it. */
	public MpptInputs copy() {
		MpptInputs copy = new MpptInputs(inputsUsed.length, inputsPerTracker, ampsPerTracker);
		System.arraycopy(inputsUsed, 0, copy.inputsUsed, 0, inputsUsed.length);
		System.arraycopy(ampsUsed, 0, copy.ampsUsed, 0, ampsUsed.length);
		return copy;
	}

	/** Lands one array's strings, all of them or none; answers whether they went in. */
	public boolean landStrings(int strings, double ampsPerString) {
		int[] chosen = new int[strings];
		for (int string = 0; string < strings; string++) {
			int tracker = tightestFit(ampsPerString);
			if (tracker < 0) {
				for (int landed = 0; landed < string; landed++) {
					inputsUsed[chosen[landed]]--;
					ampsUsed[chosen[landed]] -= ampsPerString;
				}

				return false;
			}

			chosen[string] = tracker;
			inputsUsed[tracker]++;
			ampsUsed[tracker] += ampsPerString;
		}

		return true;
	}

	/** Lands a combiner box's trunk on the busbars; answers whether the trackers between them can take it. */
	public boolean landTrunk(double amps) {
		if (amps > freeAmps() + SLACK) return false;

		double left = amps;
		for (int tracker = 0; tracker < ampsUsed.length && left > 0.0; tracker++) {
			double taken = Math.min(left, Math.max(0.0, ampsPerTracker - ampsUsed[tracker]));
			ampsUsed[tracker] += taken;
			left -= taken;
		}

		return true;
	}

	/** Current the trackers can still take between them, in amps. */
	public double freeAmps() {
		double free = 0.0;
		for (double used : ampsUsed) {
			free += Math.max(0.0, ampsPerTracker - used);
		}

		return free;
	}

	/** Current landed on one tracker, in amps. */
	public double ampsOn(int tracker) {
		return ampsUsed[tracker];
	}

	/** The tracker with a free terminal whose remaining room fits this string most closely, or -1. */
	private int tightestFit(double amps) {
		int best = -1;
		double bestRoom = Double.MAX_VALUE;
		for (int tracker = 0; tracker < ampsUsed.length; tracker++) {
			if (inputsUsed[tracker] >= inputsPerTracker) continue;

			double room = ampsPerTracker - ampsUsed[tracker];
			if (room + SLACK < amps || room >= bestRoom) continue;

			best = tracker;
			bestRoom = room;
		}

		return best;
	}
}
