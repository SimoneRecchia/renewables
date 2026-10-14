package io.github.simonerecchia.renewables.client.wire;

/**
 * The shape of one hanging span, as plain numbers: the centre line of the cable from its start fitting to its end
 * fitting, as points relative to the start.
 *
 * Kept free of every Minecraft type so the maths can be tested without a game, and so one instance can be reused
 * frame after frame (the buffer only ever grows).
 *
 * <h2>The sag</h2>
 * A conductor's {@code sag} is the dip as a <em>fraction of the span's length</em>: the cable hangs {@code sag * length}
 * below the middle of the chord joining its two ends.  Taken as a coefficient of the squared span, as the catalogue's
 * numbers once were, an AT span would hang six hundred blocks and end up under the ground; as a fraction it is 3.5
 * blocks for 160, and the 2.5 to 1 ratio between a street bundle and a transmission line is kept.
 *
 * The curve is the parabola through both ends with that dip, parametrised by {@code t} along the chord:
 * {@code (dx t, dy t - 4 drop t (1 - t), dz t)}.  A span with no sag (a conductor that declares none), or one that is
 * almost vertical, is a single straight tube; a vertical span is therefore drawn from its start to its end and not
 * mirrored about the start.
 */
public final class WireProfile {
	/** A span whose ends are closer than this is not drawn at all. */
	public static final double MIN_LENGTH = 0.1;
	/** Below this horizontal distance there is no sideways run for a cable to hang over: it is a straight tube. */
	public static final double MIN_HORIZONTAL = 0.1;
	/** Spacing of the points along the chord, in blocks, at full detail. */
	public static final double BASE_STEP = 0.25;
	/** A hanging cable is at least this many segments, or it would not look like it hangs. */
	public static final int MIN_SEGMENTS = 2;
	/** A span beyond a few thousand points is nonsense data: it is cut short rather than allocated. */
	public static final int MAX_SEGMENTS = 4096;
	/** Below this drop (blocks) a cable is straight for every purpose that can be seen. */
	public static final double NEGLIGIBLE_DROP = 1.0e-3;

	private float[] points = new float[0];
	private int segments;
	private boolean straight;
	private double drop;
	private double chord;
	private double lowest;

	/** How far the cable hangs below the middle of its chord: the conductor's sag times the span's length. */
	public static double drop(double sag, double chordLength) {
		if (!(sag > 0.0) || !(chordLength > 0.0)) return 0.0;

		return sag * chordLength;
	}

	/** The height of the cable above its start at fraction {@code t} of the way along, for a rise of {@code dy}. */
	public static double heightAt(double t, double dy, double drop) {
		return dy * t - 4.0 * drop * t * (1.0 - t);
	}

	/** The lowest height the cable reaches relative to its start, anywhere along it: what a bounding box needs. */
	public static double lowestHeight(double dy, double drop) {
		double lowest = Math.min(0.0, dy);
		if (drop <= 0.0) return lowest;

		// y(t) = 4 drop t^2 + (dy - 4 drop) t is a parabola opening upwards, so it has a minimum at this t
		double t = (4.0 * drop - dy) / (8.0 * drop);
		if (t > 0.0 && t < 1.0) lowest = Math.min(lowest, heightAt(t, dy, drop));

		return lowest;
	}

	/**
	 * Fills the profile for a span from the start to a point {@code (dx, dy, dz)} away, of a conductor with this sag.
	 *
	 * @return false when there is nothing to draw: ends too close together, or a coordinate that is not a number
	 */
	public boolean rebuild(double dx, double dy, double dz, double sag) {
		segments = 0;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (!Double.isFinite(length) || length < MIN_LENGTH) return false;

		double horizontal = Math.sqrt(dx * dx + dz * dz);
		this.chord = length;
		this.drop = drop(sag, length);
		this.straight = horizontal <= MIN_HORIZONTAL || this.drop < NEGLIGIBLE_DROP;
		this.lowest = straight ? Math.min(0.0, dy) : lowestHeight(dy, drop);

		int count = straight ? 1 : (int) Math.min(MAX_SEGMENTS, Math.max(MIN_SEGMENTS, Math.ceil(length / BASE_STEP)));
		if (points.length < 3 * (count + 1)) points = new float[3 * (count + 1)];

		for (int i = 0; i <= count; i++) {
			if (i == count) {
				// exactly the far end: not the end as the repeated division happens to round it
				points[3 * i] = (float) dx;
				points[3 * i + 1] = (float) dy;
				points[3 * i + 2] = (float) dz;
				continue;
			}

			double t = (double) i / count;
			points[3 * i] = (float) (dx * t);
			points[3 * i + 1] = (float) (straight ? dy * t : heightAt(t, dy, drop));
			points[3 * i + 2] = (float) (dz * t);
		}

		this.segments = count;
		return true;
	}

	/** How many segments the full-detail polyline has; 0 when nothing is to be drawn. The points are 0 to this. */
	public int segments() {
		return segments;
	}

	/** Whether this span is a single straight tube (no sag, or too steep to hang). */
	public boolean straight() {
		return straight;
	}

	/** The length of the chord, ends included. */
	public double chord() {
		return chord;
	}

	/** How far below the middle of the chord the cable hangs. */
	public double drop() {
		return drop;
	}

	/** The lowest height reached relative to the start (never above 0 or the end's rise). */
	public double lowest() {
		return lowest;
	}

	public float x(int i) {
		return points[3 * i];
	}

	public float y(int i) {
		return points[3 * i + 1];
	}

	public float z(int i) {
		return points[3 * i + 2];
	}
}
