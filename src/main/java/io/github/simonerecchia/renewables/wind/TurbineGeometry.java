package io.github.simonerecchia.renewables.wind;

import io.github.simonerecchia.renewables.api.power.TurbineSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * The sizes the wind line is built to, as numbers the blocks, the renderer and the tests can share: what part of a
 * block the nacelle fills, how a tower's tube narrows and what shape it makes as collision, and how tall a pile of
 * tower may stand.
 *
 * The figures are measured off the model ({@code wind_turbine.obj}, see docs/turbine-model.md section 7) and are the
 * ones to remeasure when it is redrawn.  Boxes are plain {@code double[6]} (x0, y0, z0, x1, y1, z1) in blocks from
 * the corner of the block, in the frame the model was authored in (facing south); the blocks turn them with the
 * machine's facing.
 */
public final class TurbineGeometry {
	/** The tower's tube as drawn at scale 1: radius at the foot and at the head of {@link TurbineSpec#MODEL_TOWER_HEIGHT_BLOCKS}. */
	public static final double TOWER_BASE_RADIUS = 0.340;
	public static final double TOWER_TOP_RADIUS = 0.190;
	/** A section of the tube is a staircase of rectangles whose corners sit on the circle: one pair per angle, in degrees. */
	public static final double[] TOWER_BOX_ANGLES = {10.0, 20.0, 30.0, 40.0};

	/** The nacelle's hull and the cooler on its roof, authored: x and z about the tower's axis, y up from the nacelle's floor. */
	private static final double[] NACELLE = {-0.278, 0.0, -0.888, 0.280, 0.600, 0.660};
	private static final double[] COOLER = {-0.196, 0.580, -0.826, 0.196, 0.786, -0.354};

	/** The insulator's post and plate, never scaled, as boxes of the foot block (frame facing south). */
	public static final double[] INSULATOR_BOX = {0.775, 0.0, 0.775, 0.975, 6.25 / 16.0, 0.975};

	/** The rotor's centre, authored: this far above the nacelle's floor and this far ahead of the tower's axis (pivot H). */
	public static final double HUB_HEIGHT = 0.300;
	public static final double HUB_AHEAD = 1.040;
	/** The blade tips' radius about the hub, authored: the C130's 6.5 blocks. */
	public static final double ROTOR_RADIUS = TurbineSpec.MODEL_ROTOR_DIAMETER_BLOCKS / 2.0;
	/** About one point per block of swept area, within these bounds. */
	private static final int MIN_DISC_POINTS = 12;
	private static final int MAX_DISC_POINTS = 160;
	/** The golden angle: successive points of a sunflower spiral, which spreads them evenly over a disc. */
	private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

	/** A pile of tower this tall stands for anybody, and holds up under a walker. */
	public static final int SAFE_SEGMENTS = 16;
	/** The tallest a pile can be built before it folds as soon as the block is set. */
	public static final int MAX_SEGMENTS = 20;
	/** How far up or down the column queries look. */
	public static final int SCAN_LIMIT = 24;

	private TurbineGeometry() {
	}

	// ---- the nacelle ----

	/** An authored box (x, z about the axis, y from the nacelle floor) scaled and cut to the turbine's own cell. */
	private static double[] cellBox(double[] authored, double scale) {
		return new double[] {
				clamp01(0.5 + authored[0] * scale), clamp01(authored[1] * scale), clamp01(0.5 + authored[2] * scale),
				clamp01(0.5 + authored[3] * scale), clamp01(authored[4] * scale), clamp01(0.5 + authored[5] * scale)};
	}

	/**
	 * What the turbine block collides with and is picked by: the hull and the cooler, at the nacelle's scale and cut to the
	 * block.  The rest of the head, the rotor and the tail that overhangs the block collide with nothing.
	 */
	public static List<double[]> nacelleBoxes(TurbineSpec spec) {
		double scale = spec.nacelleRenderScale();
		return List.of(cellBox(NACELLE, scale), cellBox(COOLER, scale));
	}

	// ---- the rotor ----

	/**
	 * Where the hub is, as {x, y, z} in blocks from the middle of the floor of the turbine's own block, for a nacelle the
	 * model draws turned {@code modelYawDegrees}: ahead of the tower's axis and above the nacelle's floor, at its scale.
	 */
	public static double[] hubOffset(TurbineSpec spec, double modelYawDegrees) {
		double scale = spec.nacelleRenderScale();
		double angle = Math.toRadians(modelYawDegrees);
		return new double[] {Math.sin(angle) * HUB_AHEAD * scale, HUB_HEIGHT * scale, Math.cos(angle) * HUB_AHEAD * scale};
	}

	/** The radius the blade tips sweep, in blocks. */
	public static double rotorRadius(TurbineSpec spec) {
		return ROTOR_RADIUS * spec.renderScale();
	}

	/**
	 * Points spread evenly over the disc the blades sweep, each standing for the same share of its area, as {x, y, z} from
	 * the middle of the floor of the turbine's block: a sunflower spiral in the plane square to the rotor's axis through the
	 * hub.  About one to a block of area, so a C130's 133 blocks of disc is surveyed block by block.
	 */
	public static List<double[]> rotorDisc(TurbineSpec spec, double modelYawDegrees) {
		double[] hub = hubOffset(spec, modelYawDegrees);
		double radius = rotorRadius(spec);
		double angle = Math.toRadians(modelYawDegrees);
		// the model's +x, its right seen from behind, turned with it
		double rightX = Math.cos(angle);
		double rightZ = -Math.sin(angle);

		int count = (int) Math.max(MIN_DISC_POINTS, Math.min(MAX_DISC_POINTS, Math.ceil(Math.PI * radius * radius)));
		List<double[]> points = new ArrayList<>(count);
		for (int k = 0; k < count; k++) {
			double r = radius * Math.sqrt((k + 0.5) / count);
			double phi = k * GOLDEN_ANGLE;
			double across = r * Math.cos(phi);
			double up = r * Math.sin(phi);
			points.add(new double[] {hub[0] + rightX * across, hub[1] + up, hub[2] + rightZ * across});
		}

		return points;
	}

	// ---- the tower ----

	/**
	 * The radius of the tube at the middle of tower block {@code index} (0 is the foot) of a pile of {@code height}
	 * blocks drawn at {@code scale}: the authored cone, stretched over the pile.
	 */
	public static double towerRadius(int index, int height, double scale) {
		double along = (index + 0.5) / height;
		return scale * (TOWER_BASE_RADIUS + (TOWER_TOP_RADIUS - TOWER_BASE_RADIUS) * along);
	}

	/**
	 * The scale a bare tower (one with no machine on it) is drawn at: a quarter at two blocks, full at thirteen and
	 * over.  A tower that carries a machine takes the machine's own scale instead.
	 */
	public static double bareTowerScale(int height) {
		return Math.max(0.25, Math.min(1.0, 0.25 + (height - 2) / 11.0 * 0.75));
	}

	/**
	 * The staircase that stands in for a round tube of this radius: for each angle the two rectangles whose corners are
	 * on the circle, one and its quarter turn, over the block's full height.  It fills the circle of 0.9 the radius and
	 * never leaves the circle.
	 */
	public static List<double[]> towerBoxes(double radius) {
		List<double[]> boxes = new ArrayList<>();
		for (double degrees : TOWER_BOX_ANGLES) {
			double angle = Math.toRadians(degrees);
			double[][] halves = {
					{radius * Math.cos(angle), radius * Math.sin(angle)},
					{radius * Math.sin(angle), radius * Math.cos(angle)}};
			for (double[] half : halves) {
				boolean repeated = false;
				for (double[] other : boxes) {
					if (Math.abs(other[3] - 0.5 - half[0]) < 1e-9 && Math.abs(other[5] - 0.5 - half[1]) < 1e-9) repeated = true;
				}

				if (!repeated) boxes.add(new double[] {0.5 - half[0], 0.0, 0.5 - half[1], 0.5 + half[0], 1.0, 0.5 + half[1]});
			}
		}

		return boxes;
	}

	// ---- how tall ----

	/** Whether a pile this tall, the block just set included, folds as it is placed. */
	public static boolean collapsesWhenPlaced(int pileHeight) {
		return pileHeight > MAX_SEGMENTS;
	}

	/** Whether a pile this tall folds under whatever steps on its top. */
	public static boolean collapsesWhenWalkedOn(int pileHeight) {
		return pileHeight > SAFE_SEGMENTS;
	}

	private static double clamp01(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}
}
