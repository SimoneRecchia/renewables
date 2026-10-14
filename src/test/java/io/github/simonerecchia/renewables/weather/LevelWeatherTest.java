package io.github.simonerecchia.renewables.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Which terrain survey a column shares: the ground is surveyed for squares of eight blocks, and the height is not part of it. */
class LevelWeatherTest {
	@Test
	void aSquareOfEightByEightSharesASurvey() {
		long corner = LevelWeather.siteKey(new BlockPos(0, 64, 0));
		assertEquals(corner, LevelWeather.siteKey(new BlockPos(7, 64, 7)));
		assertNotEquals(corner, LevelWeather.siteKey(new BlockPos(8, 64, 0)));
		assertNotEquals(corner, LevelWeather.siteKey(new BlockPos(0, 64, 8)));
	}

	@Test
	void theHeightIsNotPartOfTheKey() {
		assertEquals(LevelWeather.siteKey(new BlockPos(100, 10, 100)), LevelWeather.siteKey(new BlockPos(100, 200, 100)));
	}

	@Test
	void squaresAcrossTheOriginAreDistinct() {
		long minusOne = LevelWeather.siteKey(new BlockPos(-1, 64, -1));
		assertEquals(minusOne, LevelWeather.siteKey(new BlockPos(-8, 64, -8)));
		assertNotEquals(minusOne, LevelWeather.siteKey(new BlockPos(-9, 64, -1)));
		assertNotEquals(minusOne, LevelWeather.siteKey(new BlockPos(0, 64, -1)));
		assertNotEquals(LevelWeather.siteKey(new BlockPos(-8, 64, 0)), LevelWeather.siteKey(new BlockPos(0, 64, -8)));
	}
}
