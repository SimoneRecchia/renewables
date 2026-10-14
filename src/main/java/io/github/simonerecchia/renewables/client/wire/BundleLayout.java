package io.github.simonerecchia.renewables.client.wire;

/**
 * Where each wire of a bundle sits, in the plane across the span.
 *
 * {@code x} runs across the span, level with the ground, and {@code y} up.  The offsets are applied to the whole
 * profile, so the bundle follows the span and its sag.  Pure numbers.
 */
public final class BundleLayout {
	private BundleLayout() {
	}

	/**
	 * How many wires are drawn for a conductor that declares this many: one to four have a layout, anything else
	 * is a single wire down the middle.
	 */
	public static int wires(int subConductors) {
		return subConductors >= 2 && subConductors <= 4 ? subConductors : 1;
	}

	/** The across-the-span offset of wire {@code index} of a bundle of {@code count}. */
	public static double x(int count, int index, double spacing) {
		double h = spacing / 2.0;
		return switch (wires(count)) {
			case 2 -> index == 0 ? -h : h;
			case 3 -> index == 0 ? -h : index == 1 ? h : 0.0;
			case 4 -> index == 0 || index == 3 ? -h : h;
			default -> 0.0;
		};
	}

	/** The upward offset of wire {@code index}. */
	public static double y(int count, int index, double spacing) {
		double h = spacing / 2.0;
		return switch (wires(count)) {
			case 2 -> 0.0;
			// a triangle: two below and one above, the apex a little over the base's half-width
			case 3 -> index == 2 ? 1.15 * h : -0.58 * h;
			case 4 -> index <= 1 ? -h : h;
			default -> 0.0;
		};
	}

	/**
	 * The horizontal direction across a span running {@code (dx, dz)}, as a unit vector written into
	 * {@code out[0]} and {@code out[1]} (x and z).  A span with no horizontal run has no across: east.
	 */
	public static void across(double dx, double dz, double[] out) {
		double run = Math.sqrt(dx * dx + dz * dz);
		if (run < 1.0e-6) {
			out[0] = 1.0;
			out[1] = 0.0;
			return;
		}

		out[0] = dz / run;
		out[1] = -dx / run;
	}
}
