package io.github.simonerecchia.renewables.block;

/**
 * The power box's Forge Energy arithmetic: what it can hand out, what a neighbour taking some costs, and what
 * it keeps.
 *
 * A box is mostly a converter and a little a battery.  The grid delivers it kilowatts each tick, and each kW is
 * {@link #FE_PER_KW} FE for that tick.  Neighbours that speak Forge Energy are offered that flow first; what
 * they take comes off the box's field of power the same tick, so a battery next to the box is fed before the
 * lamps around it are.  Whatever nobody used goes into a buffer, up to {@link #CAPACITY}, and the buffer is
 * what a neighbour draws from before it touches the flow - so a box on a line that goes quiet still has
 * something to give, and one on a line that carries more than the neighbours can take stores the rest.
 *
 * Pure functions on numbers: the block entity keeps the state, and this is what is tested.
 */
public final class FePool {
	/** The most the box holds, and the most it reports holding. */
	public static final int CAPACITY = 100_000;
	/** The most it offers one neighbour in one tick. */
	public static final int MAX_PUSH = 2_000;
	/** One kilowatt delivered for a tick is this many FE. */
	public static final int FE_PER_KW = 50;

	private FePool() {
	}

	/** The FE the current flow is worth this tick: whole FE, never negative. */
	public static int flow(double powerKw) {
		if (!(powerKw > 0.0)) return 0;

		double fe = Math.floor(powerKw * FE_PER_KW);
		return fe >= CAPACITY ? CAPACITY : (int) fe;
	}

	/** What the box can give right now: the buffer and this tick's flow, at most {@link #CAPACITY}. */
	public static int stored(int buffer, double powerKw) {
		return (int) Math.min((long) CAPACITY, (long) Math.max(0, buffer) + flow(powerKw));
	}

	/**
	 * What taking {@code requested} FE does.
	 *
	 * @param taken        what was given: the request, or what there was
	 * @param newBuffer    the buffer afterwards; it is spent before the flow is
	 * @param newPowerKw   the field's power afterwards: the flow that was taken is no longer there (never below 0)
	 */
	public record Extraction(int taken, int newBuffer, double newPowerKw) {
	}

	public static Extraction extract(int buffer, double powerKw, int requested) {
		int available = stored(buffer, powerKw);
		int taken = Math.max(0, Math.min(requested, available));
		int fromBuffer = Math.min(taken, Math.max(0, buffer));
		int fromFlow = taken - fromBuffer;
		double power = Math.max(0.0, powerKw - (double) fromFlow / FE_PER_KW);

		return new Extraction(taken, Math.max(0, buffer) - fromBuffer, power);
	}

	/** The buffer after storing what the field left unused, in kW for a tick. */
	public static int bank(int buffer, double unusedKw) {
		return (int) Math.min((long) CAPACITY, (long) Math.max(0, buffer) + flow(unusedKw));
	}
}
