package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.TransformerSpec;

/**
 * What a transformer does to the power that crosses it: the one place the loss is worked out, so that the grid's
 * flow and the figure a player reads off the wrench are the same.
 *
 * Pure arithmetic on a {@link TransformerSpec}.
 */
public final class TransformerFlow {
	private TransformerFlow() {
	}

	/**
	 * What comes out for {@code inKw} going in: the load less the losses (a core loss that is burned whatever is
	 * drawn, and a share of the load), never below nothing.  The way it flows is kept - a negative reading is power
	 * coming the other way and stays negative - and a flow that does not cover the core loss comes out as zero.
	 */
	public static double output(TransformerSpec spec, double inKw) {
		double magnitude = Math.max(0.0, Math.abs(inKw) - spec.lossKw(inKw));
		return inKw < 0.0 ? -magnitude : magnitude;
	}
}
