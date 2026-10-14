package io.github.simonerecchia.renewables.api.power;



import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** One inverter, described the way an inverter datasheet describes one. */
public record InverterSpec(
		ResourceLocation id,
		/** Model designation. */
		String displayName,
		/** Continuous active power at the terminals, kW. */
		double acPowerKw,
		/** Apparent power ceiling, kVA. */
		double apparentPowerKva,
		/** Direct-current input the machine will accept, kW. */
		double maxDcPowerKw,
		int mpptCount,
		/** String terminals the machine actually has. */
		int stringInputs,
		/** Most direct current one tracker's terminals will carry together, in amps. */
		double maxCurrentPerMppt,
		/** Whether the machine has fused string terminals of its own. */
		boolean stringTerminals,
		/** Whether it has busbars a combiner box's output can be lugged onto. */
		boolean trunkTerminals,
		double mpptMinVolts,
		double mpptMaxVolts,
		double maxDcVolts,
		/** Voltage at which the machine wakes up and starts looking for a maximum power point. */
		double startupVolts,
		double peakEfficiency,
		/** European weighted efficiency as the datasheet prints it, which fixes how flat the curve is. */
		double ratedEuropeanEfficiency,
		/** Where on the load curve the peak sits, as a fraction of nameplate. */
		double peakLoadFraction,
		/** What the machine draws overnight, in watts. */
		double nightWatts,
		/** Line-to-line volts at the terminals. */
		double nominalAcVolts,
		double frequencyHz,
		/** How far the power factor can be pushed either side of unity. */
		double minPowerFactor,
		/** Ambient temperature above which the machine starts backing off, in Celsius. */
		double derateOnsetC,
		double maxAmbientC,
		Cooling cooling
) {
	/** The load points and weights of the European efficiency (EN 50530). */
	private static final double[] EURO_LOADS = {0.05, 0.10, 0.20, 0.30, 0.50, 1.00};
	private static final double[] EURO_WEIGHTS = {0.03, 0.06, 0.13, 0.10, 0.48, 0.20};
	/** And of the CEC weighted efficiency, which puts more of it near full load, as a sunnier climate does. */
	private static final double[] CEC_LOADS = {0.10, 0.20, 0.30, 0.50, 0.75, 1.00};
	private static final double[] CEC_WEIGHTS = {0.04, 0.05, 0.12, 0.21, 0.53, 0.05};

	public enum Cooling {
		/** No moving parts. */
		NATURAL("natural"),
		/** Temperature-controlled fans, which is what the cabinet temperature reading is for. */
		FAN("fan"),
		/** A duct and a blower, on a machine too big to cool through its own skin. */
		FORCED_AIR("forced_air");

		private final String key;

		Cooling(String key) {
			this.key = key;
		}

		public String key() {
			return key;
		}
	}

	public InverterSpec {
		if (acPowerKw <= 0.0) throw new IllegalArgumentException(id + ": rated power must be positive");
		if (apparentPowerKva < acPowerKw) throw new IllegalArgumentException(id + ": apparent power cannot be under the active nameplate");
		if (maxDcPowerKw < acPowerKw) throw new IllegalArgumentException(id + ": an inverter accepts at least its own nameplate in DC");
		if (mpptCount < 1) throw new IllegalArgumentException(id + ": an inverter has at least one maximum power point tracker");
		if (stringInputs < mpptCount) throw new IllegalArgumentException(id + ": every tracker needs at least one string terminal");
		if (maxCurrentPerMppt <= 0.0) throw new IllegalArgumentException(id + ": a tracker's terminals carry some current");
		if (!stringTerminals && !trunkTerminals) throw new IllegalArgumentException(id + ": direct current has to get in somehow");
		if (mpptMaxVolts <= mpptMinVolts) throw new IllegalArgumentException(id + ": invalid MPPT window");
		if (maxDcVolts < mpptMaxVolts) throw new IllegalArgumentException(id + ": the MPPT window has to fit inside the input rating");
		if (peakEfficiency <= 0.0 || peakEfficiency >= 1.0) throw new IllegalArgumentException(id + ": efficiency is a fraction under one");
		if (ratedEuropeanEfficiency <= 0.0 || ratedEuropeanEfficiency > peakEfficiency) {
			throw new IllegalArgumentException(id + ": a weighted efficiency cannot beat the peak");
		}
		if (peakLoadFraction <= 0.0 || peakLoadFraction > 1.0) throw new IllegalArgumentException(id + ": the peak sits somewhere on the load curve");
		if (minPowerFactor <= 0.0 || minPowerFactor > 1.0) throw new IllegalArgumentException(id + ": power factor is a fraction");
		if (maxAmbientC <= derateOnsetC) throw new IllegalArgumentException(id + ": derating needs a band to happen over");
	}

	// ---- the efficiency curve ----

	/**
	 * The loss model of Schmidt and Sauer, in fractions of nameplate at a load fraction L:
	 * {@code p0 + p1 L + p2 L^2} - what runs whatever passes (control, fans, the bridge's magnetising), what goes with
	 * the current (switching, diode drops) and what goes with its square (conduction).
	 *
	 * The datasheet fixes all three: the peak sits where {@code p0 = p2 L^2}, its height fixes {@code p1 + 2 p2 L}, and
	 * the European efficiency fixes how the rest is shared between the linear and the square term.  Found by bisection
	 * on p2, between a flat curve (p2 = 0, which has the peak's efficiency everywhere) and one with no linear term,
	 * the lowest European figure a curve with this peak can have.
	 *
	 * @return {p0, p1, p2}
	 */
	public double[] lossCoefficients() {
		double total = 1.0 / peakEfficiency - 1.0;
		double low = 0.0;
		double high = total / (2.0 * peakLoadFraction);
		if (weighted(EURO_LOADS, EURO_WEIGHTS, coefficients(high)) >= ratedEuropeanEfficiency) return coefficients(high);

		for (int i = 0; i < 60; i++) {
			double middle = (low + high) / 2.0;
			if (weighted(EURO_LOADS, EURO_WEIGHTS, coefficients(middle)) > ratedEuropeanEfficiency) {
				low = middle;
			} else {
				high = middle;
			}
		}

		return coefficients((low + high) / 2.0);
	}

	/** The three coefficients for a given square term, holding the peak where and as high as the datasheet has it. */
	private double[] coefficients(double p2) {
		double total = 1.0 / peakEfficiency - 1.0;
		return new double[] {p2 * peakLoadFraction * peakLoadFraction, Math.max(0.0, total - 2.0 * p2 * peakLoadFraction), p2};
	}

	private static double efficiency(double load, double[] p) {
		if (load <= 0.0) return 0.0;

		return load / (load + p[0] + p[1] * load + p[2] * load * load);
	}

	private static double weighted(double[] loads, double[] weights, double[] p) {
		double total = 0.0;
		for (int i = 0; i < loads.length; i++) {
			total += weights[i] * efficiency(loads[i], p);
		}

		return total;
	}

	/** Fixed loss, as a fraction of nameplate: everything that runs whether power is passing or not. */
	public double noLoadLossFraction() {
		return lossCoefficients()[0];
	}

	/** All the losses at a load fraction, as a fraction of nameplate. */
	public double lossFractionAt(double loadFraction) {
		double load = Math.max(0.0, Math.min(loadFraction, 1.0));
		double[] p = lossCoefficients();
		return p[0] + p[1] * load + p[2] * load * load;
	}

	/** Efficiency at a fraction of nameplate output. */
	public double efficiencyAt(double loadFraction) {
		if (loadFraction <= 0.0) return 0.0;

		return efficiency(Math.min(loadFraction, 1.0), lossCoefficients());
	}

	/** European efficiency: the curve above, averaged at the six EN 50530 load points.  The datasheet's, by construction. */
	public double europeanEfficiency() {
		return weighted(EURO_LOADS, EURO_WEIGHTS, lossCoefficients());
	}

	/** CEC weighted efficiency of the same curve. */
	public double cecEfficiency() {
		return weighted(CEC_LOADS, CEC_WEIGHTS, lossCoefficients());
	}

	// ---- what comes out ----

	/** What the machine would put out if nothing were holding it back, in kW. */
	public double unclippedAcKw(double dcKw) {
		if (dcKw <= 0.0) return 0.0;

		double estimate = dcKw * peakEfficiency;
		double refined = dcKw * efficiencyAt(estimate / acPowerKw);
		return dcKw * efficiencyAt(refined / acPowerKw);
	}

	/** Active power at the terminals, held under both the nameplate and whatever the heat allows. */
	public double acPowerFromDcKw(double dcKw, double thermalLimitKw) {
		return Math.min(unclippedAcKw(dcKw), Math.min(acPowerKw, Math.max(0.0, thermalLimitKw)));
	}

	/** Whether the array is offering more than the machine can pass. */
	public boolean clipping(double dcKw, double thermalLimitKw) {
		if (dcKw <= 0.0) return false;

		return unclippedAcKw(dcKw) > Math.min(acPowerKw, Math.max(0.0, thermalLimitKw)) + 1.0e-6;
	}

	/**
	 * Fraction of nameplate the machine will still pass at the top of its ambient range.  Real machines lose 0.7 to
	 * 1.8 % a kelvin past their onset: an SG110CX 110 kVA at 45 °C and 100 at 50, an SG350HX 352 at 30 and 295 at
	 * 50, a Sunny Central 2500 at 35 and 2250 at 50, which a straight line to three quarters at 60 °C meets.
	 */
	private static final double DERATED_FRACTION_AT_LIMIT = 0.75;

	/** The output ceiling at a given ambient temperature, in kW. */
	public double thermalLimitKw(double ambientC) {
		if (ambientC <= derateOnsetC) return acPowerKw;
		if (ambientC >= maxAmbientC) return acPowerKw * DERATED_FRACTION_AT_LIMIT;

		double through = (ambientC - derateOnsetC) / (maxAmbientC - derateOnsetC);
		return acPowerKw * (1.0 - through * (1.0 - DERATED_FRACTION_AT_LIMIT));
	}

	/** Whether the machine is holding itself back for heat rather than for anything the array did. */
	public boolean derating(double ambientC) {
		return ambientC > derateOnsetC;
	}

	/** Cabinet temperature at a given ambient and load. */
	public double cabinetTemperature(double ambientC, double loadFraction) {
		double riseAtFullLoad = switch (cooling) {
			case NATURAL -> 45.0;
			case FAN -> 30.0;
			case FORCED_AIR -> 25.0;
		};

		return ambientC + riseAtFullLoad * lossFractionAt(loadFraction) / Math.max(1.0e-9, lossFractionAt(1.0));
	}

	/** Nominal DC-to-AC ratio the machine is sold at: what an array this inverter is meant for looks like. */
	public double nominalDcAcRatio() {
		return maxDcPowerKw / acPowerKw;
	}

	/** String terminals on one tracker: the terminal count shared out */
	public int inputsPerMppt() {
		return Math.max(1, stringInputs / mpptCount);
	}

	/** How many strings of a given current one tracker will actually take. */
	public int stringsPerMppt(double stringCurrentAmps) {
		if (stringCurrentAmps <= 0.0) return inputsPerMppt();

		return Math.max(1, Math.min(inputsPerMppt(), (int) Math.floor(maxCurrentPerMppt / stringCurrentAmps)));
	}

	/** Strings of a given current the whole machine will take, across all its trackers. */
	public int stringCapacity(double stringCurrentAmps) {
		return mpptCount * stringsPerMppt(stringCurrentAmps);
	}

	/** Whether a string at this voltage is one the machine can actually track. */
	public boolean withinMpptWindow(double volts) {
		return volts >= mpptMinVolts && volts <= mpptMaxVolts;
	}

	// ---- the AC side ----

	/** Apparent power for a given active output and power factor, kVA. */
	public double apparentKva(double activeKw, double powerFactor) {
		double pf = Mth.clamp(Math.abs(powerFactor), minPowerFactor, 1.0);
		return Math.min(apparentPowerKva, Math.max(0.0, activeKw) / pf);
	}

	/** The most active power the machine can deliver at a power factor: its apparent-power rating times it, kW. */
	public double activePowerCeilingKw(double powerFactor) {
		return apparentPowerKva * Mth.clamp(Math.abs(powerFactor), minPowerFactor, 1.0);
	}

	/** Reactive power that a given active output at a given power factor implies, kvar. */
	public double reactiveKvar(double activeKw, double powerFactor) {
		double kva = apparentKva(activeKw, powerFactor);
		double squared = kva * kva - activeKw * activeKw;
		return squared <= 0.0 ? 0.0 : Math.sqrt(squared);
	}

	/** Phase current for a given apparent power, in amps. */
	public double phaseCurrentA(double kva) {
		if (nominalAcVolts <= 0.0) return 0.0;

		return kva * 1000.0 / (Math.sqrt(3.0) * nominalAcVolts);
	}

	/** Line-to-neutral volts, which is what a single-phase reading off a three-phase machine is. */
	public double phaseVolts() {
		return nominalAcVolts / Math.sqrt(3.0);
	}

	/** Night self-consumption in kW, as a negative output. */
	public double nightDrawKw() {
		return nightWatts / 1000.0;
	}

	/**
	 * How large the one cabinet model is drawn for this machine, about the middle of its footprint: a string
	 * inverter is a wall box, a central one a container.  The renderer scales by it and the wire anchor follows.
	 */
	public double renderScale() {
		if (acPowerKw >= 1000.0) return 1.0;
		if (acPowerKw <= 30.0) return 0.62;

		return 0.92;
	}
}
