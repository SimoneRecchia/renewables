package io.github.simonerecchia.renewables.client.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TubeBasisTest {
	private static final double[][] AXES = {
			{1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0},
			{0.6, 0.0, 0.8}, {0.5, -0.5, Math.sqrt(0.5)}, {0.1, 0.99, 0.0}, {0.0, 0.96, 0.28}, {-0.3, -0.2, 0.9327}};

	private static double[] unit(double[] v) {
		double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		return new double[] {v[0] / length, v[1] / length, v[2] / length};
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double[] sub(double[] a, double[] b) {
		return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	@Test
	void theFrameIsRightHandedAndOrthonormalForEveryDirection() {
		for (double[] raw : AXES) {
			double[] t = unit(raw);
			double[] out = new double[6];
			TubeBasis.frame(t[0], t[1], t[2], out);
			double[] u = {out[0], out[1], out[2]};
			double[] v = {out[3], out[4], out[5]};

			assertEquals(1.0, dot(u, u), 1.0e-9);
			assertEquals(1.0, dot(v, v), 1.0e-9);
			assertEquals(0.0, dot(u, t), 1.0e-9);
			assertEquals(0.0, dot(v, t), 1.0e-9);
			assertEquals(0.0, dot(u, v), 1.0e-9);

			double[] uv = cross(u, v);
			for (int i = 0; i < 3; i++) {
				assertEquals(t[i], uv[i], 1.0e-9, "u x v = T");
			}
		}
	}

	@Test
	void everySideFacesOutwardsSoNothingIsCulledAwayFromTheOutside() {
		for (int sides : new int[] {4, 6, 12}) {
			for (double[] raw : AXES) {
				double[] t = unit(raw);
				double[] frame = new double[6];
				TubeBasis.frame(t[0], t[1], t[2], frame);
				double[] u = {frame[0], frame[1], frame[2]};
				double[] v = {frame[3], frame[4], frame[5]};
				double length = 0.5;
				double radius = 0.05;

				for (int k = 0; k < sides; k++) {
					double[][] corner = new double[4][];
					for (int c = 0; c < 4; c++) {
						int ring = TubeBasis.CORNERS[c][0];
						int step = TubeBasis.CORNERS[c][1];
						double angle = 2.0 * Math.PI * ((k + step) % sides) / sides;
						corner[c] = new double[] {
								ring * length * t[0] + radius * (Math.cos(angle) * u[0] + Math.sin(angle) * v[0]),
								ring * length * t[1] + radius * (Math.cos(angle) * u[1] + Math.sin(angle) * v[1]),
								ring * length * t[2] + radius * (Math.cos(angle) * u[2] + Math.sin(angle) * v[2])};
					}

					// the normal a counter-clockwise quad has, and the direction from the axis out to the side's middle
					double[] normal = cross(sub(corner[1], corner[0]), sub(corner[2], corner[1]));
					double midAngle = 2.0 * Math.PI * (k + 0.5) / sides;
					double[] outward = {
							Math.cos(midAngle) * u[0] + Math.sin(midAngle) * v[0],
							Math.cos(midAngle) * u[1] + Math.sin(midAngle) * v[1],
							Math.cos(midAngle) * u[2] + Math.sin(midAngle) * v[2]};

					assertTrue(dot(normal, outward) > 0.0, "side " + k + " of " + sides + " faces in");
				}
			}
		}
	}

	@Test
	void theSidesTileTheWholeTextureWithoutFlipping() {
		assertEquals(4, TubeBasis.CORNERS.length);
		assertEquals(0.0f, TubeBasis.CORNER_U[0]);
		assertEquals(1.0f, TubeBasis.CORNER_U[1]);
		assertEquals(1.0f, TubeBasis.CORNER_U[2]);
		assertEquals(0.0f, TubeBasis.CORNER_U[3]);
		assertEquals(0.0f, TubeBasis.CORNER_V[0]);
		assertEquals(1.0f, TubeBasis.CORNER_V[2]);
	}
}
