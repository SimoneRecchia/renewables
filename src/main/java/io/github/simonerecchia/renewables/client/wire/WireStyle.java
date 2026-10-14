package io.github.simonerecchia.renewables.client.wire;

/**
 * What a cable looks like, apart from where it runs: a colour, a thickness, how much it sags and how many wires
 * make the bundle.  Pure, so the geometry and the tests need no conductor catalogue.
 *
 * @param colour  0xRRGGBB
 * @param radius  blocks
 * @param sag     the dip as a fraction of the span's length ({@link WireProfile})
 * @param count   wires in the bundle
 * @param spacing blocks between the wires of a bundle
 */
public record WireStyle(int colour, double radius, double sag, int count, double spacing) {
	/** A span whose conductor is not known - an old world's, or one this build no longer has: a thin black wire. */
	public static final WireStyle GENERIC = new WireStyle(0x000000, 0.025, 0.005, 1, 0.0);

	/** The same wire, in another colour. */
	public WireStyle tinted(int newColour) {
		return new WireStyle(newColour, radius, sag, count, spacing);
	}
}
