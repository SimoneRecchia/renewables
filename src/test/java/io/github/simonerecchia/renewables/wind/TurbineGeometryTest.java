package io.github.simonerecchia.renewables.wind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.catalog.TurbineCatalog;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The boxes the blocks collide with, against the figures docs/turbine-model.md section 7 prints, and the height a pile stands to. */
class TurbineGeometryTest {
	/** A box in sixteenths, as Block.box takes it. */
	private static void assertPixels(double[] box, double x0, double y0, double z0, double x1, double y1, double z1, String what) {
		double[] expected = {x0, y0, z0, x1, y1, z1};
		for (int i = 0; i < 6; i++) assertEquals(expected[i], box[i] * 16.0, 0.015, what + " [" + i + "]");
	}

	@Test
	void nacelleOfEveryModelIsTheTabulatedBox() {
		List<double[]> sw = TurbineGeometry.nacelleBoxes(TurbineCatalog.SW_10);
		assertPixels(sw.get(0), 6.89, 0.00, 4.45, 9.12, 2.40, 10.64, "sw_10 hull");
		assertPixels(sw.get(1), 7.22, 2.32, 4.70, 8.78, 3.14, 6.58, "sw_10 cooler");

		List<double[]> c52 = TurbineGeometry.nacelleBoxes(TurbineCatalog.C52_085);
		assertPixels(c52.get(0), 6.22, 0.00, 2.32, 9.79, 3.84, 12.22, "c52 hull");
		assertPixels(c52.get(1), 6.75, 3.71, 2.71, 9.25, 5.03, 5.73, "c52 cooler");

		List<double[]> c80 = TurbineGeometry.nacelleBoxes(TurbineCatalog.C80_20);
		assertPixels(c80.get(0), 5.26, 0.00, 0.00, 10.76, 5.91, 14.50, "c80 hull");
		assertPixels(c80.get(1), 6.07, 5.71, 0.00, 9.93, 7.74, 4.51, "c80 cooler");

		List<double[]> c90 = TurbineGeometry.nacelleBoxes(TurbineCatalog.C90_30);
		assertPixels(c90.get(0), 4.92, 0.00, 0.00, 11.10, 6.65, 15.31, "c90 hull");
		assertPixels(c90.get(1), 5.83, 6.42, 0.00, 10.17, 8.71, 4.08, "c90 cooler");

		List<double[]> c112 = TurbineGeometry.nacelleBoxes(TurbineCatalog.C112_30);
		assertPixels(c112.get(0), 4.17, 0.00, 0.00, 11.86, 8.27, 16.00, "c112 hull");
		assertPixels(c112.get(1), 5.30, 8.00, 0.00, 10.70, 10.83, 3.12, "c112 cooler");

		List<double[]> c130 = TurbineGeometry.nacelleBoxes(TurbineCatalog.C130_40);
		assertPixels(c130.get(0), 3.55, 0.00, 0.00, 12.48, 9.60, 16.00, "c130 hull");
		assertPixels(c130.get(1), 4.86, 9.28, 0.00, 11.14, 12.58, 2.34, "c130 cooler");
	}

	@Test
	void nothingIsOutsideTheBlock() {
		for (TurbineSpec spec : TurbineCatalog.all()) {
			for (double[] box : TurbineGeometry.nacelleBoxes(spec)) {
				for (double v : box) assertTrue(v >= 0.0 && v <= 1.0, spec.displayName());
			}
		}
	}

	@Test
	void towerRadiusFollowsTheConeOverThePile() {
		// C130 on thirteen: 0.334 at the foot block, losing 0.0115 a block, 0.196 at the top block
		assertEquals(0.334, TurbineGeometry.towerRadius(0, 13, 1.0), 0.0005);
		assertEquals(0.323, TurbineGeometry.towerRadius(1, 13, 1.0), 0.0005);
		assertEquals(0.311, TurbineGeometry.towerRadius(2, 13, 1.0), 0.0006);
		assertEquals(0.196, TurbineGeometry.towerRadius(12, 13, 1.0), 0.0005);
		// SW-10 on two: a tube 2.4 px across at the foot block
		double small = TurbineCatalog.SW_10.nacelleRenderScale();
		assertEquals(0.076, TurbineGeometry.towerRadius(0, 2, small), 0.0005);
		assertEquals(0.057, TurbineGeometry.towerRadius(1, 2, small), 0.0005);
	}

	@Test
	void towerStaircaseFillsTheCircleAndStaysInsideIt() {
		for (double radius : new double[] {0.057, 0.2, 0.334}) {
			List<double[]> boxes = TurbineGeometry.towerBoxes(radius);
			assertEquals(8, boxes.size());
			for (double[] b : boxes) {
				double halfX = b[3] - 0.5;
				double halfZ = b[5] - 0.5;
				// the corner is on the circle, never past it
				assertTrue(Math.hypot(halfX, halfZ) <= radius + 1e-9);
				assertEquals(0.0, b[1], 0.0);
				assertEquals(1.0, b[4], 0.0);
				assertEquals(0.5 - halfX, b[0], 1e-12);
			}

			// every point of the circle of 0.9 the radius is inside one of the rectangles
			for (double a = 0.0; a < 360.0; a += 1.0) {
				double x = 0.9 * radius * Math.cos(Math.toRadians(a));
				double z = 0.9 * radius * Math.sin(Math.toRadians(a));
				boolean inside = false;
				for (double[] b : boxes) inside |= Math.abs(x) <= b[3] - 0.5 + 1e-9 && Math.abs(z) <= b[5] - 0.5 + 1e-9;
				assertTrue(inside, "radius " + radius + " at " + a + " degrees");
			}
		}
	}

	@Test
	void bareTowerIsAQuarterAtTwoBlocksAndFullFromThirteen() {
		assertEquals(0.25, TurbineGeometry.bareTowerScale(2), 1e-9);
		assertEquals(0.25, TurbineGeometry.bareTowerScale(1), 1e-9);
		assertEquals(1.0, TurbineGeometry.bareTowerScale(13), 1e-9);
		assertEquals(1.0, TurbineGeometry.bareTowerScale(20), 1e-9);
		assertEquals(0.25 + 3.0 / 11.0 * 0.75, TurbineGeometry.bareTowerScale(5), 1e-9);
	}

	@Test
	void insulatorBoxIsTheTabulatedOne() {
		assertPixels(TurbineGeometry.INSULATOR_BOX, 12.40, 0.00, 12.40, 15.60, 6.25, 15.60, "insulator");
	}

	@Test
	void towerFoldsPastTwentyWhenSetAndPastSixteenWhenWalkedOn() {
		assertFalse(TurbineGeometry.collapsesWhenPlaced(20));
		assertTrue(TurbineGeometry.collapsesWhenPlaced(21));
		assertFalse(TurbineGeometry.collapsesWhenWalkedOn(16));
		assertTrue(TurbineGeometry.collapsesWhenWalkedOn(17));
		// a pile of seventeen to twenty stands until somebody steps on it
		assertFalse(TurbineGeometry.collapsesWhenPlaced(17));
		assertTrue(TurbineGeometry.collapsesWhenWalkedOn(20));
	}

	@Test
	void noModelAcceptsMoreThanTheSafeHeight() {
		TurbineCatalog.all().forEach(spec -> assertTrue(spec.maxTowerSegments() <= TurbineGeometry.SAFE_SEGMENTS));
	}

	@Test
	void scanReachesPastTheTallestPileThatCanBeBuilt() {
		assertTrue(TurbineGeometry.SCAN_LIMIT > TurbineGeometry.MAX_SEGMENTS);
	}
}
