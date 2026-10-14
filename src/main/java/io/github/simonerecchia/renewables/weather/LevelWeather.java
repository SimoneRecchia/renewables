package io.github.simonerecchia.renewables.weather;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The weather of one server level, and where machines in it ask for a reading.
 *
 * STUB: owner W.  The atmosphere itself ({@link Atmosphere}, {@link WeatherNoise}, {@link SiteConditions}) is
 * finished; what is missing is the part that joins them into a wind and sky at a position (wind.md section 3).
 * This version answers every query with a calm, clear day at the level's real time of day, so that machines
 * which ask for weather run and show sensible numbers until W replaces the sampling.
 *
 * One manager per level, created on first use and dropped when the level unloads or the server stops.
 */
public final class LevelWeather {
	private static final Map<ServerLevel, LevelWeather> BY_LEVEL = new ConcurrentHashMap<>();

	private final ServerLevel level;

	private LevelWeather(ServerLevel level) {
		this.level = level;
	}

	/** The manager for a level, created the first time it is asked for. */
	public static LevelWeather get(ServerLevel level) {
		return BY_LEVEL.computeIfAbsent(level, LevelWeather::new);
	}

	/** Runs at the start of every server tick for every level. */
	public static void tick(ServerLevel level) {
		get(level).advance();
	}

	public static void remove(ServerLevel level) {
		BY_LEVEL.remove(level);
	}

	public static void clear() {
		BY_LEVEL.clear();
	}

	private void advance() {
		// STUB: owner W - prune the sites remembered for longer than 1200 ticks once there are more than 4096
	}

	/**
	 * The weather at a position, as seen from the top of a mast that many blocks high.
	 *
	 * @param towerBlocks the height above the block, in blocks; 1 for a ground instrument
	 */
	public WeatherSnapshot sample(BlockPos pos, int towerBlocks) {
		double phase = Atmosphere.dayPhase(level.getDayTime());
		SkyConditions sky = Atmosphere.sky(Atmosphere.sunPosition(phase), 1013.25, 0.0, 0.22, 15.0, 5.0);

		return new WeatherSnapshot(5.0, 5.0, 6.0, 0.1, 0.0f, 15.0, 1013.25, Atmosphere.airDensity(15.0, 1013.25), 0.14, sky);
	}

	/** What the ground under a machine does to wind and light.  Surveys the column. */
	public SiteConditions siteAt(BlockPos groundPos) {
		return SiteConditions.survey(level, groundPos);
	}
}
