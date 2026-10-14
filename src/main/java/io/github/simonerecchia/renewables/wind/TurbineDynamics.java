package io.github.simonerecchia.renewables.wind;

import io.github.simonerecchia.renewables.api.WorldConditions;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;

/**
 * What a turbine decides each tick, as pure functions of numbers: the yaw drive, the alignment loss, the wind
 * cut-out and its hysteresis, how much power the curve gives under the brake and the curtailment, and how fast
 * the rotor turns.
 *
 * Nothing here knows about blocks, levels or the network, so the whole of it can be checked without starting a
 * game, and the client can run the same functions on the values the server synced to decide how to animate.
 *
 * <b>Angles.</b> Wind direction and the yaw kept here are in one system: the heading the air is travelling
 * towards, 0 = towards +X (east), 90 = towards +Z (south).  A machine that sits exactly at the wind direction is
 * aligned.  The rotor of a real turbine looks <em>into</em> the wind, so what is drawn is the opposite heading;
 * {@link #modelRotationDegrees} does that one conversion, at the point of drawing.
 */
public final class TurbineDynamics {
	/** The nacelle does not chase a wind that is within this many degrees of where it already points. */
	public static final double YAW_DEAD_BAND = 7.5;
	/** The yaw drive's speed, in degrees per tick: five degrees a second. */
	public static final double YAW_RATE = 0.25;

	/** Rotor speed closes on a lower target this fast each tick (the brake and the wind both pull it down). */
	private static final double SLOW_DOWN_LAG = 0.12;
	/** and a higher one this fast (a rotor's inertia is why it takes a while to spin up). */
	private static final double SPEED_UP_LAG = 0.05;
	/** Below this a rotor that has nothing to turn it is simply stopped. */
	private static final double STOPPED_DEGREES_PER_TICK = 0.01;

	/** Turbulence at which the machine starts to disturb the network it is on. */
	public static final double SURGE_TURBULENCE = 0.22;
	/** Hysteresis of the cut-out: it releases this far under the cut-out speed (or at the storm onset, if lower). */
	private static final double CUT_OUT_RELEASE_MARGIN = 3.0;

	/** Gentlest pitch of the regulating range, and the top of it. */
	private static final double MAX_REGULATING_PITCH = 25.0;
	private static final double MAX_STORM_PITCH = 60.0;
	/** Fully feathered. */
	public static final double FEATHERED_PITCH = 90.0;

	private TurbineDynamics() {
	}

	// ---- angles ----

	/** The shortest way round from {@code from} to {@code to}, in (-180, 180]. */
	public static double shortestAngle(double from, double to) {
		double difference = (to - from) % 360.0;
		if (difference > 180.0) difference -= 360.0;
		if (difference <= -180.0) difference += 360.0;

		return difference;
	}

	/** An angle onto (-180, 180]. */
	public static double wrapSigned(double degrees) {
		double wrapped = degrees % 360.0;
		if (wrapped > 180.0) wrapped -= 360.0;
		if (wrapped <= -180.0) wrapped += 360.0;

		return wrapped;
	}

	/** An angle onto [0, 360). */
	public static double wrapCompass(double degrees) {
		double wrapped = degrees % 360.0;
		return wrapped < 0.0 ? wrapped + 360.0 : wrapped;
	}

	/**
	 * The rotation about Y to give the model (authored facing +Z) so that its rotor looks into a wind that is
	 * travelling towards {@code windDirection}.
	 *
	 * The front of a model turned by {@code r} degrees points along (sin r, cos r) in (x, z); the airflow is
	 * (cos d, sin d), and looking into it is its opposite, which gives r = -90 - d.  For an airflow towards east
	 * (d = 0) the rotor faces west: r = -90 = 270.
	 */
	public static double modelRotationDegrees(double windDirection) {
		return wrapSigned(-90.0 - windDirection);
	}

	/** The inverse of {@link #modelRotationDegrees}: the wind-system yaw of a machine drawn at that rotation. */
	public static double windYawOfModelRotation(double modelRotationDegrees) {
		return wrapSigned(-90.0 - modelRotationDegrees);
	}

	// ---- yaw ----

	/** What the yaw drive did this tick. */
	public record YawStep(double yaw, boolean yawing, double twist) {
	}

	/**
	 * Moves the nacelle towards the wind, if it is far enough off.
	 *
	 * Inside the dead band the drive stays put: a real one would hunt back and forth over every gust.  Outside it
	 * turns at {@link #YAW_RATE}, and the signed step is what winds the power cable up in the tower.
	 *
	 * @return the new yaw in (-180, 180], whether it moved, and the signed step it took
	 */
	public static YawStep stepYaw(double yaw, double windDirection) {
		double difference = shortestAngle(yaw, windDirection);
		if (Math.abs(difference) <= YAW_DEAD_BAND) return new YawStep(wrapSigned(yaw), false, 0.0);

		double step = Math.copySign(Math.min(Math.abs(difference), YAW_RATE), difference);
		return new YawStep(wrapSigned(yaw + step), true, step);
	}

	/**
	 * How much of its power a rotor that is {@code misalignment} degrees off the wind still makes: cos cubed,
	 * and none at all from side on.
	 */
	public static double alignment(double misalignment) {
		double off = Math.abs(misalignment);
		if (off >= 90.0) return 0.0;

		double cosine = Math.cos(Math.toRadians(off));
		return cosine * cosine * cosine;
	}

	/**
	 * The wind a rotor {@code misalignment} degrees off it actually works with: the component square to the rotor
	 * disc, {@code v cos(gamma)}.  The power curve cubes it, which is where {@link #alignment}'s cos cubed comes from;
	 * cubing an already-cubed factor would charge the dead band 7 % instead of 2.5.
	 */
	public static double effectiveWind(double wind, double misalignment) {
		double off = Math.abs(misalignment);
		if (off >= 90.0) return 0.0;

		return Math.max(0.0, wind) * Math.cos(Math.toRadians(off));
	}

	// ---- wind cut-out ----

	/** Wind below which a tripped machine starts again: the lower of the storm onset and three under the cut-out. */
	public static double cutOutReleaseSpeed(TurbineSpec spec) {
		return Math.min(spec.stormOnsetSpeed(), spec.cutOutSpeed() - CUT_OUT_RELEASE_MARGIN);
	}

	/**
	 * The cut-out latch after this tick.
	 *
	 * Trips on a mean at or over the cut-out, or on a three-second gust 1.2 times over it; once tripped it holds
	 * until the mean is down to {@link #cutOutReleaseSpeed} <em>and</em> the gust is down to 1.2 times that.  The gust
	 * needs its own band: with the release on the mean alone, a turbulent wind whose gust sits on the trip level while
	 * its mean is under the release would stop and restart the machine tick after tick.
	 */
	public static boolean updateCutOut(boolean latched, double meanWind, double gust, TurbineSpec spec) {
		if (meanWind >= spec.cutOutSpeed() || gust >= WorldConditions.GUST_TRIP_RATIO * spec.cutOutSpeed()) return true;

		double release = cutOutReleaseSpeed(spec);
		if (latched && meanWind <= release && gust < WorldConditions.GUST_TRIP_RATIO * release) return false;

		return latched;
	}

	// ---- power ----

	/**
	 * What the wind offers, in kW: the model's curve at the wind the rotor actually faces and the air density,
	 * or nothing at all with the brake on.  Not limited by curtailment.
	 */
	public static double uncappedKw(boolean braked, double alignedWind, double airDensity, TurbineSpec spec) {
		if (braked) return 0.0;

		return spec.powerWhileRunningKw(Math.max(0.0, alignedWind), airDensity);
	}

	/** What the machine produces: the offer, held to the curtailment setpoint, and nothing at all under the brake. */
	public static double producedKw(boolean braked, double uncappedKw, double limitKw) {
		if (braked) return 0.0;

		return Math.min(uncappedKw, Math.max(0.0, limitKw));
	}

	/**
	 * The same, held also to what the generator's rated torque can take out of the rotor at the speed it is actually
	 * turning ({@link TurbineSpec#torqueCeilingKw}): a rotor just released from the brake makes little until it has
	 * spun up, instead of its full output at a twentieth of its speed.
	 */
	public static double producedKw(boolean braked, double uncappedKw, double limitKw, double rotorRpm, TurbineSpec spec) {
		return Math.min(producedKw(braked, uncappedKw, limitKw), spec.torqueCeilingKw(rotorRpm));
	}

	/**
	 * Whether the output is being held below what the machine could do: braked, or in the storm-control
	 * region above rated wind, or under the curtailment.
	 */
	public static boolean powerLimited(boolean braked, double alignedWind, double uncappedKw, double producedKw, TurbineSpec spec) {
		return braked || alignedWind > spec.ratedSpeed() || uncappedKw > producedKw;
	}

	/** Whether the machine is putting a disturbance onto the network: buffeted, still running, and not braked. */
	public static boolean surging(double turbulence, double meanWind, boolean braked, TurbineSpec spec) {
		return turbulence >= SURGE_TURBULENCE && meanWind < spec.cutOutSpeed() && !braked;
	}

	// ---- the rotor ----

	/**
	 * The speed the rotor is being driven to, in degrees per tick.
	 *
	 * Parked (held by a command or by redstone): none.  Cut out, or curtailed to nothing: it idles with the
	 * blades feathered, slowly.  Otherwise it holds its tip-speed ratio, except that a machine asked for less than
	 * the wind offers sees a correspondingly lower wind (speed goes with the cube root of power), and one that
	 * thereby falls under its cut-in idles too.
	 */
	public static double targetDegreesPerTick(boolean parked, boolean cutOutLatched, double alignedWind, double limitKw,
			double uncappedKw, TurbineSpec spec) {
		if (parked) return 0.0;

		double wind = Math.max(0.0, alignedWind);
		double rpm;
		if (cutOutLatched || limitKw <= 0.0) {
			rpm = spec.idlingRpmAt(wind);
		} else {
			double held = uncappedKw > 0.0 && limitKw < uncappedKw ? wind * Math.cbrt(limitKw / uncappedKw) : wind;
			rpm = spec.rotorRpmAt(held);
		}

		return rpm * TurbineSpec.DEGREES_PER_TICK_PER_RPM;
	}

	/**
	 * One tick of the rotor's inertia: the speed closes on its target a fraction at a time, quickly when
	 * slowing and slowly when speeding up.  A rotor with nothing to drive it that has all but stopped stops.
	 */
	public static double lagSpeed(double current, double target) {
		double lag = target < current ? SLOW_DOWN_LAG : SPEED_UP_LAG;
		double next = current + (target - current) * lag;
		if (target == 0.0 && Math.abs(next) < STOPPED_DEGREES_PER_TICK) return 0.0;

		return next;
	}

	/** The rotor speed in rpm that a real speed in degrees per tick stands for. */
	public static double rpmOf(double degreesPerTick) {
		return degreesPerTick / TurbineSpec.DEGREES_PER_TICK_PER_RPM;
	}

	// ---- the blades ----

	/**
	 * The collective blade pitch, in degrees, that the power curve implies.
	 *
	 * Feathered when braked for any reason.  Flat until rated wind; climbing to 25 degrees by the storm onset to
	 * hold the plateau; then on to 60 by the cut-out as storm control sheds the output.
	 */
	public static double collectivePitch(boolean braked, double alignedWind, TurbineSpec spec) {
		if (braked) return FEATHERED_PITCH;

		double rated = spec.ratedSpeed();
		if (alignedWind <= rated) return 0.0;

		double onset = spec.stormOnsetSpeed();
		double regulatingSpan = onset - rated;
		if (alignedWind < onset) {
			if (regulatingSpan <= 0.0) return 0.0;

			return clamp01((alignedWind - rated) / regulatingSpan) * MAX_REGULATING_PITCH;
		}

		double stormSpan = spec.cutOutSpeed() - onset;
		if (stormSpan <= 0.0) return MAX_STORM_PITCH;

		return MAX_REGULATING_PITCH + clamp01((alignedWind - onset) / stormSpan) * (MAX_STORM_PITCH - MAX_REGULATING_PITCH);
	}

	/**
	 * The collective pitch with the curtailment in it.  A machine held under what the wind offers sheds the rest with
	 * its blades, as a real one does: it reported flat blades on a slowed rotor, 0° on a C130 held to 1000 of 2642 kW.
	 * Never less than the curve's own pitch; feathered when held to nothing.
	 */
	public static double collectivePitch(boolean braked, double alignedWind, double uncappedKw, double limitKw, TurbineSpec spec) {
		double curve = collectivePitch(braked, alignedWind, spec);
		if (braked || uncappedKw <= 0.0 || limitKw >= uncappedKw) return curve;

		double kept = Math.max(0.0, limitKw) / uncappedKw;
		double rated = spec.ratedSpeed();
		// over rated wind the regulating pitch is already shedding the rest of what flat blades would take
		if (alignedWind > rated) kept *= Math.pow(rated / alignedWind, 3);

		return Math.max(curve, sheddingPitch(kept, spec));
	}

	/**
	 * The pitch that keeps {@code kept} of what flat blades would take, on the regulating range's own scale: there, at a
	 * wind v over rated, flat blades would take (v / rated)^3 of the rated power and the pitch is 25° x (v - rated) / (onset
	 * - rated); this is the same pitch for the same share kept, so curtailment and regulation agree with each other.
	 */
	static double sheddingPitch(double kept, TurbineSpec spec) {
		if (kept <= 0.0) return FEATHERED_PITCH;
		if (kept >= 1.0) return 0.0;

		double rated = spec.ratedSpeed();
		double span = spec.stormOnsetSpeed() - rated;
		if (span <= 0.0) span = spec.cutOutSpeed() - rated;

		return Math.min(FEATHERED_PITCH, MAX_REGULATING_PITCH * rated * (Math.cbrt(1.0 / kept) - 1.0) / span);
	}

	private static double clamp01(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}
}
