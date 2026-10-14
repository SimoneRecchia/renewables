package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.Renewables;

import net.minecraft.server.level.ServerLevel;

/**
 * The grid's error log for one level: at most one entry a minute, with a count of what was held back since.  A
 * faulty machine fails every tick, and the log must neither drown in it nor go quiet for good after a few lines.
 */
final class FaultLog {
	/** One entry a minute of game time. */
	static final long INTERVAL_TICKS = 1200;

	private long lastLogged = Long.MIN_VALUE;
	private int held;

	/** Logs a failure, unless one was logged less than a minute ago; then it is only counted. */
	void report(ServerLevel level, String what, Throwable error) {
		if (!admit(level.getGameTime())) return;

		int before = take();
		Renewables.LOGGER.error("The grid of {}: {}{}", level.dimension().location(), what,
				before > 0 ? " (and " + before + " more since the last report)" : "", error);
	}

	/** Whether a failure at {@code now} is logged; one that is not is counted for the next report. */
	boolean admit(long now) {
		if (lastLogged != Long.MIN_VALUE && now >= lastLogged && now - lastLogged < INTERVAL_TICKS) {
			held++;
			return false;
		}

		lastLogged = now;
		return true;
	}

	/** How many failures were held back since the last report, and starts counting again. */
	int take() {
		int count = held;
		held = 0;
		return count;
	}
}
