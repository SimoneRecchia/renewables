package io.github.simonerecchia.renewables.client.model;

/**
 * The ray against box test the picker is built on (docs/spec/models.md section 11.2): the slab method, on a
 * half-line with no maximum length.
 */
final class Rays {
	/** A direction this small on an axis is taken as "does not move along it". */
	private static final double PARALLEL = 1.0e-12;

	private Rays() {
	}

	/**
	 * Where the half-line {@code origin + t * direction} enters the box, as the smallest t that is not negative, or
	 * NaN when it misses.  An origin inside the box enters at 0; a box wholly behind the origin is a miss; the two
	 * corners may be given in either order.
	 */
	static double entry(double ox, double oy, double oz, double dx, double dy, double dz,
			double ax, double ay, double az, double bx, double by, double bz) {
		double tMin = Double.NEGATIVE_INFINITY;
		double tMax = Double.POSITIVE_INFINITY;

		double[] o = {ox, oy, oz};
		double[] d = {dx, dy, dz};
		double[] a = {ax, ay, az};
		double[] b = {bx, by, bz};

		for (int axis = 0; axis < 3; axis++) {
			double low = Math.min(a[axis], b[axis]);
			double high = Math.max(a[axis], b[axis]);

			if (Math.abs(d[axis]) < PARALLEL) {
				// not moving along this axis: it has to be inside the slab from the start
				if (o[axis] < low || o[axis] > high) return Double.NaN;
				continue;
			}

			double t1 = (low - o[axis]) / d[axis];
			double t2 = (high - o[axis]) / d[axis];
			tMin = Math.max(tMin, Math.min(t1, t2));
			tMax = Math.min(tMax, Math.max(t1, t2));
		}

		if (tMax < tMin || tMax < 0.0) return Double.NaN;

		return Math.max(tMin, 0.0);
	}
}
