package io.github.simonerecchia.renewables.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Row-on-row shading is across the rows: a sun beside them or behind them casts nothing onto the next. */
class RowShadingTest {
	private static final double GCR = 0.45;
	private static final double TILT = 25.0;
	private static final SunPosition MORNING = new SunPosition(15.0, SunPosition.EAST);

	/** A rack facing the morning sun loses a tenth to the row in front: cos 25 + sin 25 / tan 15, times the GCR. */
	@Test
	void aRowFacingTheSunIsShadedByTheOneInFront() {
		double reach = Math.cos(Math.toRadians(TILT)) + Math.sin(Math.toRadians(TILT)) / Math.tan(Math.toRadians(15.0));
		assertEquals(1.0 - 1.0 / (GCR * reach), Shading.rowShadedFraction(GCR, TILT, 0.0, MORNING), 1e-12);
	}

	/** Facing south or north the sun runs along the rows, which is where the old figure put a 10 % loss. */
	@Test
	void aSunAlongTheRowsCastsNoRowShadow() {
		assertEquals(0.0, Shading.rowShadedFraction(GCR, TILT, 90.0, MORNING), 0.0);
		assertEquals(0.0, Shading.rowShadedFraction(GCR, TILT, 270.0, MORNING), 0.0);
	}

	/** A plane turned away from the sun is lit from behind, if at all, and no row in front of it is between. */
	@Test
	void aSunBehindTheRowsCastsNoRowShadow() {
		assertEquals(0.0, Shading.rowShadedFraction(GCR, TILT, 180.0, MORNING), 0.0);
	}

	/** Half way round, the profile angle is steeper than the elevation and the shadow shorter. */
	@Test
	void anObliqueSunCastsLess() {
		double facing = Shading.rowShadedFraction(GCR, 40.0, 0.0, MORNING);
		double oblique = Shading.rowShadedFraction(GCR, 40.0, 45.0, MORNING);
		assertTrue(oblique < facing, oblique + " against " + facing);
	}
}
