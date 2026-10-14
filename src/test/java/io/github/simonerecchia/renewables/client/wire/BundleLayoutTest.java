package io.github.simonerecchia.renewables.client.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BundleLayoutTest {
	private static final double SPACING = 0.075;
	private static final double H = SPACING / 2.0;

	@Test
	void oneWireSitsOnTheCentreLine() {
		assertEquals(1, BundleLayout.wires(1));
		assertEquals(0.0, BundleLayout.x(1, 0, SPACING));
		assertEquals(0.0, BundleLayout.y(1, 0, SPACING));
	}

	@Test
	void twoWiresSideBySide() {
		assertEquals(2, BundleLayout.wires(2));
		assertEquals(-H, BundleLayout.x(2, 0, SPACING));
		assertEquals(H, BundleLayout.x(2, 1, SPACING));
		assertEquals(0.0, BundleLayout.y(2, 0, SPACING));
		assertEquals(0.0, BundleLayout.y(2, 1, SPACING));
	}

	@Test
	void threeWiresATriangleWithTheApexUp() {
		assertEquals(-H, BundleLayout.x(3, 0, SPACING));
		assertEquals(H, BundleLayout.x(3, 1, SPACING));
		assertEquals(0.0, BundleLayout.x(3, 2, SPACING));
		assertEquals(-0.58 * H, BundleLayout.y(3, 0, SPACING), 1.0e-12);
		assertEquals(-0.58 * H, BundleLayout.y(3, 1, SPACING), 1.0e-12);
		assertEquals(1.15 * H, BundleLayout.y(3, 2, SPACING), 1.0e-12);
	}

	@Test
	void fourWiresTheCornersOfASquareAllDistinct() {
		double[][] expected = {{-H, -H}, {H, -H}, {H, H}, {-H, H}};
		for (int i = 0; i < 4; i++) {
			assertEquals(expected[i][0], BundleLayout.x(4, i, SPACING), 1.0e-12);
			assertEquals(expected[i][1], BundleLayout.y(4, i, SPACING), 1.0e-12);
		}
	}

	@Test
	void anyOtherCountIsOneWireInTheMiddle() {
		assertEquals(1, BundleLayout.wires(0));
		assertEquals(1, BundleLayout.wires(5));
		assertEquals(1, BundleLayout.wires(12));
		assertEquals(0.0, BundleLayout.x(7, 0, SPACING));
	}

	@Test
	void acrossIsHorizontalAndPerpendicularToTheRun() {
		double[] out = new double[2];

		BundleLayout.across(0.0, 10.0, out);
		assertEquals(1.0, out[0], 1.0e-12);
		assertEquals(0.0, out[1], 1.0e-12);

		BundleLayout.across(3.0, 4.0, out);
		assertEquals(0.0, out[0] * 3.0 + out[1] * 4.0, 1.0e-12);
		assertEquals(1.0, Math.hypot(out[0], out[1]), 1.0e-12);

		BundleLayout.across(0.0, 0.0, out);
		assertEquals(1.0, out[0]);
		assertEquals(0.0, out[1]);
	}
}
