package io.github.simonerecchia.renewables.api.power;



import io.github.simonerecchia.renewables.api.WorldConditions;

import net.minecraft.resources.ResourceLocation;

/** One class of overhead line conductor */
public record ConductorSpec(
		ResourceLocation id,
		String displayName,
		VoltageClass voltageClass,
		/** One sub-conductor's section, mm². */
		double crossSectionMm2,
		/** Highest voltage for equipment (Um) the line is built for, volts. */
		double maxSystemVolts,
		/** Continuous current of the whole phase, bundle and all, amps. */
		double ampacity,
		/** DC resistance of the whole phase at 20 °C, bundle and all, ohms per km. */
		double ohmsPerKm,
		int subConductors,
		double bundleSpacing,
		double radius,
		int colour,
		double sag,
		double maxSpan,
		int blocksPerItem
) {
	public ConductorSpec {
		if (crossSectionMm2 <= 0.0) throw new IllegalArgumentException(id + ": a conductor has a cross-section");
		if (subConductors < 1) throw new IllegalArgumentException(id + ": a phase has at least one wire in it");
		if (radius <= 0.0) throw new IllegalArgumentException(id + ": a conductor has a thickness");
		if (sag < 0.0) throw new IllegalArgumentException(id + ": a span does not dip upwards");
		if (maxSpan <= 0.0) throw new IllegalArgumentException(id + ": a span has a length");
		if (blocksPerItem < 1) throw new IllegalArgumentException(id + ": an item buys at least one block");
	}

	/**
	 * Which structures a conductor may be strung between, and the system it is part of.
	 *
	 * The nominal voltage is the IEC 60038 line-to-line figure the network runs at, which is what a line's
	 * current and so its loss follow from; {@link #maxSystemVolts} is the highest voltage for equipment (Um)
	 * the line is built for.
	 */
	public enum VoltageClass {
		/** Aerial bundled cable on poles: the street, 230/400 V. */
		LOW(400.0),
		/** Bare conductor on poles: the plant's own collector, 33 kV, the level its transformers step up to. */
		MEDIUM(33_000.0),
		/** Bundled ACSR on lattice towers: the transmission line, 400 kV. */
		HIGH(400_000.0);

		private final double nominalVolts;

		VoltageClass(double nominalVolts) {
			this.nominalVolts = nominalVolts;
		}

		/** Line-to-line volts the network of this class runs at. */
		public double nominalVolts() {
			return nominalVolts;
		}
	}

	/** How many items a span of this many blocks costs, rounded up: no free metre at the end. */
	public int cost(double blocks) {
		return Math.max(1, (int) Math.ceil(blocks / blocksPerItem));
	}

	/** One phase's resistance over a span this many blocks long, bundle and all, at 20 °C. */
	public double resistanceOhms(double blocks) {
		return ohmsPerKm * Math.max(0.0, blocks) * WorldConditions.METRES_PER_BLOCK / 1000.0;
	}

	/** The line-to-line volts a span of this conductor is worked at. */
	public double nominalVolts() {
		return voltageClass.nominalVolts();
	}
}
