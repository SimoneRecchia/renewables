package io.github.simonerecchia.renewables.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Animation steps follow the clock, not the number of frames. */
class FrameClockTest {
	private static final long MS = 1_000_000L;

	@BeforeEach
	void fresh() {
		FrameClock.reset();
	}

	@Test
	void theFirstFrameHasNoDuration() {
		FrameClock.advance(5_000 * MS, 1.0, false);
		assertEquals(0.0f, FrameClock.seconds());
	}

	@Test
	void aSixtiethOfASecondIsOneReferenceFrame() {
		FrameClock.advance(1_000 * MS, 1.0, false);
		FrameClock.advance(1_000 * MS + 1_000_000_000L / 60, 2.0, false);
		assertEquals(1.0f / 60.0f, FrameClock.seconds(), 1e-5f);
		assertEquals(1.0f, FrameClock.perSixty(), 1e-3f);
	}

	@Test
	void aSlowFrameTakesProportionallyBiggerSteps() {
		FrameClock.advance(1_000 * MS, 1.0, false);
		FrameClock.advance(1_000 * MS + 100 * MS, 2.0, false);
		assertEquals(6.0f, FrameClock.perSixty(), 1e-3f);
	}

	@Test
	void everyPassOfTheSameFrameSeesTheSameDuration() {
		FrameClock.advance(1_000 * MS, 1.0, false);
		FrameClock.advance(1_020 * MS, 2.0, false);
		float first = FrameClock.seconds();
		FrameClock.advance(1_025 * MS, 2.0, false);
		assertEquals(first, FrameClock.seconds());
	}

	@Test
	void aStallIsCappedSoNothingLeaps() {
		FrameClock.advance(1_000 * MS, 1.0, false);
		FrameClock.advance(1_000 * MS + 30_000 * MS, 2.0, false);
		assertEquals((float) FrameClock.MAX_SECONDS, FrameClock.seconds());
	}

	@Test
	void pausedMeansNothingMovesAndTheFrameAfterTheBreakIsNormal() {
		FrameClock.advance(1_000 * MS, 1.0, false);
		FrameClock.advance(1_020 * MS, 2.0, false);
		FrameClock.advance(61_000 * MS, 3.0, true);
		assertEquals(0.0f, FrameClock.seconds());

		// a minute later the game resumes: the next frame lasted what a frame lasts, not a minute
		FrameClock.advance(61_000 * MS + 16 * MS, 4.0, false);
		assertEquals(0.016f, FrameClock.seconds(), 1e-4f);
	}
}
