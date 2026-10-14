package io.github.simonerecchia.renewables.client.wire;

/**
 * How much detail a cable gets at a given distance from the camera.
 *
 * A 160-block quad bundle at full detail is some thirty thousand quads, rebuilt every frame; nobody can see that
 * detail past a few dozen blocks.  Three independent knobs, all pure functions of distance:
 * <ul>
 * <li>the <b>stride</b> through the full-detail points: 1 (every 0.25 blocks) near, then every 2nd, 4th and 8th;</li>
 * <li>the <b>sides</b> of the tube: 12 near, 6, then 4;</li>
 * <li>whether a <b>bundle</b> is still worth drawing as separate wires, or as one thicker one.</li>
 * </ul>
 * No Minecraft types: the thresholds are tested directly.
 */
public final class WireLod {
	/** Every point of the profile up to here, then every second, fourth and eighth. */
	public static final double STRIDE_2_FROM = 32.0;
	public static final double STRIDE_4_FROM = 64.0;
	public static final double STRIDE_8_FROM = 128.0;

	/** Twelve sides up to here, six up to {@link #SIDES_4_FROM}, then four. */
	public static final double SIDES_6_FROM = 24.0;
	public static final double SIDES_4_FROM = 96.0;

	/** Past this a bundle is drawn as one wire. */
	public static final double MERGE_BUNDLE_FROM = 64.0;

	private WireLod() {
	}

	/** How many profile points to step over at this distance: 1, 2, 4 or 8 (spacing 0.25, 0.5, 1 and 2 blocks). */
	public static int stride(double distance) {
		if (distance < STRIDE_2_FROM) return 1;
		if (distance < STRIDE_4_FROM) return 2;
		if (distance < STRIDE_8_FROM) return 4;

		return 8;
	}

	/** How many sides the tube has at this distance. */
	public static int sides(double distance) {
		if (distance < SIDES_6_FROM) return 12;
		if (distance < SIDES_4_FROM) return 6;

		return 4;
	}

	/** Whether a bundle of {@code count} wires should be drawn as a single one at this distance. */
	public static boolean mergeBundle(double distance, int count) {
		return count > 1 && distance >= MERGE_BUNDLE_FROM;
	}

	/**
	 * The radius of the one wire that stands in for a bundle: wide enough to cover it, so it does not thin out
	 * as it gets further away.
	 */
	public static double mergedRadius(double radius, double spacing, int count) {
		if (count <= 1) return radius;

		return radius + 0.5 * spacing;
	}

	/**
	 * The next point to draw after {@code from}, in a profile whose points are {@code 0..last}.
	 *
	 * Steps by the wanted stride but only to a multiple of it, so the points the cable is drawn through are on a
	 * regular grid and a neighbouring span's tessellation does not shimmer as the camera moves; it never steps
	 * past {@code last}, and always moves forward.
	 */
	public static int next(int from, int last, int wantedStride) {
		int step = Math.max(1, wantedStride);
		while (step > 1 && (from % step != 0 || from + step > last)) {
			step >>= 1;
		}

		return Math.min(last, from + step);
	}

	/** Mixes two packed light values (block in bits 4-7, sky in bits 20-23) by {@code t}, per component. */
	public static int mixLight(int a, int b, float t) {
		int block = Math.round(lerp((a >> 4) & 15, (b >> 4) & 15, t));
		int sky = Math.round(lerp((a >> 20) & 15, (b >> 20) & 15, t));

		return (block << 4) | (sky << 20);
	}

	private static float lerp(int from, int to, float t) {
		return from + (to - from) * t;
	}
}
