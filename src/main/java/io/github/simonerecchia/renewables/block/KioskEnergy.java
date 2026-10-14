package io.github.simonerecchia.renewables.block;

/**
 * The power box's energy over a tick: the buffer, and what is left of the flow the grid delivered for this tick.
 *
 * The flow is one tick's energy and it is spent once - by a neighbour that takes Forge Energy, by the field, or into
 * the buffer - and whatever spends it takes it out of the flow.  The box used to bank its unused flow and leave the
 * same kilowatts standing as the flow until the grid's next delivery, so a cable pulling after the box's tick found
 * buffer and flow and took both: twice what had come in.  The figure the wrench reads is the delivery, kept by the
 * block entity apart from this.
 *
 * Plain numbers ({@link FePool} does the arithmetic), so the conservation can be tested without a world.
 */
public final class KioskEnergy {
	private int buffer;
	private double flowKw;
	/** FE handed to neighbours and cables since the last {@link #takeOut}. */
	private long out;

	/** The grid's delivery for the coming tick.  It replaces what was left: a flow not spent in its tick is not stored. */
	public void deliver(double kw) {
		flowKw = kw > 0.0 ? kw : 0.0;
	}

	/** What a neighbour can take now: the buffer and the flow left, at most the capacity. */
	public int stored() {
		return FePool.stored(buffer, flowKw);
	}

	public int buffer() {
		return buffer;
	}

	/** What is left of this tick's flow, kW. */
	public double flowKw() {
		return flowKw;
	}

	/** Restores a saved buffer, clamped to what a box can hold. */
	public void setBuffer(int fe) {
		buffer = Math.max(0, Math.min(FePool.CAPACITY, fe));
	}

	/** Takes FE out for a neighbour or a cable, the buffer first; nothing changes when only simulating. */
	public int extract(int requested, boolean simulate) {
		FePool.Extraction taken = FePool.extract(buffer, flowKw, requested);
		if (!simulate && taken.taken() > 0) {
			buffer = taken.newBuffer();
			flowKw = taken.newPowerKw();
			out += taken.taken();
		}

		return taken.taken();
	}

	/** The field used {@code kw} of the flow, at most what is left of it. */
	public void spend(double kw) {
		flowKw -= Math.max(0.0, Math.min(kw, flowKw));
	}

	/**
	 * Banks what is left of the flow and leaves none standing, so nobody can take it again before the next delivery.
	 *
	 * @return whether the buffer moved
	 */
	public boolean bankRest() {
		int banked = FePool.bank(buffer, flowKw);
		flowKw = 0.0;
		if (banked == buffer) return false;

		buffer = banked;
		return true;
	}

	/** The FE handed out since this was last asked, which is then forgotten. */
	public long takeOut() {
		long taken = out;
		out = 0;
		return taken;
	}
}
