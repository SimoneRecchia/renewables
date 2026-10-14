package io.github.simonerecchia.renewables.client.wire;

/**
 * The two decisions a tube's mesh depends on that cannot be seen to be right in a render: which way the ring's own
 * axes point, and in which order the corners of a side are given.  Pure maths, so a test can check that a side faces
 * outwards (a tube whose sides face in is culled away and the cable is invisible).
 *
 * <b>The frame.</b> For a ring across the direction {@code T}, {@link #frame} gives {@code u} and {@code v}, unit
 * vectors perpendicular to {@code T} and to each other, with {@code u x v = T}: the angle {@code k/n} of a turn then
 * runs counter-clockwise seen from the tip of {@code T}, and the point at that angle is {@code cos u + sin v}.
 *
 * <b>The corners.</b> A side joins the ring at one end of a segment (A) to the ring at the other (B), between the
 * angles {@code k} and {@code k+1}.  Listed as {@link #CORNERS} - A at k, A at k+1, B at k+1, B at k - the side is
 * counter-clockwise seen from outside the tube, which is what a front face is.
 */
public final class TubeBasis {
	/** Which ring (0 = start of the segment, 1 = end) and which step (0 = k, 1 = k + 1) each corner of a side is at. */
	public static final int[][] CORNERS = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
	/** Texture coordinates of the same corners: the whole width round the perimeter, the whole height along. */
	public static final float[] CORNER_U = {0.0f, 1.0f, 1.0f, 0.0f};
	public static final float[] CORNER_V = {0.0f, 0.0f, 1.0f, 1.0f};

	private TubeBasis() {
	}

	/**
	 * Writes {@code u} to {@code out[0..2]} and {@code v} to {@code out[3..5]} for a unit axis {@code (tx, ty, tz)}.
	 *
	 * The reference used to span the plane is up, unless the axis is within about 18 degrees of vertical, where it
	 * is east; so a cable that turns from level to vertical does not flip its frame on the way.
	 */
	public static void frame(double tx, double ty, double tz, double[] out) {
		double rx = 0.0;
		double ry = 1.0;
		double rz = 0.0;
		if (Math.abs(ty) > 0.95) {
			rx = 1.0;
			ry = 0.0;
		}

		double ux = ty * rz - tz * ry;
		double uy = tz * rx - tx * rz;
		double uz = tx * ry - ty * rx;
		double length = Math.sqrt(ux * ux + uy * uy + uz * uz);
		ux /= length;
		uy /= length;
		uz /= length;

		out[0] = ux;
		out[1] = uy;
		out[2] = uz;
		out[3] = ty * uz - tz * uy;
		out[4] = tz * ux - tx * uz;
		out[5] = tx * uy - ty * ux;
	}
}
