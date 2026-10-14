package io.github.simonerecchia.renewables.api.power;



import io.github.simonerecchia.renewables.api.Nameplate;

import net.minecraft.resources.ResourceLocation;

/**
 * One transformer: what it steps between, and how much of it it will take.
 *
 * The two in the mod are the two a plant's output actually passes through - a machine unit at a
 * generator's foot and a substation unit at the grid connection - and everything different about them
 * follows from the ratio.
 */
public record TransformerSpec(
		ResourceLocation id,
		String displayName,
		Duty duty,
		/** Rating in kVA. */
		double ratingKva,
		/** Volts in and volts out, which is what makes it the machine it is. */
		double primaryVolts,
		double secondaryVolts,
		/** Load loss Pk at rated current and 75 °C, kW: the windings' I²R, which goes with the square of the load. */
		double loadLossKw,
		/** No-load loss P0 in kW: the core's, which it burns whether anything is drawing from it or not. */
		double coreLossKw,
		/** The model this is drawn as. */
		String modelName,
		/** How many fittings it has: three bushings on a machine unit, six on a substation one. */
		int bushings) {

	public enum Duty {
		/** Low voltage to medium: at the foot of every generator on the plant. */
		MACHINE,
		/** Medium voltage to high: the grid connection, and where a transmission line starts. */
		SUBSTATION
	}

	/**
	 * What carrying {@code kw} costs in losses: {@code P0 + Pk (S / Sr)^2}, the core loss plus the windings' at that
	 * share of the rating.  The grid moves active power at unity power factor, so S is the kW itself.
	 */
	public double lossKw(double kw) {
		double load = Math.abs(kw) / ratingKva;
		return coreLossKw + loadLossKw * load * load;
	}

	/** Efficiency at a load, as a fraction: what comes out over what goes in. */
	public double efficiencyAt(double kw) {
		double through = Math.abs(kw);
		return through <= 0.0 ? 0.0 : Math.max(0.0, through - lossKw(through)) / through;
	}

	/** The ratio, for a tooltip: 0.8/33 reads better than two numbers in volts. */
	public String ratioText() {
		return Nameplate.fmt("%.3g/%.3g kV", primaryVolts / 1000.0, secondaryVolts / 1000.0);
	}
}
