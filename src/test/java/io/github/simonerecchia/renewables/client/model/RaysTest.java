package io.github.simonerecchia.renewables.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The slab test, case by case from docs/spec/models.md section 11.2. */
class RaysTest {
	/** The unit box from (0,0,0) to (1,1,1), as a ray from (ox,oy,oz) in direction (dx,dy,dz). */
	private static double hit(double ox, double oy, double oz, double dx, double dy, double dz) {
		return Rays.entry(ox, oy, oz, dx, dy, dz, 0, 0, 0, 1, 1, 1);
	}

	@Test
	void aRayThroughTheBoxEntersAtTheNearFace() {
		assertEquals(2.0, hit(0.5, 0.5, -2.0, 0, 0, 1), 1e-12);
		assertEquals(1.0, hit(0.5, 0.5, -2.0, 0, 0, 2), 1e-12, "the direction need not be a unit vector");
	}

	@Test
	void aRayThatPassesBesideMisses() {
		assertTrue(Double.isNaN(hit(1.5, 0.5, -2.0, 0, 0, 1)));
		assertTrue(Double.isNaN(hit(0.5, 1.0001, -2.0, 0, 0, 1)));
	}

	@Test
	void anOriginInsideHitsAtZero() {
		assertEquals(0.0, hit(0.5, 0.5, 0.5, 1, 0, 0));
		assertEquals(0.0, hit(0.5, 0.5, 0.5, 0.3, -0.2, 0.9));
	}

	@Test
	void aBoxBehindTheOriginIsAMiss() {
		assertTrue(Double.isNaN(hit(0.5, 0.5, 3.0, 0, 0, 1)));
		assertEquals(2.0, hit(0.5, 0.5, 3.0, 0, 0, -1), 1e-12);
	}

	@Test
	void aRayParallelToAnAxisMustStartInsideThatSlab() {
		assertEquals(1.0, hit(-1.0, 0.5, 0.5, 1, 0, 0), 1e-12);
		assertTrue(Double.isNaN(hit(-1.0, 1.5, 0.5, 1, 0, 0)));
		// the faces count: on the boundary is inside
		assertEquals(1.0, hit(-1.0, 1.0, 0.0, 1, 0, 0), 1e-12);
	}

	@Test
	void aDiagonalRayIntoACorner() {
		assertEquals(1.0, hit(-1, -1, -1, 1, 1, 1), 1e-12);
	}

	@Test
	void aRayThatGrazesAnEdgeThroughTheCornerHits() {
		assertEquals(1.0, hit(-1, 1, 0.5, 1, 0, 0), 1e-12);
	}

	@Test
	void theCornersMayBeGivenInEitherOrder() {
		double a = Rays.entry(0.5, 0.5, -2, 0, 0, 1, 0, 0, 0, 1, 1, 1);
		double b = Rays.entry(0.5, 0.5, -2, 0, 0, 1, 1, 1, 1, 0, 0, 0);
		assertEquals(a, b);
		assertEquals(2.0, b, 1e-12);
	}

	@Test
	void aBoxWithNoSizeIsHitOnlyOnAnExactLine() {
		assertEquals(2.0, Rays.entry(0, 0, -2, 0, 0, 1, 0, 0, 0, 0, 0, 0), 1e-12);
		assertTrue(Double.isNaN(Rays.entry(0.001, 0, -2, 0, 0, 1, 0, 0, 0, 0, 0, 0)));
	}
}
