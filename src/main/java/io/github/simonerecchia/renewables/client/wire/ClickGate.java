package io.github.simonerecchia.renewables.client.wire;

/**
 * Lets one physical click through once.
 *
 * A click in the air is reported once per hand, so with a reel in each hand (or a reel and an item that does not take
 * the click) the same click would reach the link machine twice and the second pass would cancel what the first began.
 * The gate admits one click per game tick, which is as fast as a player can click.
 */
public final class ClickGate {
	private static final long NEVER = Long.MIN_VALUE;

	private long last = NEVER;

	/** Whether this tick's click is the first: true once per tick. */
	public boolean admit(long gameTime) {
		if (gameTime == last) return false;

		last = gameTime;
		return true;
	}

	public void reset() {
		last = NEVER;
	}
}
