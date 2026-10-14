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
	private static final SiteConditions GRASS = new SiteConditions(0.03, 0.4, 0.8, 0.22, 70.0, Double.NaN);

	private static final long NOON = 6000L;
	private static final long MIDNIGHT = 18000L;

	private static Input input(int mast, long dayTime, boolean raining, boolean thundering, SiteConditions site, boolean withSky) {
		// the machine stands on a tower of `mast` blocks over ground at y 70
		return new Input(SEED, 1200, -340, 70, SEA, mast, dayTime, dayTime, dayTime, raining, thundering, site, withSky);
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
		SiteConditions forest = new SiteConditions(0.55, 0.8, 0.7, 0.14, 70.0, Double.NaN);
		double over = WeatherSampler.sample(input(9, NOON, false, false, GRASS, false)).meanWind();
		double inForest = WeatherSampler.sample(input(9, NOON, false, false, forest, false)).meanWind();
		assertTrue(inForest < over);
	}

	@Test
	void aSiteThatStandsProudOfTheLandIsWindier() {
		SiteConditions flat = new SiteConditions(0.03, 0.4, 0.8, 0.22, 70.0, 70.0);
		SiteConditions hill = new SiteConditions(0.03, 0.4, 0.8, 0.22, 70.0, 40.0);
		SiteConditions hollow = new SiteConditions(0.03, 0.4, 0.8, 0.22, 70.0, 100.0);

		double onFlat = WeatherSampler.sample(input(9, NOON, false, false, flat, false)).meanWind();
		double onHill = WeatherSampler.sample(input(9, NOON, false, false, hill, false)).meanWind();
		double inHollow = WeatherSampler.sample(input(9, NOON, false, false, hollow, false)).meanWind();
		assertTrue(onHill > onFlat);
		assertTrue(inHollow < onFlat);
	}

	@Test
	void exposureIsLimitedBothWays() {
		assertEquals(0.30, new SiteConditions(0.03, 0.4, 0.8, 0.22, 70.0, -500.0).exposure(), 1e-9);
		assertEquals(-0.12, new SiteConditions(0.03, 0.4, 0.8, 0.22, 70.0, 500.0).exposure(), 1e-9);
		assertEquals(0.0, new SiteConditions(0.03, 0.4, 0.8, 0.22, 70.0, Double.NaN).exposure(), 1e-9);
		// thirty-two blocks of relief is 320 m: 0.64 of the way to the cap's own scale of 500 m
		assertEquals(0.30, new SiteConditions(0.03, 0.4, 0.8, 0.22, 70.0, 38.0).exposure(), 1e-9);
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
		SiteConditions rough = new SiteConditions(1.0, 1.0, 0.7, 0.12, 70.0, Double.NaN);
		SiteConditions smooth = new SiteConditions(Atmosphere.SMOOTHEST_ROUGHNESS, 0.5, 0.5, 0.07, 70.0, Double.NaN);
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
			WeatherSnapshot w = WeatherSampler.sample(new Input(SEED, 1200, -340, 70, SEA, 9, NOON, i * 20L, NOON, false, false, GRASS, false));
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
		SiteConditions forest = new SiteConditions(0.55, 0.8, 0.7, 0.14, 70.0, Double.NaN);
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
		WeatherSnapshot low = WeatherSampler.sample(new Input(SEED, 1200, -340, 64, SEA, 9, NOON, NOON, NOON, false, false, GRASS, false));
		WeatherSnapshot high = WeatherSampler.sample(new Input(SEED, 1200, -340, 180, SEA, 9, NOON, NOON, NOON, false, false, GRASS, false));
		assertTrue(high.pressureHpa() < low.pressureHpa());
		assertTrue(high.temperatureC() < low.temperatureC());
		assertTrue(high.airDensity() < low.airDensity());
	}

	@Test
	void seaLevelAirIsNearTheReferenceDensity() {
		// at sea level, near fifteen degrees, the density is close to the one every power curve is quoted at
		SiteConditions mild = new SiteConditions(0.03, 0.4, 0.55, 0.22, 70.0, Double.NaN);
		WeatherSnapshot w = WeatherSampler.sample(new Input(SEED, 0, 0, SEA, SEA, 1, NOON, NOON, NOON, false, false, mild, false));
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
		Input other = new Input(SEED + 1, 1200, -340, 70, SEA, 9, NOON, NOON, NOON, false, false, GRASS, true);
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

	// ---- the review's findings ----

	/**
	 * A flat world's ground is at y -60.  Read against a sea level of 63 it was 1230 m under the sea, in air at 1170 hPa
	 * and 1.48 kg/m3; ground under the sea now reads as the sea's surface, and a flat world's own generator puts its
	 * sea at -63, 30 m below its ground.
	 */
	@Test
	void groundUnderTheSeaIsReadAtTheSurface() {
		SiteConditions mild = new SiteConditions(0.03, 0.4, 0.55, 0.22, 70.0, Double.NaN);
		WeatherSnapshot under = WeatherSampler.sample(new Input(SEED, 0, 0, -60, SEA, 1, NOON, NOON, NOON, false, false, mild, false));
		WeatherSnapshot at = WeatherSampler.sample(new Input(SEED, 0, 0, SEA, SEA, 1, NOON, NOON, NOON, false, false, mild, false));
		assertEquals(at.pressureHpa(), under.pressureHpa(), 1e-9);
		assertTrue(under.airDensity() < 1.3, "density " + under.airDensity());

		WeatherSnapshot flat = WeatherSampler.sample(new Input(SEED, 0, 0, -60, -63, 1, NOON, NOON, NOON, false, false, mild, false));
		assertTrue(flat.pressureHpa() < at.pressureHpa());
		assertEquals(at.pressureHpa(), flat.pressureHpa(), 5.0);
	}

	/** The hub's air is the air at the hub: 130 m up it is thinner than 10 m up, by the scale height's 1.4 %. */
	@Test
	void theAirIsTakenAtTheInstrumentsHeight() {
		assertTrue(at(13).pressureHpa() < at(1).pressureHpa());
		assertEquals(at(1).pressureHpa() * Math.exp(-120.0 / 8400.0), at(13).pressureHpa(), 0.5);
	}

	/**
	 * The cumulus field drifted by the instantaneous wind times the whole game clock, so after three hours of play a
	 * change of the wind moved it kilometres in a tick and the cover jumped by up to 0.53.  It drifts with the steering
	 * flow now, and a tick moves it half a metre.
	 */
	@Test
	void cloudCoverDoesNotJumpAfterHoursOfPlay() {
		SiteConditions wet = new SiteConditions(0.03, 0.8, 0.8, 0.22, 70.0, Double.NaN);
		for (long start : new long[] {216_000L, 720_000L, 5_000_000L}) {
			for (int x = -4000; x <= 4000; x += 800) {
				double previous = Atmosphere.cloudCover(SEED, x, 77, start, start, wet.downfall(), false, false);
				for (long t = start + 1; t <= start + 40; t++) {
					double now = Atmosphere.cloudCover(SEED, x, 77, t, t, wet.downfall(), false, false);
					assertTrue(Math.abs(now - previous) < 0.01, "jump " + (now - previous) + " at " + t);
					previous = now;
				}
			}
		}
	}

	/** Centred difference over the same 75 blocks the geostrophic wind is worked out from, hPa per block. */
	private static double[] pressureGradient(double x, double z, long time) {
		double step = 75.0;
		return new double[] {
				(Atmosphere.pressureAt(SEED, x + step, z, time) - Atmosphere.pressureAt(SEED, x - step, z, time)) / (2 * step),
				(Atmosphere.pressureAt(SEED, x, z + step, time) - Atmosphere.pressureAt(SEED, x, z - step, time)) / (2 * step)};
	}

	/**
	 * At 45° north the wind aloft keeps the low on its left (Buys Ballot).  With x east and z south the left of a
	 * heading (u, w) is (w, -u), and the low lies against the gradient, so grad p . (w, -u) < 0 everywhere.  The
	 * prevailing westerly, which no local gradient explains, is taken off first.
	 */
	@Test
	void theWindAloftKeepsTheLowOnItsLeft() {
		double heading = Math.toRadians(Atmosphere.prevailingDirection(SEED));
		int checked = 0;
		for (int i = 0; i < 2000; i++) {
			double x = (i % 50) * 997.0 - 25_000.0;
			double z = (i / 50) * 1013.0 - 20_000.0;
			Atmosphere.Flow aloft = Atmosphere.geostrophicWind(SEED, x, z, NOON);
			double u = aloft.x() - Math.cos(heading) * 4.0;
			double w = aloft.z() - Math.sin(heading) * 4.0;
			double[] grad = pressureGradient(x, z, NOON);
			if (Math.hypot(grad[0], grad[1]) < 1e-6) continue;

			assertTrue(grad[0] * w - grad[1] * u < 0.0, "point " + i);
			checked++;
		}

		assertTrue(checked > 1900);
	}

	/**
	 * Friction backs the surface wind across the isobars towards the low.  The isobars are those of the whole field:
	 * the local systems, and the large-scale gradient that drives the prevailing westerly, which at 45° N is the
	 * gradient whose geostrophic wind it is (u = s dp/dz, w = -s dp/dx, s = 100 / 400 / (1.225 x 1e-4) m/s per hPa a
	 * block).  The review found 1903 points in 2000 crossing towards the high; every one now crosses towards the low.
	 */
	@Test
	void frictionTurnsTheSurfaceWindTowardsTheLow() {
		double scale = 100.0 / 400.0 / (WorldConditions.REFERENCE_AIR_DENSITY * 1.0e-4);
		double heading = Math.toRadians(Atmosphere.prevailingDirection(SEED));
		double[] background = {-Math.sin(heading) * 4.0 / scale, Math.cos(heading) * 4.0 / scale};

		for (int i = 0; i < 2000; i++) {
			int x = (i % 50) * 997 - 25_000;
			int z = (i / 50) * 1013 - 20_000;
			WeatherSnapshot w = WeatherSampler.sample(new Input(SEED, x, z, 70, SEA, 9, NOON, NOON, NOON, false, false, GRASS, false));
			double[] grad = pressureGradient(x, z, NOON);
			double towards = Math.toRadians(w.direction());
			double along = (grad[0] + background[0]) * Math.cos(towards) + (grad[1] + background[1]) * Math.sin(towards);
			assertTrue(along < 0.0, "point " + i);
		}
	}

	/**
	 * The weather systems ran on the day clock, so {@code /time set} moved the mean wind from 4.8 to 15.7 m/s in one tick,
	 * a night slept through skipped half a day of weather, and with the day cycle off the field never moved again.  They
	 * run on the weather clock now: the same weather-clock reading is the same weather whatever the day clock says.
	 */
	@Test
	void settingTheTimeOfDayDoesNotMoveTheWeather() {
		long weather = 1_234_567L;
		for (long jump : new long[] {24000L, 5 * 24000L, 1000 * 24000L}) {
			Input before = new Input(SEED, 1200, -340, 70, SEA, 9, NOON, weather, weather, false, false, GRASS, true);
			Input after = new Input(SEED, 1200, -340, 70, SEA, 9, NOON + jump, weather, weather, false, false, GRASS, true);
			assertEquals(WeatherSampler.sample(before), WeatherSampler.sample(after), "a jump of " + jump + " ticks");
		}

		// what the day clock still decides is the daily cycle of the air: a calm, stable night against a mixed noon
		Input noon = new Input(SEED, 1200, -340, 70, SEA, 9, NOON, weather, weather, false, false, GRASS, false);
		Input night = new Input(SEED, 1200, -340, 70, SEA, 9, MIDNIGHT, weather, weather, false, false, GRASS, false);
		assertNotEquals(WeatherSampler.sample(noon).meanWind(), WeatherSampler.sample(night).meanWind());
	}

	/** With the day cycle switched off the day clock stands still, and the wind still comes and goes. */
	@Test
	void theWindKeepsChangingWithTheDayCycleOff() {
		double lowest = Double.MAX_VALUE;
		double highest = 0.0;
		for (long weather = 0; weather <= 4 * 24000L; weather += 1000L) {
			double mean = WeatherSampler.sample(new Input(SEED, 1200, -340, 70, SEA, 9, NOON, weather, weather, false, false, GRASS, false)).meanWind();
			lowest = Math.min(lowest, mean);
			highest = Math.max(highest, mean);
		}

		assertTrue(highest - lowest > 1.0, "the wind ranged only " + lowest + " to " + highest);
	}

	/** And the weather clock moves it a little at a time: no tick of it changes the mean wind by a noticeable step. */
	@Test
	void theWeatherClockMovesTheWindSmoothly() {
		double previous = WeatherSampler.sample(new Input(SEED, 1200, -340, 70, SEA, 9, NOON, 0, 500_000L, false, false, GRASS, false)).meanWind();
		for (long weather = 500_001L; weather <= 502_000L; weather++) {
			double mean = WeatherSampler.sample(new Input(SEED, 1200, -340, 70, SEA, 9, NOON, 0, weather, false, false, GRASS, false)).meanWind();
			assertTrue(Math.abs(mean - previous) < 0.02, "step " + (mean - previous) + " at " + weather);
			previous = mean;
		}
	}

	/**
	 * A tower stood on a pillar or a building had the top of the pillar for its ground, and its exposure went to the cap:
	 * 14.5 m/s against 11.3 for the same machine on the ground.  The exposure is the lie of the land the world made, and
	 * what is under the foot changes the air's pressure and nothing about the wind.
	 */
	@Test
	void aPillarUnderTheFootIsNotAHill() {
		SiteConditions plain = new SiteConditions(0.03, 0.4, 0.8, 0.22, 70.0, 70.0);
		WeatherSnapshot onTheGround = WeatherSampler.sample(new Input(SEED, 1200, -340, 70, SEA, 13, NOON, NOON, NOON, false, false, plain, false));
		WeatherSnapshot onAPillar = WeatherSampler.sample(new Input(SEED, 1200, -340, 84, SEA, 13, NOON, NOON, NOON, false, false, plain, false));
		assertEquals(onTheGround.meanWind(), onAPillar.meanWind(), 0.0);
		assertEquals(0.0, plain.exposure(), 0.0);
	}
}
