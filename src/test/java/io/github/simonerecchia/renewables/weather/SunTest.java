package io.github.simonerecchia.renewables.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The sun the mod works with is the one Minecraft draws, and a dimension with no sky has none. */
class SunTest {
	private static final SiteConditions GRASS = new SiteConditions(0.03, 0.4, 0.8, 0.22, Double.NaN);

	private static SunPosition sunAt(long dayTime) {
		return Atmosphere.sunPosition(Atmosphere.sunPhase(Atmosphere.celestialAngle(dayTime)));
	}

	/** The figures vanilla's own celestial angle gives: 12.4° up at tick 0, 27.6 at 1000, overhead at 6000. */
	@Test
	void theSunIsWhereVanillaDrawsIt() {
		assertEquals(12.4, sunAt(0L).elevationDeg(), 0.05);
		assertFalse(sunAt(0L).afternoon());
		assertEquals(27.6, sunAt(1000L).elevationDeg(), 0.05);
		assertEquals(90.0, sunAt(6000L).elevationDeg(), 1e-6);
		assertEquals(12.4, sunAt(12000L).elevationDeg(), 0.05);
		assertTrue(sunAt(12000L).afternoon());
	}

	/** It rises some eight hundred ticks before the day clock's zero and sets as long after twelve thousand. */
	@Test
	void sunriseAndSunsetAreWhereThePlayerSeesThem() {
		assertFalse(sunAt(23100L).up());
		assertTrue(sunAt(23300L).up());
		assertTrue(sunAt(12700L).up());
		assertFalse(sunAt(12900L).up());
		// and the next day is the same day
		assertEquals(sunAt(500L).elevationDeg(), sunAt(24500L).elevationDeg(), 1e-9);
		assertEquals(sunAt(-1000L).elevationDeg(), sunAt(23000L).elevationDeg(), 1e-9);
	}

	/** Under the Nether's roof there is no sun, at any hour of the Overworld's clock. */
	@Test
	void aDimensionWithNoSkyIsDarkAtNoon() {
		WeatherSnapshot lit = WeatherSampler.sample(new WeatherSampler.Input(1L, 0, 0, 70, 63, 1, 6000L, 6000L,
				false, false, GRASS, true));
		WeatherSnapshot roofed = WeatherSampler.sample(new WeatherSampler.Input(1L, 0, 0, 70, 63, 1, 6000L, 6000L,
				false, false, GRASS, true, Double.NaN));

		assertTrue(lit.sky().sun().up());
		assertTrue(lit.sky().globalHorizontal() > 0.0);
		assertFalse(roofed.sky().sun().up());
		assertEquals(0.0, roofed.sky().globalHorizontal(), 0.0);
		assertEquals(0.0, roofed.sky().directNormal(), 0.0);
		// the air is the same air: only the light is gone
		assertEquals(lit.temperatureC(), roofed.temperatureC(), 0.0);
	}
}
