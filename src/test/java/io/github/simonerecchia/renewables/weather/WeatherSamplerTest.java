package io.github.simonerecchia.renewables.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.WorldConditions;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.catalog.TurbineCatalog;
import io.github.simonerecchia.renewables.weather.WeatherSampler.Input;

import org.junit.jupiter.api.Test;

/** The wind at the top of a mast, against the formulas it is built from and the way it should change with height. */
class WeatherSamplerTest {
	private static final long SEED = 123456789L;
	private static final int SEA = 63;
	/** Open grassland, flat: nothing makes it windier or calmer than the profile says. */
	private static final SiteConditions GRASS = new SiteConditions(0.03, 0.4, 0.8, 0.22, Double.NaN);

	private static final long NOON = 6000L;
	private static final long MIDNIGHT = 18000L;

	private static Input input(int mast, long dayTime, boolean raining, boolean thundering, SiteConditions site, boolean withSky) {
		// the machine stands on a tower of `mast` blocks over ground at y 70
		return new Input(SEED, 1200, -340, 70, SEA, mast, dayTime, dayTime, raining, thundering, site, withSky);
	}

	private static WeatherSnapshot at(int mast) {
		return WeatherSampler.sample(input(mast, NOON, false, false, GRASS, true));
	}

	@Test
	void windGrowsWithHeight() {
		double previous = 0.0;
		for (int mast = 1; mast <= 13; mast++) {
			double mean = at(mast).meanWind();
			assertTrue(mean >= previous - 1e-12, "mast " + mast);
			previous = mean;
		}

		assertTrue(at(13).meanWind() > at(2).meanWind());
	}

	@Test
	void meanWindIsThePowerLawProfileOfTheWindAloft() {
		for (int mast : new int[] {1, 6, 9, 13}) {
			double stability = Atmosphere.stability(Atmosphere.dayPhase(NOON), false, false);
			double aloft = Atmosphere.geostrophicWind(SEED, 1200, -340, NOON).speed();
			double expected = Atmosphere.windAtHeight(aloft, mast * 10.0, GRASS.roughness(), stability);
			assertEquals(expected, at(mast).meanWind(), 1e-9, "mast " + mast);
		}
	}

	@Test
	void aMastIsMeasuredAtTenMetresABlock() {
		assertEquals(10.0, WeatherSampler.heightM(1), 1e-9);
		assertEquals(130.0, WeatherSampler.heightM(13), 1e-9);
		// a mast of no blocks is read as one, so a ground instrument never asks for the wind at the ground itself
		assertEquals(10.0, WeatherSampler.heightM(0), 1e-9);
		assertEquals(at(1).meanWind(), WeatherSampler.sample(input(0, NOON, false, false, GRASS, true)).meanWind(), 1e-12);
	}

	@Test
	void roughGroundSlowsTheWindAtTheHub() {
		SiteConditions forest = new SiteConditions(0.55, 0.8, 0.7, 0.14, Double.NaN);
		double over = WeatherSampler.sample(input(9, NOON, false, false, GRASS, false)).meanWind();
		double inForest = WeatherSampler.sample(input(9, NOON, false, false, forest, false)).meanWind();
		assertTrue(inForest < over);
	}

	@Test
	void aSiteThatStandsProudOfTheLandIsWindier() {
		SiteConditions flat = new SiteConditions(0.03, 0.4, 0.8, 0.22, 70.0);
		SiteConditions hill = new SiteConditions(0.03, 0.4, 0.8, 0.22, 40.0);
		SiteConditions hollow = new SiteConditions(0.03, 0.4, 0.8, 0.22, 100.0);

		double onFlat = WeatherSampler.sample(input(9, NOON, false, false, flat, false)).meanWind();
		double onHill = WeatherSampler.sample(input(9, NOON, false, false, hill, false)).meanWind();
		double inHollow = WeatherSampler.sample(input(9, NOON, false, false, hollow, false)).meanWind();
		assertTrue(onHill > onFlat);
		assertTrue(inHollow < onFlat);
	}

	@Test
	void exposureIsLimitedBothWays() {
		assertEquals(0.30, new SiteConditions(0.03, 0.4, 0.8, 0.22, -500.0).exposure(70.0), 1e-9);
		assertEquals(-0.12, new SiteConditions(0.03, 0.4, 0.8, 0.22, 500.0).exposure(70.0), 1e-9);
		assertEquals(0.0, new SiteConditions(0.03, 0.4, 0.8, 0.22, Double.NaN).exposure(70.0), 1e-9);
		// thirty-two blocks of relief is 320 m: 0.64 of the way to the cap's own scale of 500 m
		assertEquals(0.30, new SiteConditions(0.03, 0.4, 0.8, 0.22, 38.0).exposure(70.0), 1e-9);
	}

	@Test
	void turbulenceFollowsTheStandardFormula() {
		double stability = Atmosphere.stability(Atmosphere.dayPhase(NOON), false, false);
		double expected = 1.0 / Math.log(130.0 / 0.03) * (1.0 - 0.30 * stability);
		assertEquals(expected, at(13).turbulence(), 1e-9);
		// calmer at the hub than near the ground: the ground's roughness matters less up there
		assertTrue(at(13).turbulence() < at(1).turbulence());
	}

	@Test
	void rainAndThunderMakeItGustier() {
		SiteConditions open = GRASS;
		// the same stability in all three, which is what the day phase gives at the neutral crossing
		long neutral = 1440L;
		double dry = WeatherSampler.sample(input(9, neutral, false, false, open, false)).turbulence();
		double rain = WeatherSampler.sample(input(9, neutral, true, false, open, false)).turbulence();
		double storm = WeatherSampler.sample(input(9, neutral, true, true, open, false)).turbulence();
		assertTrue(rain > dry * 1.1);
		assertTrue(storm > rain);
		// thunder without rain is not a thing: the flag only counts together with rain
		assertEquals(dry, WeatherSampler.sample(input(9, neutral, false, true, open, false)).turbulence(), 1e-12);
	}

	@Test
	void turbulenceStaysInItsRange() {
		SiteConditions rough = new SiteConditions(1.0, 1.0, 0.7, 0.12, Double.NaN);
		SiteConditions smooth = new SiteConditions(Atmosphere.SMOOTHEST_ROUGHNESS, 0.5, 0.5, 0.07, Double.NaN);
		for (int mast = 1; mast <= 13; mast++) {
			for (SiteConditions site : new SiteConditions[] {rough, smooth}) {
				double ti = WeatherSampler.sample(input(mast, NOON, true, true, site, false)).turbulence();
				assertTrue(ti >= 0.03 && ti <= 0.45, "ti " + ti);
			}
		}
	}

	@Test
	void gustIsMeanTimesOnePlusThreeTurbulence() {
		WeatherSnapshot w = at(9);
		assertEquals(w.meanWind() * (1.0 + 3.0 * w.turbulence()), w.gustWind(), 1e-9);
	}

	@Test
	void instantaneousWindAveragesOutToTheMeanAndNeverGoesNegative() {
		double total = 0.0;
		int samples = 6000;
		for (int i = 0; i < samples; i++) {
			// the gusts run on the game clock: one reading every second over an hour and a half
			WeatherSnapshot w = WeatherSampler.sample(new Input(SEED, 1200, -340, 70, SEA, 9, NOON, i * 20L, false, false, GRASS, false));
			assertTrue(w.instantWind() >= 0.0);
			total += w.instantWind();
		}

		double mean = at(9).meanWind();
		assertEquals(mean, total / samples, mean * 0.06);
	}

	@Test
	void directionIsAHeadingAndTurnsWithFriction() {
		for (int mast = 1; mast <= 13; mast++) {
			float direction = at(mast).direction();
			assertTrue(direction >= 0.0f && direction < 360.0f, "direction " + direction);
		}

		// rougher ground turns the surface wind further off the geostrophic one
		float aloft = Atmosphere.geostrophicWind(SEED, 1200, -340, NOON).direction();
		SiteConditions forest = new SiteConditions(0.55, 0.8, 0.7, 0.14, Double.NaN);
		float over = WeatherSampler.sample(input(9, NOON, false, false, GRASS, false)).direction();
		float inForest = WeatherSampler.sample(input(9, NOON, false, false, forest, false)).direction();
		double turnOver = Math.floorMod(Math.round(aloft - over), 360);
		double turnForest = Math.floorMod(Math.round(aloft - inForest), 360);
		assertTrue(turnForest > turnOver);
		assertTrue(turnOver > 0.0);
	}

	@Test
	void airDensityIsTheIdealGasLaw() {
		WeatherSnapshot w = at(9);
		assertEquals(w.pressureHpa() * 100.0 / (287.05 * (273.15 + w.temperatureC())), w.airDensity(), 1e-9);
	}

	@Test
	void highGroundHasThinnerColderAir() {
		WeatherSnapshot low = WeatherSampler.sample(new Input(SEED, 1200, -340, 64, SEA, 9, NOON, NOON, false, false, GRASS, false));
		WeatherSnapshot high = WeatherSampler.sample(new Input(SEED, 1200, -340, 180, SEA, 9, NOON, NOON, false, false, GRASS, false));
		assertTrue(high.pressureHpa() < low.pressureHpa());
		assertTrue(high.temperatureC() < low.temperatureC());
		assertTrue(high.airDensity() < low.airDensity());
	}

	@Test
	void seaLevelAirIsNearTheReferenceDensity() {
		// at sea level, near fifteen degrees, the density is close to the one every power curve is quoted at
		SiteConditions mild = new SiteConditions(0.03, 0.4, 0.55, 0.22, Double.NaN);
		WeatherSnapshot w = WeatherSampler.sample(new Input(SEED, 0, 0, SEA, SEA, 1, NOON, NOON, false, false, mild, false));
		assertEquals(WorldConditions.REFERENCE_AIR_DENSITY, w.airDensity(), 0.12);
	}

	@Test
	void theNightIsMoreStratifiedSoTheProfileIsSteeper() {
		double day = WeatherSampler.sample(input(9, NOON, false, false, GRASS, false)).shearExponent();
		double night = WeatherSampler.sample(input(9, MIDNIGHT, false, false, GRASS, false)).shearExponent();
		assertTrue(night > day);
		assertEquals(Atmosphere.profileExponent(GRASS.roughness(), Atmosphere.stability(Atmosphere.dayPhase(MIDNIGHT), false, false)), night, 1e-12);
	}

	@Test
	void sameWorldSamePlaceSameMomentSameAnswer() {
		assertEquals(at(9), at(9));
		Input other = new Input(SEED + 1, 1200, -340, 70, SEA, 9, NOON, NOON, false, false, GRASS, true);
		assertNotEquals(at(9).meanWind(), WeatherSampler.sample(other).meanWind());
	}

	@Test
	void aWindOnlyReadingIsTheSameReadingWithoutTheSky() {
		WeatherSnapshot full = at(9);
		WeatherSnapshot windOnly = WeatherSampler.sample(input(9, NOON, false, false, GRASS, false));
		assertNotNull(full.sky());
		assertNull(windOnly.sky());
		assertEquals(full.meanWind(), windOnly.meanWind(), 0.0);
		assertEquals(full.instantWind(), windOnly.instantWind(), 0.0);
		assertEquals(full.gustWind(), windOnly.gustWind(), 0.0);
		assertEquals(full.direction(), windOnly.direction(), 0.0);
		assertEquals(full.airDensity(), windOnly.airDensity(), 0.0);
		assertEquals(full.turbulence(), windOnly.turbulence(), 0.0);
	}

	@Test
	void theSkyCarriesTheSamplesOwnAirAndWind() {
		WeatherSnapshot w = at(1);
		assertEquals(w.temperatureC(), w.sky().ambientTempC(), 1e-12);
		assertEquals(w.instantWind(), w.sky().windSpeed(), 1e-12);
		assertEquals(GRASS.albedo(), w.sky().albedo(), 1e-12);
		// noon: the sun is up and there is light to be had
		assertTrue(w.sky().globalHorizontal() > 0.0);
	}

	@Test
	void midnightHasNoSun() {
		WeatherSnapshot w = WeatherSampler.sample(input(1, MIDNIGHT, false, false, GRASS, true));
		assertEquals(0.0, w.sky().globalHorizontal(), 0.0);
	}

	@Test
	void aTallerTowerOfTheSameMachineMakesMorePower() {
		TurbineSpec c130 = TurbineCatalog.C130_40;
		double low = c130.powerWhileRunningKw(at(c130.minTowerSegments()).meanWind(), at(c130.minTowerSegments()).airDensity());
		double high = c130.powerWhileRunningKw(at(c130.maxTowerSegments()).meanWind(), at(c130.maxTowerSegments()).airDensity());
		assertTrue(high >= low);
	}
}
