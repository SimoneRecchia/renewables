package io.github.simonerecchia.renewables.telemetry;

import io.github.simonerecchia.renewables.api.power.Telemetry;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.api.power.TurbineTelemetry;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;

/**
 * Turns what the mod actually knows about a turbine into the signal set a real turbine's controller would publish.
 */
public final class TurbineTelemetrySimulator {
	/** Line-to-line volts, the usual LV level for a machine this size. */
	private static final double NOMINAL_VOLTAGE = 690.0;
	private static final double NOMINAL_FREQUENCY = 50.0;
	/** Reaches 63% of a step in roughly 25 seconds. */
	private static final double THERMAL_LAG = 0.002;
	/** The parking brake goes on once a held rotor is down to this, as a real one's does: never on a turning rotor. */
	private static final double PARKING_BRAKE_RPM = 1.0;
	/** Where the cooling water's rise across the generator is kept between samples: not a tag. */
	private static final String COOLANT_RISE = "coolantRise";
	/** Rise of the cooling water across an unloaded generator, which is what it starts from. */
	private static final double IDLE_COOLANT_RISE = 6.0;

	private final Map<String, Double> thermal = new HashMap<>();
	/** The game time of the last sample, so a lag steps by however many ticks have gone since. */
	private long lastSample = Long.MIN_VALUE;
	private long elapsed = 1;

	/** Everything the simulator needs for one tick. */
	public record Sample(
			double activePowerKw,
			double ratedPowerKw,
			double activePowerLimitKw,
			boolean powerLimited,
			double windSpeed,
			double alignedWindSpeed,
			double windDirection,
			double nacelleDir,
			double turbulence,
			double rotorDegreesPerTick,
			/** The collective pitch the machine is at, from {@code TurbineDynamics.collectivePitch}: the one it is drawn at. */
			double bladePitchDegrees,
			/** Rotor is held stopped, whatever the reason: wind cut-out or a command. */
			boolean braked,
			boolean windCutOut,
			boolean stoppedByComputer,
			boolean stoppedByPlayer,
			boolean stoppedByRedstone,
			boolean yawing,
			double ambientTempC,
			/** Air pressure at the site, in hPa. */
			double pressureHpa,
			boolean raining,
			boolean thundering,
			long gameTime,
			int seed,
			double yawCableTwist,
			/** Step-up to synchronous speed, derived per model: a slow wide rotor needs a taller one. */
			double gearboxRatio
	) {
	}

	public Telemetry.Snapshot sample(Sample s) {
		elapsed = lastSample == Long.MIN_VALUE ? 1 : Math.max(1, s.gameTime() - lastSample);
		lastSample = s.gameTime();

		double rated = s.ratedPowerKw() > 0.0 ? s.ratedPowerKw() : 1.0;
		double load = Mth.clamp(s.activePowerKw() / rated, 0.0, 1.0);
		double ambient = s.ambientTempC();
		boolean spinning = s.rotorDegreesPerTick() > 0.05;

		double rotorRpm = s.rotorDegreesPerTick() / TurbineSpec.DEGREES_PER_TICK_PER_RPM;
		double pitch = clampPitch(s.bladePitchDegrees());
		double pitchActivity = s.braked() || pitch > 0.5 ? 1.0 : 0.15;

		Telemetry.Builder out = Telemetry.builder();

		// ---- measured ----
		out.put(TurbineTelemetry.WIND_SPEED, s.windSpeed());
		// both headings are normalised to a 0..360 compass.
		out.put(TurbineTelemetry.WIND_DIR, compass(s.windDirection()));
		out.put(TurbineTelemetry.NACELLE_DIR, compass(s.nacelleDir()));
		out.put(TurbineTelemetry.ROTOR_RPM, rotorRpm);
		out.put(TurbineTelemetry.ACTIVE_POWER, s.activePowerKw());
		out.put(TurbineTelemetry.ACTIVE_POWER_LIMIT, s.activePowerLimitKw());
		out.put(TurbineTelemetry.POWER_LIMITATION_ACTIVE, s.powerLimited());
		out.put(TurbineTelemetry.AMBIENT_TEMP, ambient);
		out.put(TurbineTelemetry.TURBULENCE, s.turbulence());
		out.put(TurbineTelemetry.YAW_CABLE_TWIST, s.yawCableTwist());
		// the reasons are kept apart from the fact: a program that shuts a turbine down
		// needs to tell its own command from the machine protecting itself in a gale
		out.put(TurbineTelemetry.RUNNING, !s.braked());
		out.put(TurbineTelemetry.WIND_CUT_OUT, s.windCutOut());
		out.put(TurbineTelemetry.STOPPED_BY_COMPUTER, s.stoppedByComputer());
		out.put(TurbineTelemetry.STOPPED_BY_PLAYER, s.stoppedByPlayer());
		out.put(TurbineTelemetry.STOPPED_BY_REDSTONE, s.stoppedByRedstone());

		// ---- derived ----
		out.put(TurbineTelemetry.GENERATOR_RPM, rotorRpm * s.gearboxRatio());

		// power factor improves as the machine loads up, which is how an induction
		double powerFactor = s.activePowerKw() <= 0.01 ? 0.0 : 0.90 + 0.08 * load;
		double apparent = powerFactor <= 0.0 ? 0.0 : s.activePowerKw() / powerFactor;
		double reactive = Math.sqrt(Math.max(0.0, apparent * apparent - s.activePowerKw() * s.activePowerKw()));
		out.put(TurbineTelemetry.POWER_FACTOR, powerFactor);
		out.put(TurbineTelemetry.APPARENT_POWER, apparent);
		out.put(TurbineTelemetry.REACTIVE_POWER, reactive);
		out.put(TurbineTelemetry.FREQUENCY, NOMINAL_FREQUENCY + wobble(s, 397.0, 0.04));

		// terminal volts sag slightly under load
		double busVoltage = NOMINAL_VOLTAGE * (1.0 - 0.012 * load);
		double v12 = busVoltage + wobble(s, 211.0, 1.8);
		double v23 = busVoltage + wobble(s, 233.0, 1.8) - 0.9;
		double v31 = busVoltage + wobble(s, 257.0, 1.8) + 0.6;
		out.put(TurbineTelemetry.V12, v12);
		out.put(TurbineTelemetry.V23, v23);
		out.put(TurbineTelemetry.V31, v31);

		// the per-phase variation is a fraction of the current rather than an offset on
		double lineCurrent = busVoltage <= 0.0 ? 0.0 : apparent * 1000.0 / (Math.sqrt(3.0) * busVoltage);
		out.put(TurbineTelemetry.I1, lineCurrent * (1.004 + wobble(s, 197.0, 0.002)));
		out.put(TurbineTelemetry.I2, lineCurrent * (0.994 + wobble(s, 223.0, 0.002)));
		out.put(TurbineTelemetry.I3, lineCurrent * (1.002 + wobble(s, 241.0, 0.002)));

		// the three blades never track the collective demand perfectly, but they cannot
		out.put(TurbineTelemetry.BLADE_PITCH_ANGLE, pitch);
		out.put(TurbineTelemetry.BLADE_PITCH_ANGLE_1, clampPitch(pitch + wobble(s, 89.0, 0.25)));
		out.put(TurbineTelemetry.BLADE_PITCH_ANGLE_2, clampPitch(pitch + wobble(s, 97.0, 0.25) - 0.1));
		out.put(TurbineTelemetry.BLADE_PITCH_ANGLE_3, clampPitch(pitch + wobble(s, 101.0, 0.25) + 0.15));

		// the barometer reads the weather model's own field now, rather than a figure worked
		out.put(TurbineTelemetry.AIR_PRESSURE, s.pressureHpa() + wobble(s, 1201.0, 0.6));

		// ---- simulated ----
		out.put(TurbineTelemetry.GEARBOX_OIL_TEMP, lag(TurbineTelemetry.GEARBOX_OIL_TEMP, ambient + 18.0 + 42.0 * load, ambient));
		out.put(TurbineTelemetry.GEARBOX_OIL_TEMP_SUMP, lag(TurbineTelemetry.GEARBOX_OIL_TEMP_SUMP, ambient + 12.0 + 34.0 * load, ambient));
		out.put(TurbineTelemetry.GEAR_BEAR_TEMP_GEN, lag(TurbineTelemetry.GEAR_BEAR_TEMP_GEN, ambient + 22.0 + 48.0 * load, ambient));
		out.put(TurbineTelemetry.GEAR_BEAR_TEMP_ROT, lag(TurbineTelemetry.GEAR_BEAR_TEMP_ROT, ambient + 20.0 + 44.0 * load, ambient));
		out.put(TurbineTelemetry.GEN_BEAR_TEMP_BS, lag(TurbineTelemetry.GEN_BEAR_TEMP_BS, ambient + 25.0 + 50.0 * load, ambient));
		out.put(TurbineTelemetry.GEN_BEAR_TEMP_D_END, lag(TurbineTelemetry.GEN_BEAR_TEMP_D_END, ambient + 24.0 + 47.0 * load, ambient));
		out.put(TurbineTelemetry.MAIN_BEAR_TEMP, lag(TurbineTelemetry.MAIN_BEAR_TEMP, ambient + 15.0 + 30.0 * load, ambient));

		// stator windings are the hottest thing in the nacelle
		double ratedApparent = rated / 0.98;
		double copperLoss = Mth.clamp(apparent / ratedApparent, 0.0, 1.0);
		copperLoss *= copperLoss;
		out.put(TurbineTelemetry.GENERATOR_L1_TEMP, lag(TurbineTelemetry.GENERATOR_L1_TEMP, ambient + 30.0 + 78.0 * copperLoss, ambient));
		out.put(TurbineTelemetry.GENERATOR_L2_TEMP, lag(TurbineTelemetry.GENERATOR_L2_TEMP, ambient + 30.0 + 80.0 * copperLoss, ambient));
		out.put(TurbineTelemetry.GENERATOR_L3_TEMP, lag(TurbineTelemetry.GENERATOR_L3_TEMP, ambient + 30.0 + 76.0 * copperLoss, ambient));

		// the outlet is the inlet plus what the generator puts into the water: the rise is what lags, and it is never
		// negative, where an outlet lagging on its own started at the air and ran colder than the inlet for fourteen seconds
		double coolantInlet = lag(TurbineTelemetry.GEN_CW_TEMP_INLET, ambient + 8.0 + 15.0 * load, ambient);
		double coolantRise = lag(COOLANT_RISE, IDLE_COOLANT_RISE + 14.0 * load, IDLE_COOLANT_RISE);
		out.put(TurbineTelemetry.GEN_CW_TEMP_INLET, coolantInlet);
		out.put(TurbineTelemetry.GEN_CW_TEMP_OUTLET, coolantInlet + coolantRise);

		out.put(TurbineTelemetry.NACELLE_TEMP, lag(TurbineTelemetry.NACELLE_TEMP, ambient + 8.0 + 12.0 * load, ambient));
		out.put(TurbineTelemetry.HYD_OIL_TEMP, lag(TurbineTelemetry.HYD_OIL_TEMP, ambient + 10.0 + 20.0 * load, ambient));
		out.put(TurbineTelemetry.AIR_TEMP_CTRL_CAB, lag(TurbineTelemetry.AIR_TEMP_CTRL_CAB, ambient + 6.0 + 10.0 * load, ambient));
		out.put(TurbineTelemetry.AIR_TEMP_PWR_CAB_CTRL_FLD, lag(TurbineTelemetry.AIR_TEMP_PWR_CAB_CTRL_FLD, ambient + 9.0 + 16.0 * load, ambient));
		out.put(TurbineTelemetry.AIR_TEMP_PWR_CAB_PWR_FLD, lag(TurbineTelemetry.AIR_TEMP_PWR_CAB_PWR_FLD, ambient + 12.0 + 24.0 * load, ambient));
		out.put(TurbineTelemetry.AIR_TEMP_TOWER_BOTT, lag(TurbineTelemetry.AIR_TEMP_TOWER_BOTT, ambient + 3.0 + 4.0 * load, ambient));
		out.put(TurbineTelemetry.MV_TRAFO_TEMP_AREA_COIL, lag(TurbineTelemetry.MV_TRAFO_TEMP_AREA_COIL, ambient + 20.0 + 55.0 * copperLoss, ambient));

		out.put(TurbineTelemetry.PITCH_1_MOTOR_TEMP, lag(TurbineTelemetry.PITCH_1_MOTOR_TEMP, ambient + 5.0 + 9.0 * pitchActivity, ambient));
		out.put(TurbineTelemetry.PITCH_2_MOTOR_TEMP, lag(TurbineTelemetry.PITCH_2_MOTOR_TEMP, ambient + 5.0 + 8.0 * pitchActivity, ambient));
		out.put(TurbineTelemetry.PITCH_3_MOTOR_TEMP, lag(TurbineTelemetry.PITCH_3_MOTOR_TEMP, ambient + 5.0 + 9.5 * pitchActivity, ambient));
		out.put(TurbineTelemetry.PITCH_1_BOX_TEMP, lag(TurbineTelemetry.PITCH_1_BOX_TEMP, ambient + 4.0 + 6.0 * pitchActivity, ambient));
		out.put(TurbineTelemetry.PITCH_2_BOX_TEMP, lag(TurbineTelemetry.PITCH_2_BOX_TEMP, ambient + 4.0 + 5.5 * pitchActivity, ambient));
		out.put(TurbineTelemetry.PITCH_3_BOX_TEMP, lag(TurbineTelemetry.PITCH_3_BOX_TEMP, ambient + 4.0 + 6.5 * pitchActivity, ambient));

		// hydraulics respond in well under a tick, so these are not lagged
		out.put(TurbineTelemetry.GEARBOX_OIL_PRESS, spinning ? 2.0 + 1.5 * load + wobble(s, 73.0, 0.06) : 0.0);
		out.put(TurbineTelemetry.GEARBOX_OIL_PRESS_PUMP, spinning ? 4.5 + 2.0 * load + wobble(s, 79.0, 0.08) : 0.0);
		out.put(TurbineTelemetry.HYDR_SYSTEM_PRESS, 190.0 + 20.0 * load + wobble(s, 151.0, 1.2));
		// the parking brake holds a rotor that has been stopped and has run down; a rotor idling in a gale is feathered,
		// not braked, and turns
		boolean parked = s.stoppedByComputer() || s.stoppedByPlayer() || s.stoppedByRedstone();
		boolean brakeOn = parked && rotorRpm < PARKING_BRAKE_RPM;
		out.put(TurbineTelemetry.HYDR_MAIN_BRAKES_PRESS, brakeOn ? 178.0 + wobble(s, 167.0, 1.5) : 4.0 + wobble(s, 167.0, 0.3));
		out.put(TurbineTelemetry.YAW_H_ACCU_PRESS, 148.0 + 6.0 * load + wobble(s, 181.0, 1.0));
		// the yaw brakes clamp the nacelle at rest and are let down to a residual drag while the drives turn it
		out.put(TurbineTelemetry.YAW_HYDR_BRK_PRESS, s.yawing() ? 25.0 + wobble(s, 139.0, 1.4) : 140.0 + wobble(s, 139.0, 1.0));

		// turbulence shakes the machine harder than raw load does
		out.put(TurbineTelemetry.VIB_Y, 0.4 + 1.8 * load + 2.5 * s.turbulence() + wobble(s, 31.0, 0.12));
		out.put(TurbineTelemetry.VIB_Z, 0.3 + 1.5 * load + 2.0 * s.turbulence() + wobble(s, 37.0, 0.1));

		return out.build();
	}

	/** Any heading onto a 0..360 compass */
	private static double compass(double degrees) {
		return ((degrees % 360.0) + 360.0) % 360.0;
	}

	/** Fine pitch to full feather is the whole mechanical travel */
	private static double clampPitch(double degrees) {
		return Mth.clamp(degrees, 0.0, 90.0);
	}

	/**
	 * First-order lag toward {@code target}, stepped over the ticks since the last sample: a machine that publishes once a
	 * second warms exactly as one that publishes every tick.
	 */
	private double lag(String tag, double target, double seed) {
		Double current = thermal.get(tag);
		if (current == null) {
			thermal.put(tag, seed);
			return seed;
		}

		double next = target + (current - target) * Math.pow(1.0 - THERMAL_LAG, elapsed);
		thermal.put(tag, next);
		return next;
	}

	/** Smooth, repeatable variation. */
	private static double wobble(Sample s, double periodTicks, double amplitude) {
		double phase = (s.gameTime() + s.seed()) * (2.0 * Math.PI / periodTicks);
		return Math.sin(phase) * amplitude;
	}
}
