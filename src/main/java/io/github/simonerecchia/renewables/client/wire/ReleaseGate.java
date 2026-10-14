package io.github.simonerecchia.renewables.client.wire;

/**
 * Lets a press of the use button through once, however long it is held.
 *
 * Holding the button down repeats the use action every few ticks, which is right for placing blocks and wrong for a
 * two-click connection: the second, repeated "click" lands on the fitting that was just picked and cancels it.  A
 * press is admitted only if the button has been seen up since the last one that was.  The same gate also swallows the
 * second, off-hand report of a single press in the air.
 */
public final class ReleaseGate {
	private boolean released = true;

	/** Called every tick with whether the button is down. */
	public void observe(boolean down) {
		if (!down) released = true;
	}

	/** Whether this press is a new one; if so it is now used up until the button is let go. */
	public boolean admit() {
		if (!released) return false;

		released = false;
		return true;
	}

	/** Back to the start: a new world. */
	public void reset() {
		released = true;
	}
}
