package io.github.simonerecchia.renewables.client.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WireProfileTest {
	private static final double ABC_SAG = 0.055;
	private static final double MV_SAG = 0.030;
	private static final double HV_SAG = 0.022;

	@Test
	void dropIsSagTimesLengthSoTheCatalogueNumbersGivePlausibleDips() {
		assertEquals(2.2, WireProfile.drop(ABC_SAG, 40.0), 1.0e-9);
		assertEquals(2.7, WireProfile.drop(MV_SAG, 90.0), 1.0e-9);
		assertEquals(3.52, WireProfile.drop(HV_SAG, 160.0), 1.0e-9);
		assertEquals(0.2, WireProfile.drop(0.005, 40.0), 1.0e-9);
		// the ratio between a street bundle and a transmission line is what the catalogue's comment promises
		assertEquals(2.5, ABC_SAG / HV_SAG, 1.0e-9);
	}

	@Test
	void aLevelSpanHangsByItsDropAtTheMiddleAndMeetsBothEnds() {
		WireProfile profile = new WireProfile();
		assertTrue(profile.rebuild(40.0, 0.0, 0.0, ABC_SAG));

		int last = profile.segments();
		assertFalse(profile.straight());
		assertEquals(40.0 / WireProfile.BASE_STEP, last);
		assertEquals(0.0, profile.y(0), 1.0e-6);
		assertEquals(0.0, profile.y(last), 1.0e-6);
		assertEquals(40.0f, profile.x(last), 0.0f);
		assertEquals(-2.2, profile.y(last / 2), 1.0e-4);
		assertEquals(-2.2, profile.lowest(), 1.0e-9);
	}

	@Test
	void aSlopingSpanMeetsBothEndsAndHangsBelowItsChord() {
		WireProfile profile = new WireProfile();
		assertTrue(profile.rebuild(30.0, 6.0, 40.0, MV_SAG));

		int last = profile.segments();
		assertEquals(30.0f, profile.x(last), 0.0f);
		assertEquals(6.0f, profile.y(last), 0.0f);
		assertEquals(40.0f, profile.z(last), 0.0f);

		double drop = WireProfile.drop(MV_SAG, Math.sqrt(30.0 * 30 + 6 * 6 + 40 * 40));
		for (int i = 1; i < last; i++) {
			double t = (double) i / last;
			double chordY = 6.0 * t;
			assertTrue(profile.y(i) <= chordY + 1.0e-4, "the cable never rises above its chord");
			assertEquals(chordY - 4 * drop * t * (1 - t), profile.y(i), 1.0e-4);
		}
	}

	@Test
	void theLowestPointIsFoundAnalyticallyEvenWhenItIsNotAtTheMiddle() {
		double dy = 8.0;
		double drop = 3.0;
		double analytic = WireProfile.lowestHeight(dy, drop);

		double sampled = Double.MAX_VALUE;
		for (int i = 0; i <= 100000; i++) {
			sampled = Math.min(sampled, WireProfile.heightAt(i / 100000.0, dy, drop));
		}

		assertEquals(sampled, analytic, 1.0e-6);
		assertTrue(analytic < 0.0, "the cable dips under its own start when the drop outweighs a gentle rise");
	}

	@Test
	void aSteepSpanThatNeverDipsBelowItsLowerEndReportsThatEnd() {
		// a drop of 1 on a rise of 20: the cable is monotonic and its lowest point is the start
		assertEquals(0.0, WireProfile.lowestHeight(20.0, 1.0), 1.0e-12);
		assertEquals(-20.0, WireProfile.lowestHeight(-20.0, 1.0), 1.0e-12);
	}

	@Test
	void noSagIsAStraightTubeNotNaN() {
		WireProfile profile = new WireProfile();
		assertTrue(profile.rebuild(40.0, 5.0, 0.0, 0.0));

		assertTrue(profile.straight());
		assertEquals(1, profile.segments());
		assertEquals(0.0f, profile.y(0), 0.0f);
		assertEquals(5.0f, profile.y(1), 0.0f);
		for (int i = 0; i <= 1; i++) {
			assertTrue(Float.isFinite(profile.x(i)) && Float.isFinite(profile.y(i)) && Float.isFinite(profile.z(i)));
		}
	}

	@Test
	void aVerticalSpanGoesFromItsStartToItsEndInBothDirections() {
		WireProfile up = new WireProfile();
		assertTrue(up.rebuild(0.0, 8.0, 0.0, ABC_SAG));
		assertTrue(up.straight());
		assertEquals(1, up.segments());
		assertEquals(8.0f, up.y(1), 0.0f);

		WireProfile down = new WireProfile();
		assertTrue(down.rebuild(0.02, -8.0, 0.0, ABC_SAG));
		assertTrue(down.straight());
		assertEquals(-8.0f, down.y(1), 0.0f);
		assertEquals(-8.0, down.lowest(), 0.0);
	}

	@Test
	void aSpanWithAnEndOnTopOfTheOtherIsNotDrawn() {
		WireProfile profile = new WireProfile();
		assertFalse(profile.rebuild(0.0, 0.0, 0.0, ABC_SAG));
		assertEquals(0, profile.segments());

		assertFalse(profile.rebuild(0.05, 0.05, 0.0, ABC_SAG));
	}

	@Test
	void anythingThatIsNotANumberIsRefusedRatherThanDrawn() {
		WireProfile profile = new WireProfile();
		assertFalse(profile.rebuild(Double.NaN, 0.0, 10.0, ABC_SAG));
		assertFalse(profile.rebuild(Double.POSITIVE_INFINITY, 0.0, 10.0, ABC_SAG));
		assertEquals(0, profile.segments());
	}

	@Test
	void aShortHangingSpanStillHasAtLeastTwoSegments() {
		WireProfile profile = new WireProfile();
		assertTrue(profile.rebuild(0.5, 0.0, 0.0, 0.2));

		assertFalse(profile.straight());
		assertTrue(profile.segments() >= WireProfile.MIN_SEGMENTS);
	}

	@Test
	void anAbsurdlyLongSpanIsCappedNotAllocated() {
		WireProfile profile = new WireProfile();
		assertTrue(profile.rebuild(1.0e7, 0.0, 0.0, 0.05));

		assertEquals(WireProfile.MAX_SEGMENTS, profile.segments());
		assertEquals(1.0e7f, profile.x(WireProfile.MAX_SEGMENTS), 1.0f);
	}

	@Test
	void theBufferIsReusedWhenTheNextSpanIsShorter() {
		WireProfile profile = new WireProfile();
		assertTrue(profile.rebuild(160.0, 0.0, 0.0, HV_SAG));
		int long_ = profile.segments();

		assertTrue(profile.rebuild(10.0, 0.0, 0.0, HV_SAG));
		assertTrue(profile.segments() < long_);
		assertEquals(10.0f, profile.x(profile.segments()), 0.0f);
	}
}
