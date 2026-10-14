package io.github.simonerecchia.renewables.client.render;

/**
 * How long the last frame took, for animations that must run at the same speed on a slow machine and a fast one
 * (docs/spec/models.md section 14.5).
 *
 * Every constant a renderer was tuned with is "degrees per frame", which is only right at one frame rate.  They are
 * read here as degrees per frame at 60 frames a second, so a step is scaled by {@link #perSixty()}: the same
 * picture at 60 fps, the same speed at 30 or 240.
 *
 * Several passes draw in the same frame and each asks, so the clock is told which frame it is in and only moves on
 * when that changes.
 */
public final class FrameClock {
	/** A frame longer than this (a stall, a breakpoint, a window dragged about) counts as this long: no leaps. */
	static final double MAX_SECONDS = 0.25;
	private static final double REFERENCE_FPS = 60.0;

	private static long lastNanos;
	private static double lastFrame = Double.NaN;
	private static float seconds;

	private FrameClock() {
	}

	/** Called by every pass at the start of the frame; only the first call of a frame does anything. */
	static void begin(int renderTick, float partialTick, boolean paused) {
		advance(System.nanoTime(), renderTick + (double) partialTick, paused);
	}

	/** The clock proper, with the time passed in so it can be tested. */
	static void advance(long nowNanos, double frame, boolean paused) {
		if (paused) {
			// a paused game still draws and nothing on it should move; keep the stamp current so the first frame
			// after the pause is a normal frame and not a leap over all the time spent paused
			seconds = 0.0f;
			lastNanos = nowNanos;
			lastFrame = frame;
			return;
		}

		if (frame != lastFrame) {
			seconds = lastNanos == 0L ? 0.0f : (float) Math.max(0.0, Math.min(MAX_SECONDS, (nowNanos - lastNanos) * 1.0e-9));
			lastNanos = nowNanos;
			lastFrame = frame;
		}
	}

	/** Seconds since the previous frame; 0 on the first frame and while the game is paused. */
	public static float seconds() {
		return seconds;
	}

	/** The frame as a multiple of one 60 fps frame: what a "per frame" constant is multiplied by. */
	public static float perSixty() {
		return (float) (seconds * REFERENCE_FPS);
	}

	/** Forgets everything, so the first frame of the next world does not see the time since the last one. */
	static void reset() {
		lastNanos = 0L;
		lastFrame = Double.NaN;
		seconds = 0.0f;
	}
}
