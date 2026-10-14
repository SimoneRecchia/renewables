package io.github.simonerecchia.renewables.wind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.WorldConditions;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.catalog.TurbineCatalog;
import io.github.simonerecchia.renewables.wind.TurbineDynamics.YawStep;

import org.junit.jupiter.api.Test;

/** What a turbine decides each tick: yaw, alignment, cut-out, power under the brake and the limit, and rotor speed. */
class TurbineDynamicsTest {
	private static final double RHO = WorldConditions.REFERENCE_AIR_DENSITY;
	private static final TurbineSpec C130 = TurbineCatalog.C130_40;
	private static final TurbineSpec SW10 = TurbineCatalog.SW_10;

	// ---- angles ----

	@Test
	void shortestAngleGoesTheShortWayRound() {
		assertEquals(10.0, TurbineDynamics.shortestAngle(350.0, 0.0), 1e-9);
		assertEquals(-10.0, TurbineDynamics.shortestAngle(0.0, 350.0), 1e-9);
		assertEquals(180.0, TurbineDynamics.shortestAngle(0.0, 180.0), 1e-9);
		assertEquals(180.0, TurbineDynamics.shortestAngle(180.0, 0.0), 1e-9);
		assertEquals(0.0, TurbineDynamics.shortestAngle(720.0, 0.0), 1e-9);
		assertEquals(-90.0, TurbineDynamics.shortestAngle(-90.0 + 90.0, -90.0), 1e-9);
	}

	@Test
	void wrapsKeepTheirRanges() {
		assertEquals(-170.0, TurbineDynamics.wrapSigned(190.0), 1e-9);
		assertEquals(180.0, TurbineDynamics.wrapSigned(-180.0), 1e-9);
		assertEquals(180.0, TurbineDynamics.wrapSigned(180.0), 1e-9);
		assertEquals(350.0, TurbineDynamics.wrapCompass(-10.0), 1e-9);
		assertEquals(0.0, TurbineDynamics.wrapCompass(360.0), 1e-9);
	}

	@Test
	void rotorFacesIntoTheWind() {
		// the heading of a model turned by r is (sin r, cos r) in (x, z); the air is going (cos d, sin d)
		for (double d = 0.0; d < 360.0; d += 15.0) {
			double r = Math.toRadians(TurbineDynamics.modelRotationDegrees(d));
			assertEquals(-Math.cos(Math.toRadians(d)), Math.sin(r), 1e-9, "x at " + d);
			assertEquals(-Math.sin(Math.toRadians(d)), Math.cos(r), 1e-9, "z at " + d);
		}
	}

	@Test
	void specificationExamplesOfTheConversion() {
		// air towards east: the rotor looks west, a rotation of -90 (= 270)
		assertEquals(-90.0, TurbineDynamics.modelRotationDegrees(0.0), 1e-9);
		// air towards south: the rotor looks north, which is the model turned 180
		assertEquals(180.0, TurbineDynamics.modelRotationDegrees(90.0), 1e-9);
		// air towards west: the rotor looks east, 90
		assertEquals(90.0, TurbineDynamics.modelRotationDegrees(180.0), 1e-9);
		// air towards north: the rotor looks south, no rotation at all
		assertEquals(0.0, TurbineDynamics.modelRotationDegrees(270.0), 1e-9);
	}

	@Test
	void conversionIsItsOwnInverse() {
		for (double d = -179.0; d <= 180.0; d += 17.0) {
			double model = TurbineDynamics.modelRotationDegrees(d);
			assertEquals(0.0, TurbineDynamics.shortestAngle(d, TurbineDynamics.windYawOfModelRotation(model)), 1e-9, "yaw " + d);
		}
	}

	@Test
	void aMachineStartsAlignedWithTheWindItsBlockFacingImplies() {
		// the block's own rotation is 0 for south, 90 east, 180 north, 270 west; the yaw in the wind's system is
		// the heading of the air that rotor would face, so that drawing it back gives the same rotation
		assertEquals(-90.0, TurbineDynamics.windYawOfModelRotation(0.0), 1e-9);
		assertEquals(180.0, TurbineDynamics.windYawOfModelRotation(90.0), 1e-9);
		assertEquals(90.0, TurbineDynamics.windYawOfModelRotation(180.0), 1e-9);
		assertEquals(0.0, TurbineDynamics.windYawOfModelRotation(270.0), 1e-9);
	}

	// ---- yaw ----

	@Test
	void yawStaysPutInsideTheDeadBand() {
		YawStep step = TurbineDynamics.stepYaw(10.0, 17.5);
		assertEquals(10.0, step.yaw(), 1e-9);
		assertFalse(step.yawing());
		assertEquals(0.0, step.twist(), 1e-9);
	}

	@Test
	void yawTurnsFiveDegreesASecondOutsideIt() {
		YawStep step = TurbineDynamics.stepYaw(10.0, 17.6);
		assertEquals(10.25, step.yaw(), 1e-9);
		assertTrue(step.yawing());
		assertEquals(0.25, step.twist(), 1e-9);

		YawStep back = TurbineDynamics.stepYaw(10.0, -30.0);
		assertEquals(9.75, back.yaw(), 1e-9);
		assertEquals(-0.25, back.twist(), 1e-9);
	}

	@Test
	void yawTakesTheShortWayAcrossTheSeam() {
		// from 170 to -170 is twenty degrees through 180, not three hundred and forty back
		YawStep step = TurbineDynamics.stepYaw(170.0, -170.0);
		assertEquals(170.25, step.yaw(), 1e-9);
		YawStep over = TurbineDynamics.stepYaw(179.9, -170.0);
		assertEquals(-179.85, over.yaw(), 1e-9);
	}

	@Test
	void yawConvergesOnTheWindAndStopsAtTheEdgeOfTheBand() {
		double yaw = 0.0;
		double twist = 0.0;
		int ticks = 0;
		while (ticks < 10_000) {
			YawStep step = TurbineDynamics.stepYaw(yaw, 120.0);
			if (!step.yawing()) break;

			yaw = step.yaw();
			twist += step.twist();
			ticks++;
		}

		// it stops when it is within 7.5 degrees: after turning 112.5 degrees at a quarter of a degree a tick
		assertEquals(450, ticks, 1);
		assertEquals(112.5, yaw, 0.26);
		assertEquals(yaw, twist, 1e-6);
	}

	// ---- alignment ----

	@Test
	void alignmentIsCosineCubed() {
		assertEquals(1.0, TurbineDynamics.alignment(0.0), 1e-12);
		assertEquals(Math.pow(Math.cos(Math.toRadians(30.0)), 3), TurbineDynamics.alignment(30.0), 1e-12);
		assertEquals(TurbineDynamics.alignment(30.0), TurbineDynamics.alignment(-30.0), 1e-12);
		assertEquals(0.0, TurbineDynamics.alignment(90.0), 1e-12);
		assertEquals(0.0, TurbineDynamics.alignment(135.0), 1e-12);
	}

	@Test
	void deadBandCostsAtMostTwoAndAHalfPercent() {
		assertEquals(0.025, 1.0 - TurbineDynamics.alignment(TurbineDynamics.YAW_DEAD_BAND), 0.0005);
	}

	/** The rotor sees v cos(gamma), so below rated, where the curve is cubic, the power is cos cubed: 2.5 %, not 7.4. */
	@Test
	void theRotorWorksWithTheWindSquareToIt() {
		assertEquals(10.0 * Math.cos(Math.toRadians(30.0)), TurbineDynamics.effectiveWind(10.0, 30.0), 1e-12);
		assertEquals(TurbineDynamics.effectiveWind(10.0, 30.0), TurbineDynamics.effectiveWind(10.0, -30.0), 1e-12);
		assertEquals(0.0, TurbineDynamics.effectiveWind(10.0, 90.0), 1e-12);
		assertEquals(0.0, TurbineDynamics.effectiveWind(10.0, 135.0), 1e-12);

		double off = TurbineDynamics.YAW_DEAD_BAND;
		double ratio = Math.pow(TurbineDynamics.effectiveWind(8.0, off) / 8.0, 3);
		assertEquals(TurbineDynamics.alignment(off), ratio, 1e-12);
		assertEquals(0.0254, 1.0 - ratio, 0.0001);
	}

	// ---- cut-out ----

	@Test
	void cutOutTripsOnTheMeanAtTheLimit() {
		assertFalse(TurbineDynamics.updateCutOut(false, 24.9, 25.0, C130));
		assertTrue(TurbineDynamics.updateCutOut(false, 25.0, 25.0, C130));
		assertTrue(TurbineDynamics.updateCutOut(false, 30.0, 31.0, C130));
	}

	@Test
	void cutOutTripsOnAGustOneAndAFifthOverTheLimit() {
		assertFalse(TurbineDynamics.updateCutOut(false, 20.0, 29.9, C130));
		assertTrue(TurbineDynamics.updateCutOut(false, 20.0, 30.0, C130));
	}

	@Test
	void cutOutHoldsUntilTheMeanIsWellDown() {
		// C130: released at 22.0, because 25 - 3 = 22 is under the storm onset of 22.5
		assertEquals(22.0, TurbineDynamics.cutOutReleaseSpeed(C130), 1e-9);
		assertTrue(TurbineDynamics.updateCutOut(true, 22.1, 22.1, C130));
		assertTrue(TurbineDynamics.updateCutOut(true, 24.0, 24.0, C130));
		assertFalse(TurbineDynamics.updateCutOut(true, 22.0, 22.0, C130));
		assertFalse(TurbineDynamics.updateCutOut(true, 10.0, 11.0, C130));
	}

	@Test
	void releaseSpeedOfEveryModel() {
		assertEquals(22.0, TurbineDynamics.cutOutReleaseSpeed(SW10), 1e-9);
		assertEquals(22.0, TurbineDynamics.cutOutReleaseSpeed(TurbineCatalog.C52_085), 1e-9);
		assertEquals(22.0, TurbineDynamics.cutOutReleaseSpeed(TurbineCatalog.C90_30), 1e-9);
		assertEquals(22.0, TurbineDynamics.cutOutReleaseSpeed(TurbineCatalog.C112_30), 1e-9);
	}

	@Test
	void aGustStillTooHighTripsAgainRightAfterARelease() {
		// the mean alone allows a release, but the gust is over the trip: the latch stays on
		assertTrue(TurbineDynamics.updateCutOut(true, 18.0, 31.0, C130));
		assertTrue(TurbineDynamics.updateCutOut(false, 18.0, 31.0, C130));
	}

	@Test
	void hysteresisCycle() {
		boolean latched = false;
		double[] means = {10.0, 24.0, 25.5, 24.0, 22.5, 22.1, 21.9, 15.0};
		boolean[] expected = {false, false, true, true, true, true, false, false};
		for (int i = 0; i < means.length; i++) {
			// no gust above the mean for this test, which is about the mean's own band
			latched = TurbineDynamics.updateCutOut(latched, means[i], means[i], C130);
			assertEquals(expected[i], latched, "mean " + means[i]);
		}
	}

	// ---- power ----

	@Test
	void brakeMeansNothingAtAll() {
		assertEquals(0.0, TurbineDynamics.uncappedKw(true, 15.0, RHO, C130), 1e-9);
		assertEquals(0.0, TurbineDynamics.producedKw(true, 3000.0, 4000.0), 1e-9);
	}

	@Test
	void runningMachineFollowsTheCurveUnderTheLimit() {
		double offered = TurbineDynamics.uncappedKw(false, 10.0, RHO, C130);
		assertEquals(3284.0, offered, 1.0);
		assertEquals(offered, TurbineDynamics.producedKw(false, offered, 4000.0), 1e-9);
		assertEquals(1000.0, TurbineDynamics.producedKw(false, offered, 1000.0), 1e-9);
	}

	@Test
	void aLimitOfZeroProducesNothingButDoesNotBrake() {
		double offered = TurbineDynamics.uncappedKw(false, 10.0, RHO, C130);
		assertEquals(0.0, TurbineDynamics.producedKw(false, offered, 0.0), 1e-9);
		assertEquals(0.0, TurbineDynamics.producedKw(false, offered, -50.0), 1e-9);
		assertTrue(offered > 0.0);
	}

	@Test
	void negativeAlignedWindIsReadAsCalm() {
		assertEquals(0.0, TurbineDynamics.uncappedKw(false, -3.0, RHO, C130), 1e-9);
	}

	@Test
	void powerLimitedFlag() {
		// braked, over the rated wind, or giving up some of what is on offer
		assertTrue(TurbineDynamics.powerLimited(true, 5.0, 0.0, 0.0, C130));
		assertTrue(TurbineDynamics.powerLimited(false, 14.0, 3979.0, 3979.0, C130));
		assertTrue(TurbineDynamics.powerLimited(false, 8.0, 1929.0, 1000.0, C130));
		assertFalse(TurbineDynamics.powerLimited(false, 8.0, 1929.0, 1929.0, C130));
	}

	@Test
	void surgingNeedsTurbulenceARunningMachineAndWindUnderTheCutOut() {
		assertTrue(TurbineDynamics.surging(0.22, 10.0, false, C130));
		assertFalse(TurbineDynamics.surging(0.21, 10.0, false, C130));
		assertFalse(TurbineDynamics.surging(0.30, 25.0, false, C130));
		assertFalse(TurbineDynamics.surging(0.30, 10.0, true, C130));
	}

	// ---- the rotor ----

	@Test
	void parkedRotorHasNoTarget() {
		assertEquals(0.0, TurbineDynamics.targetDegreesPerTick(true, false, 12.0, 4000.0, 3800.0, C130), 1e-9);
	}

	@Test
	void workingRotorHoldsTipSpeedRatioEightWithinItsRange() {
		// 8 * 10 m/s * 60 / (pi * 130 m) = 11.75 rpm, inside 7.9..14.0
		double rpm = TurbineDynamics.rpmOf(TurbineDynamics.targetDegreesPerTick(false, false, 10.0, 4000.0, 3284.0, C130));
		assertEquals(8.0 * 10.0 * 60.0 / (Math.PI * 130.0), rpm, 1e-6);

		// at 20 m/s it would be 23.5 and is held at the maximum
		double fast = TurbineDynamics.rpmOf(TurbineDynamics.targetDegreesPerTick(false, false, 20.0, 4000.0, 4000.0, C130));
		assertEquals(C130.maxRotorRpm(), fast, 1e-9);

		// just over the cut-in it is held at the minimum
		double slow = TurbineDynamics.rpmOf(TurbineDynamics.targetDegreesPerTick(false, false, 3.1, 4000.0, 100.0, C130));
		assertEquals(C130.minRotorRpm(), slow, 1e-9);
	}

	@Test
	void cutOutRotorIdlesAtTipSpeedRatioTwoUnderACeiling() {
		// 2 * 30 * 60 / (pi * 130) = 8.8 rpm, but an idling rotor is held to 15% of the maximum
		double rpm = TurbineDynamics.rpmOf(TurbineDynamics.targetDegreesPerTick(false, true, 30.0, 4000.0, 0.0, C130));
		assertEquals(0.15 * C130.maxRotorRpm(), rpm, 1e-9);

		// a light wind idles slower than the ceiling
		double light = TurbineDynamics.rpmOf(TurbineDynamics.targetDegreesPerTick(false, true, 3.0, 4000.0, 0.0, C130));
		assertEquals(2.0 * 3.0 * 60.0 / (Math.PI * 130.0), light, 1e-6);
		assertEquals(0.0, TurbineDynamics.targetDegreesPerTick(false, true, 0.0, 4000.0, 0.0, C130), 1e-9);
	}

	@Test
	void machineCurtailedToNothingIdlesToo() {
		double idle = TurbineDynamics.targetDegreesPerTick(false, false, 10.0, 0.0, 3284.0, C130);
		assertEquals(TurbineDynamics.targetDegreesPerTick(false, true, 10.0, 4000.0, 0.0, C130) * 0.0 + C130.idlingRpmAt(10.0) * 0.3, idle, 1e-9);
	}

	@Test
	void aCurtailedRotorSlowsWithTheCubeRootOfThePower() {
		// at half the power the wind it is held at is 0.794 of the wind it sees
		double wind = 12.0;
		double uncapped = 3700.0;
		double held = TurbineDynamics.targetDegreesPerTick(false, false, wind, uncapped / 2.0, uncapped, C130);
		double expected = C130.rotorRpmAt(wind * Math.cbrt(0.5)) * 0.3;
		assertEquals(expected, held, 1e-9);
		assertTrue(held < TurbineDynamics.targetDegreesPerTick(false, false, wind, uncapped, uncapped, C130));
	}

	@Test
	void curtailmentThatPutsTheHeldWindUnderTheCutInIdles() {
		// 3.5 m/s is over the cut-in of 3.0, but a tenth of the power is a wind of 3.5 * 0.464 = 1.6
		double uncapped = 200.0;
		double held = TurbineDynamics.targetDegreesPerTick(false, false, 3.5, uncapped * 0.1, uncapped, C130);
		assertEquals(C130.idlingRpmAt(3.5 * Math.cbrt(0.1)) * 0.3, held, 1e-9);
	}

	@Test
	void speedClosesFasterWhenSlowingThanWhenSpeedingUp() {
		assertEquals(5.0 + 5.0 * 0.05, TurbineDynamics.lagSpeed(5.0, 10.0), 1e-9);
		assertEquals(10.0 - 5.0 * 0.12, TurbineDynamics.lagSpeed(10.0, 5.0), 1e-9);
	}

	@Test
	void aRotorWithNothingToTurnItStops() {
		assertEquals(0.0, TurbineDynamics.lagSpeed(0.01, 0.0), 1e-12);
		assertEquals(0.0, TurbineDynamics.lagSpeed(0.0, 0.0), 1e-12);
		// but not while it is still well above the threshold
		assertTrue(TurbineDynamics.lagSpeed(0.5, 0.0) > 0.0);
	}

	@Test
	void speedFromStandstillReachesTheTargetWithoutOvershoot() {
		double speed = 0.0;
		double target = 3.0;
		for (int tick = 0; tick < 400; tick++) {
			speed = TurbineDynamics.lagSpeed(speed, target);
			assertTrue(speed <= target);
		}

		assertEquals(target, speed, 1e-6);
	}

	@Test
	void brakedRotorFallsToAStopWithinSeconds() {
		double speed = 2.5;
		int ticks = 0;
		while (speed > 0.0 && ticks < 1000) {
			speed = TurbineDynamics.lagSpeed(speed, 0.0);
			ticks++;
		}

		assertEquals(0.0, speed, 0.0);
		assertTrue(ticks < 100, "took " + ticks);
	}

	// ---- the blades ----

	@Test
	void pitchOfAMachineAtEveryRegime() {
		// flat to rated, 0..25 to the storm onset, 25..60 to the cut-out, feathered under any brake
		assertEquals(0.0, TurbineDynamics.collectivePitch(false, 5.0, C130), 1e-9);
		assertEquals(0.0, TurbineDynamics.collectivePitch(false, C130.ratedSpeed(), C130), 1e-9);
		assertEquals(12.5, TurbineDynamics.collectivePitch(false, (C130.ratedSpeed() + C130.stormOnsetSpeed()) / 2.0, C130), 1e-9);
		assertEquals(25.0, TurbineDynamics.collectivePitch(false, C130.stormOnsetSpeed(), C130), 1e-9);
		assertEquals(42.5, TurbineDynamics.collectivePitch(false, (C130.stormOnsetSpeed() + C130.cutOutSpeed()) / 2.0, C130), 1e-9);
		assertEquals(90.0, TurbineDynamics.collectivePitch(true, 5.0, C130), 1e-9);
	}

	@Test
	void pitchOfAMachineWithoutStormControlHasNoStormRange() {
		// SW-10: the onset is the cut-out, so the regulating range runs all the way to it
		double nearlyThere = TurbineDynamics.collectivePitch(false, 24.99, SW10);
		assertEquals(25.0, nearlyThere, 0.05);
		assertTrue(TurbineDynamics.collectivePitch(false, 20.0, SW10) > 0.0);
		// and from the onset on it reads as the storm value
		assertEquals(60.0, TurbineDynamics.collectivePitch(false, 25.0, SW10), 1e-9);
	}
}
