package io.github.simonerecchia.renewables.weather;

import io.github.simonerecchia.renewables.api.WorldConditions;

/**
 * The weather at the top of a mast, worked out from numbers alone.
 *
 * {@link LevelWeather} gathers what only a level can say (the seed, the clocks, whether it is raining in the
 * region, what the ground is) and hands it here, so the procedure itself - the one that turns a pressure field
 * and a patch of ground into a wind at a hub height - runs without a game and can be checked against its own
 * formulas.
 */
public final class WeatherSampler {
	private WeatherSampler() {
	}

	/**
	 * Everything the procedure needs.
	 *
	 * @param seed         the world seed, which fixes the pressure field and the prevailing wind
	 * @param x            block column of the machine
	 * @param z            block column of the machine
	 * @param groundY      the block at the foot of the mast: the machine's own block lowered by the mast's height
	 * @param seaLevel     the sea level of the level's terrain generator, in blocks (a flat world's is -63)
	 * @param mastBlocks   how many blocks of mast the instrument stands on; 1 for a ground instrument
	 * @param dayTime      the day clock, in ticks, which the sun and the daily cycle of the air run on
	 * @param gameTime     the game clock, in ticks, which the gusts and the drift of the cumulus run on
	 * @param weatherTime  the clock the pressure systems run on (see {@link LevelWeather#weatherClock}): it never jumps,
	 *                     so neither {@code /time set} nor a night slept through moves the weather
	 * @param raining      whether rain or snow is falling on the region
	 * @param thundering   whether there is a thunderstorm (only meaningful with rain)
	 * @param site         the ground around the mast
	 * @param withSky      whether the caller wants the sky (cloud cover, irradiance); a wind-only caller can skip it
	 * @param sunPhase     where the drawn sun is, {@link Atmosphere#sunPhase} of the level's celestial angle; NaN in a
	 *                     dimension with no sky, where there is no sun to sample
	 */
	public record Input(long seed, int x, int z, int groundY, int seaLevel, int mastBlocks, long dayTime, long gameTime,
			long weatherTime, boolean raining, boolean thundering, SiteConditions site, boolean withSky, double sunPhase) {
		/** An Overworld sky, whose sun follows the day clock through vanilla's own easing. */
		public Input(long seed, int x, int z, int groundY, int seaLevel, int mastBlocks, long dayTime, long gameTime,
				long weatherTime, boolean raining, boolean thundering, SiteConditions site, boolean withSky) {
			this(seed, x, z, groundY, seaLevel, mastBlocks, dayTime, gameTime, weatherTime, raining, thundering, site, withSky,
					Atmosphere.sunPhase(Atmosphere.celestialAngle(dayTime)));
		}
	}

	/** The measurement height of a mast of this many blocks, in metres. */
	public static double heightM(int mastBlocks) {
		return Math.max(1, mastBlocks) * WorldConditions.METRES_PER_BLOCK;
	}

	/**
	 * The weather at a mast, in the order the physics goes.
	 *
	 * <ol>
	 * <li>the air's layering from the time of day and the weather overhead;</li>
	 * <li>the wind above the boundary layer from the pressure field's gradient, plus the prevailing wind, and
	 *     faster where the site stands proud of the land around it;</li>
	 * <li>that wind brought down to the instrument's height through the roughness of the ground;</li>
	 * <li>its turbulence, the instantaneous wind it makes and the three-second gust;</li>
	 * <li>the heading, turned off the isobars by friction;</li>
	 * <li>temperature, station pressure and density from the ground's height above the sea.</li>
	 * </ol>
	 */
	public static WeatherSnapshot sample(Input in) {
		SiteConditions site = in.site();
		double dayPhase = Atmosphere.dayPhase(in.dayTime());
		boolean thundering = in.raining() && in.thundering();

		double stability = Atmosphere.stability(dayPhase, in.raining(), thundering);
		double heightM = heightM(in.mastBlocks());
		// the air the instrument stands in: the ground's height above the sea, and the mast on top of it. Ground
		// below the sea is the floor of a sea or a cave; the air above it starts at the surface
		double elevationM = Math.max(0, in.groundY() - in.seaLevel()) * WorldConditions.METRES_PER_BLOCK + heightM;

		Atmosphere.Flow aloft = Atmosphere.geostrophicWind(in.seed(), in.x(), in.z(), in.weatherTime());
		double windAloft = aloft.speed() * (1.0 + site.exposure());

		double meanWind = Atmosphere.windAtHeight(windAloft, heightM, site.roughness(), stability);
		double turbulence = Atmosphere.turbulenceIntensity(heightM, site.roughness(), stability, in.raining(), thundering);
		double fluctuation = Atmosphere.turbulentFluctuation(in.seed(), in.x(), in.z(), in.gameTime());
		double instantWind = Math.max(0.0, meanWind * (1.0 + turbulence * fluctuation));
		double gust = Atmosphere.gustFrom(meanWind, turbulence);

		float direction = Atmosphere.wrapDegrees(aloft.direction() - (float) Atmosphere.frictionTurn(site.roughness(), stability));

		double temperature = Atmosphere.temperatureAt(site.biomeTemperature(), site.downfall(), elevationM, dayPhase, in.raining(), thundering);
		double seaLevelPressure = Atmosphere.pressureAt(in.seed(), in.x(), in.z(), in.weatherTime());
		double pressure = Atmosphere.stationPressure(seaLevelPressure, elevationM, temperature);
		double density = Atmosphere.airDensity(temperature, pressure);
		double shear = Atmosphere.profileExponent(site.roughness(), stability);

		SkyConditions sky = in.withSky() ? skyAt(in, site, pressure, temperature, instantWind, thundering) : null;

		return new WeatherSnapshot(meanWind, instantWind, gust, turbulence, direction, temperature, pressure, density, shear, sky);
	}

	private static SkyConditions skyAt(Input in, SiteConditions site, double pressure, double temperature,
			double windSpeed, boolean thundering) {
		double cover = Atmosphere.cloudCover(in.seed(), in.x(), in.z(), in.weatherTime(), in.gameTime(), site.downfall(),
				in.raining(), thundering);

		// the sun the player sees, not the day clock read as a straight line: they are twelve degrees apart at dawn
		SunPosition sun = Double.isNaN(in.sunPhase()) ? SunPosition.NONE : Atmosphere.sunPosition(in.sunPhase());
		return Atmosphere.sky(sun, pressure, cover, site.albedo(), temperature, windSpeed);
	}
}
