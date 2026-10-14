package io.github.simonerecchia.renewables.wind;

import io.github.simonerecchia.renewables.api.power.TurbineSpec;

/**
 * The sizes the wind line is built to, as numbers the blocks, the renderer and the tests can share: what part of a
 * block the nacelle fills, how tall a tower may stand, and how a pile of tower reads.
 *
 * The nacelle figures are properties of the authored model ({@code wind_turbine.obj}); when the model is redrawn they
 * are the two values to remeasure (the height of the nacelle group, and the farthest a nacelle vertex is from the
 * tower's axis in plan).
 */
public final class TurbineGeometry {
	/** Height of the authored nacelle, in blocks, at scale 1. */
	public static final double NACELLE_HEIGHT = 0.95;
	/** Distance from the tower's axis to the farthest corner of the authored nacelle in plan, in blocks. */
	public static final double NACELLE_REACH = 1.575;

	/** A pile of tower this tall stands for anybody, and holds up under a walker. */
	public static final int SAFE_SEGMENTS = 16;
	/** The tallest a pile can be built before it folds as soon as the block is set. */
	public static final int MAX_SEGMENTS = 20;
	/** How far up or down the column queries look. */
	public static final int SCAN_LIMIT = 24;

	/** Half the width of the tower's two crossed boxes along their long side, and half along the short one. */
	public static final double TOWER_LONG_HALF = 0.3465;
	public static final double TOWER_SHORT_HALF = 0.245;

	private TurbineGeometry() {
	}

	/** Half the side of the (square) box the nacelle fills, in blocks; never more than the block itself. */
	public static double nacelleHalfWidth(TurbineSpec spec) {
		return Math.min(0.5, NACELLE_REACH * spec.nacelleRenderScale());
	}

	/** How tall the box the nacelle fills is, in blocks; never more than the block itself. */
	public static double nacelleHeight(TurbineSpec spec) {
		return Math.min(1.0, NACELLE_HEIGHT * spec.nacelleRenderScale());
	}

	/** Whether a pile this tall, the block just set included, folds as it is placed. */
	public static boolean collapsesWhenPlaced(int pileHeight) {
		return pileHeight > MAX_SEGMENTS;
	}

	/** Whether a pile this tall folds under whatever steps on its top. */
	public static boolean collapsesWhenWalkedOn(int pileHeight) {
		return pileHeight > SAFE_SEGMENTS;
	}
}
