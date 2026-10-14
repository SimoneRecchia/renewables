package io.github.simonerecchia.renewables.weather;

import io.github.simonerecchia.renewables.api.WorldConditions;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * What the ground under a machine does to the wind and the sunlight reaching it.
 *
 * @param terrainY           the natural ground (or sea surface) at the middle of the surveyed square, in blocks
 * @param surroundingGroundY the same, averaged over eight points a couple of chunks out; NaN when unknown
 */
public record SiteConditions(double roughness, double downfall, double biomeTemperature, double albedo, double terrainY,
		double surroundingGroundY) {
	/** How far out the land is measured to decide whether a site stands proud of it. */
	private static final int EXPOSURE_RADIUS = 32;
	/** The squares a survey stands for are this many blocks on a side (a shift): LevelWeather caches by them. */
	public static final int CELL_SHIFT = 3;
	/** Metres of relative elevation worth a full share of the speed-up below. */
	private static final double EXPOSURE_SCALE = 500.0;
	private static final double MAX_SPEED_UP = 0.30;
	private static final double MAX_SHELTER = -0.12;

	/** Roughness lengths, in metres */
	private static final double WATER = Atmosphere.SMOOTHEST_ROUGHNESS;
	private static final double SNOW_OR_SAND = 0.005;
	private static final double BARE_GROUND = 0.012;
	private static final double SCRUB = 0.05;
	private static final double ROCK = 0.06;
	private static final double FOREST = 0.55;
	private static final double DENSE_FOREST = 1.0;

	/** Albedos, and they are the published figures for the same ground the roughnesses above describe. */
	private static final double WATER_ALBEDO = 0.07;
	private static final double SNOW_ALBEDO = 0.80;
	private static final double SAND_ALBEDO = 0.35;
	private static final double BARE_GROUND_ALBEDO = 0.30;
	private static final double ROCK_ALBEDO = 0.20;
	private static final double GRASS_ALBEDO = 0.22;
	private static final double FOREST_ALBEDO = 0.14;
	private static final double DENSE_FOREST_ALBEDO = 0.12;

	/**
	 * Fraction to add to the wind aloft for a site that stands above the country around it.  Both heights are the
	 * land as the world made it, so a tower stood on a pillar or a roof gains nothing from what is under its foot.
	 */
	public double exposure() {
		return exposure(terrainY, surroundingGroundY);
	}

	/** The same, for a site at {@code siteY} in country whose mean height is {@code surroundingY}. */
	public static double exposure(double siteY, double surroundingY) {
		if (Double.isNaN(siteY) || Double.isNaN(surroundingY)) return 0.0;

		double relativeM = (siteY - surroundingY) * WorldConditions.METRES_PER_BLOCK;
		return Mth.clamp(relativeM / EXPOSURE_SCALE, MAX_SHELTER, MAX_SPEED_UP);
	}

	/** Surveys the column a machine stands on. */
	public static SiteConditions survey(ServerLevel level, BlockPos groundPos) {
		return survey(level, groundPos, null);
	}

	/**
	 * Surveys the column a machine stands on, taking the lie of the land from an earlier survey of the same square
	 * when there is one: it is the terrain generator's, so it never changes, and it is the dear part.
	 */
	public static SiteConditions survey(ServerLevel level, BlockPos groundPos, @Nullable SiteConditions earlier) {
		var biome = level.getBiome(groundPos);
		var climate = biome.value().getModifiedClimateSettings();
		double downfall = climate.downfall();

		double terrain;
		double surrounding;
		if (earlier != null) {
			terrain = earlier.terrainY();
			surrounding = earlier.surroundingGroundY();
		} else {
			int centreX = ((groundPos.getX() >> CELL_SHIFT) << CELL_SHIFT) + (1 << CELL_SHIFT) / 2;
			int centreZ = ((groundPos.getZ() >> CELL_SHIFT) << CELL_SHIFT) + (1 << CELL_SHIFT) / 2;
			terrain = naturalHeight(level, centreX, centreZ);
			surrounding = surveySurroundings(level, centreX, centreZ);
		}

		return new SiteConditions(roughnessOf(biome, downfall), downfall, biome.value().getBaseTemperature(),
				albedoOf(level, groundPos, biome, downfall), terrain, surrounding);
	}

	/** How much of the light this ground throws back up. */
	private static double albedoOf(ServerLevel level, BlockPos groundPos, net.minecraft.core.Holder<Biome> biome, double downfall) {
		BlockState surface = level.getBlockState(groundPos);
		if (surface.is(Blocks.SNOW) || surface.is(Blocks.SNOW_BLOCK) || surface.is(Blocks.POWDER_SNOW) || surface.is(Blocks.ICE)
				|| surface.is(Blocks.PACKED_ICE) || surface.is(Blocks.BLUE_ICE)) {
			return SNOW_ALBEDO;
		}

		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) return WATER_ALBEDO;
		if (biome.is(BiomeTags.IS_BEACH)) return SAND_ALBEDO;
		if (biome.is(BiomeTags.IS_JUNGLE)) return DENSE_FOREST_ALBEDO;
		if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)) return FOREST_ALBEDO;
		if (biome.is(BiomeTags.IS_BADLANDS)) return BARE_GROUND_ALBEDO;
		if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) return ROCK_ALBEDO;
		if (biome.is(BiomeTags.IS_SAVANNA)) return GRASS_ALBEDO;

		// nothing claimed it, so rainfall decides again: a desert is sand and anywhere wetter is
		// vegetation, which is darker
		return downfall < 0.2 ? SAND_ALBEDO : GRASS_ALBEDO;
	}

	private static double roughnessOf(net.minecraft.core.Holder<Biome> biome, double downfall) {
		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) return WATER;
		if (biome.is(BiomeTags.IS_BEACH)) return SNOW_OR_SAND;
		// checked before the mountain tags on purpose: a windswept forest carries both
		// is the trees that decide what the wind does
		if (biome.is(BiomeTags.IS_JUNGLE)) return DENSE_FOREST;
		if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)) return FOREST;
		if (biome.is(BiomeTags.IS_SAVANNA)) return SCRUB;
		if (biome.is(BiomeTags.IS_BADLANDS)) return BARE_GROUND;
		if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) return ROCK;

		// nothing claimed it: read the roughness off how much grows there. Tuned so a desert
		// lands on bare ground and plains on grassland, which are the two this actually decides
		return Mth.clamp(0.008 * Math.pow(12.0, Mth.clamp(downfall, 0.0, 1.0)), WATER, DENSE_FOREST);
	}

	/**
	 * Mean natural ground level of the eight points a couple of chunks out.  Asked of the terrain generator rather than of
	 * the chunks, so the answer is the same whichever chunks happen to be loaded when the question is asked; reading
	 * loaded chunks only, a survey made before its neighbours had loaded called a ridge flat for a minute.
	 */
	private static double surveySurroundings(ServerLevel level, int x, int z) {
		int[][] offsets = {{EXPOSURE_RADIUS, 0}, {-EXPOSURE_RADIUS, 0}, {0, EXPOSURE_RADIUS}, {0, -EXPOSURE_RADIUS}, {23, 23}, {23, -23}, {-23, 23}, {-23, -23}};
		double total = 0.0;
		for (int[] offset : offsets) {
			double height = naturalHeight(level, x + offset[0], z + offset[1]);
			if (Double.isNaN(height)) return Double.NaN;

			total += height;
		}

		return total / offsets.length;
	}

	/**
	 * The height of the land the generator lays at a column, the sea surface over water: what was there before anybody
	 * built, which is what the wind has crossed to get here.
	 */
	private static double naturalHeight(ServerLevel level, int x, int z) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		try {
			return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
		} catch (RuntimeException e) {
			// a generator of another mod that cannot answer for a column leaves the site neither proud nor sheltered
			return Double.NaN;
		}
	}
}
