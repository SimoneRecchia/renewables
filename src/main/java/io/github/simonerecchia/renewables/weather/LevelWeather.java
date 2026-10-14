package io.github.simonerecchia.renewables.weather;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The weather of one server level, and where machines in it ask for a reading.
 *
 * The atmosphere itself ({@link Atmosphere}, {@link WeatherNoise}) is pure physics and {@link WeatherSampler}
 * strings it together for one mast; what this class adds is everything that needs the level: the seed, the clock,
 * rain on the spot, the survey of the ground, and a cache of those surveys so a hundred machines on the same hill
 * do not each walk the terrain every tick.
 *
 * One manager per level, created on first use and dropped when the level unloads or the server stops.  There is no
 * filter by dimension: the Nether and the End have weather too, as far as the machines in them are concerned.
 */
public final class LevelWeather {
	/** A survey of the ground stays good this long, in ticks: the land does not change in a minute. */
	private static final long SITE_TTL_TICKS = 1200L;
	/** Past this many remembered sites the old ones are dropped. */
	private static final int SITE_CACHE_LIMIT = 4096;
	/** Terrain is surveyed for squares of this many blocks on a side (a shift, so 8). */
	private static final int SITE_CELL_SHIFT = 3;

	private static final Map<ServerLevel, LevelWeather> BY_LEVEL = new ConcurrentHashMap<>();

	private final ServerLevel level;
	private final Map<Long, CachedSite> sites = new ConcurrentHashMap<>();

	private record CachedSite(long surveyedAt, SiteConditions site) {
	}

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

	/** Nothing here moves with time; the one thing to do each tick is to forget old surveys once there are many. */
	private void advance() {
		if (sites.size() <= SITE_CACHE_LIMIT) return;

		long now = level.getGameTime();
		sites.values().removeIf(cached -> now - cached.surveyedAt() > SITE_TTL_TICKS);
	}

	/**
	 * The weather at a position, as seen from the top of a mast that many blocks high.
	 *
	 * @param towerBlocks the height above the ground, in blocks; 1 for a ground instrument.  The ground is the block
	 *                    {@code towerBlocks} below {@code pos}, which for a turbine is the foot of its tower and
	 *                    for a panel the block it stands on
	 */
	public WeatherSnapshot sample(BlockPos pos, int towerBlocks) {
		return sample(pos, towerBlocks, true);
	}

	/**
	 * As {@link #sample(BlockPos, int)} for a caller that only wants the wind and the air: the snapshot's sky is then
	 * null, and the cloud and irradiance work is not done.  A turbine asks every tick and has no use for the sun.
	 */
	public WeatherSnapshot sampleWind(BlockPos pos, int towerBlocks) {
		return sample(pos, towerBlocks, false);
	}

	private WeatherSnapshot sample(BlockPos pos, int towerBlocks, boolean withSky) {
		BlockPos ground = pos.below(Math.max(0, towerBlocks));
		boolean raining = level.isRainingAt(pos.above());

		return WeatherSampler.sample(new WeatherSampler.Input(level.getSeed(), pos.getX(), pos.getZ(), ground.getY(),
				seaLevel(), towerBlocks, level.getDayTime(), level.getGameTime(), raining,
				raining && level.isThundering(), siteAt(ground), withSky));
	}

	/**
	 * Where the sea is in this level.  {@code Level.getSeaLevel()} is the constant 63 in 1.20.1, whatever the world;
	 * a flat world's ground is at -60, which read against 63 put it 1230 m under the sea, in air at 1170 hPa and 1.48
	 * kg/m3.  The terrain generator knows its own: 63 for the default, -63 for a flat world, 32 for the Nether.
	 */
	private int seaLevel() {
		return level.getChunkSource().getGenerator().getSeaLevel();
	}

	/** What the ground under a machine does to wind and light.  Surveys the column, or reuses a recent survey of its square. */
	public SiteConditions siteAt(BlockPos groundPos) {
		long key = siteKey(groundPos);
		long now = level.getGameTime();

		CachedSite cached = sites.get(key);
		if (cached != null && now - cached.surveyedAt() <= SITE_TTL_TICKS) return cached.site();

		SiteConditions surveyed = SiteConditions.survey(level, groundPos);
		sites.put(key, new CachedSite(now, surveyed));

		return surveyed;
	}

	/** The cell of the 8x8 grid a column is in; the height is not part of it. */
	static long siteKey(BlockPos pos) {
		return ((long) (pos.getX() >> SITE_CELL_SHIFT) << 32) | ((pos.getZ() >> SITE_CELL_SHIFT) & 0xFFFFFFFFL);
	}
}
