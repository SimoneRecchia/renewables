package io.github.simonerecchia.renewables.weather;



import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What stands between an array and the sky.
  *
 * It is also why {@link PlaneIrradiance} carries its three components apart rather than added up.
 */
public final class Shading {
	/** How far a beam ray is traced, in blocks. */
	private static final int BEAM_TRACE_BLOCKS = 24;
	/** How far a sky ray is traced. */
	private static final int SKY_TRACE_BLOCKS = 12;
	/** How far along the sun's direction an entity can still cast its shadow onto the array, in blocks. */
	private static final double ENTITY_SHADOW_REACH = 6.0;

	/** What a block passes, as a fraction */
	private static final double GLASS = 0.88;
	private static final double GLASS_PANE = 0.92;
	private static final double ICE = 0.70;
	private static final double PACKED_ICE = 0.35;
	private static final double WATER = 0.50;
	private static final double LEAVES = 0.25;

	/** Metres of real snow one vanilla snow layer stands for. */
	public static final double METRES_PER_SNOW_LAYER = 0.05;
	/** Depth of snow that halves what reaches the cells, in metres. */
	private static final double SNOW_HALF_DEPTH_M = 0.014;

	/** Soiling accumulation, as a fraction of output lost per day of dry weather. */
	private static final double SOILING_PER_DAY_DRY = 0.0030;
	private static final double SOILING_PER_DAY_WET = 0.0005;
	/** Where soiling stops getting worse. */
	private static final double SOILING_CEILING = 0.15;

	private Shading() {
	}

	/** What the snow on the glass passes. */
	public static double snowTransmittance(double snowDepthM) {
		return snowDepthM <= 0.0 ? 1.0 : Math.exp(-snowDepthM * Math.log(2.0) / SNOW_HALF_DEPTH_M);
	}

	// ---- what is overhead ----

	/**
	 * Fraction of the beam reaching a row, traced along the sun's own direction from the middle of its module plane.
	 *
	 * @param planeHeight how far above the block's floor the module plane is, in blocks: a ballasted table sits low and
	 *                    a tracker's plane on its torque tube, and a block beside the row shades the modules only if it
	 *                    stands higher than they do
	 * @param sameField   blocks the trace passes through: the other rows, whose shading of this one is the row model's
	 */
	public static double beamFraction(Level level, BlockPos arrayPos, double planeHeight, SunPosition sun, Predicate<BlockState> sameField) {
		if (!sun.up()) return 0.0;

		return traceTransmittance(level, planeCentre(arrayPos, planeHeight), towardsSun(sun), BEAM_TRACE_BLOCKS, sameField);
	}

	/** Fraction of the sky dome a row's module plane can see. */
	public static double skyViewFactor(Level level, BlockPos arrayPos, double planeHeight, Predicate<BlockState> sameField) {
		Vec3 from = planeCentre(arrayPos, planeHeight);

		double weighted = traceTransmittance(level, from, new Vec3(0.0, 1.0, 0.0), SKY_TRACE_BLOCKS, sameField);
		double totalWeight = 1.0;

		double sin45 = Math.sin(Math.toRadians(45.0));
		double cos45 = Math.cos(Math.toRadians(45.0));
		for (int i = 0; i < 8; i++) {
			double bearing = Math.toRadians(i * 45.0);
			Vec3 direction = new Vec3(cos45 * Math.cos(bearing), sin45, cos45 * Math.sin(bearing));
			weighted += sin45 * traceTransmittance(level, from, direction, SKY_TRACE_BLOCKS, sameField);
			totalWeight += sin45;
		}

		return Mth.clamp(weighted / totalWeight, 0.0, 1.0);
	}

	private static Vec3 planeCentre(BlockPos arrayPos, double planeHeight) {
		return new Vec3(arrayPos.getX() + 0.5, arrayPos.getY() + planeHeight, arrayPos.getZ() + 0.5);
	}

	/** Unit vector pointing at the sun, on the world's axes. */
	static Vec3 towardsSun(SunPosition sun) {
		double bearing = Math.toRadians(sun.azimuthDeg());
		double cosElevation = sun.cosElevation();
		return new Vec3(cosElevation * Math.cos(bearing), sun.sinElevation(), cosElevation * Math.sin(bearing));
	}

	/** Every block a ray crosses, once each, in order: vanilla's own walk, the one {@code Level.clip} makes. */
	private static double traceTransmittance(Level level, Vec3 from, Vec3 direction, double blocks, Predicate<BlockState> sameField) {
		Vec3 to = from.add(direction.normalize().scale(blocks));
		double[] transmittance = {1.0};

		BlockGetter.traverseBlocks(from, to, transmittance, (passed, pos) -> {
			if (pos.getY() > level.getMaxBuildHeight()) return Boolean.TRUE;
			if (pos.getY() < level.getMinBuildHeight()) return null;
			// an unloaded chunk ends the trace rather than blocking it: an unknown must not be able to
			// manufacture a shadow that appears and disappears with a player's render distance
			if (!level.hasChunkAt(pos)) return Boolean.TRUE;

			BlockState state = level.getBlockState(pos);
			if (sameField.test(state)) return null;

			passed[0] *= blockTransmittance(level, state, pos, from, to);
			return passed[0] <= 0.0 ? Boolean.TRUE : null;
		}, passed -> Boolean.FALSE);

		return Math.max(0.0, transmittance[0]);
	}

	/** What one block passes of a ray running from {@code from} to {@code to}. */
	private static double blockTransmittance(BlockGetter level, BlockState state, BlockPos pos, Vec3 from, Vec3 to) {
		if (state.isAir()) return 1.0;
		// a few centimetres of snow on the ground, or on the modules - where the snow model already counts it
		if (state.getBlock() instanceof SnowLayerBlock) return 1.0;
		if (state.is(BlockTags.LEAVES)) return LEAVES;
		if (state.is(BlockTags.IMPERMEABLE)) return GLASS;
		// glass panes and iron bars are the same class in vanilla, and both pass more than a solid
		// pane of glass does because most of what a player builds out of them is gaps
		if (state.getBlock() instanceof IronBarsBlock) return GLASS_PANE;
		if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) return ICE;
		if (state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) return PACKED_ICE;
		if (!state.getFluidState().isEmpty()) return WATER;
		if (state.isSolidRender(level, pos)) return 0.0;

		// anything else is a shape: a cabinet, a slab, a fence, a post. The ray has to pass through it, and then
		// it takes what its outline covers across the beam - a cabinet nearly all of it, a fence post a quarter
		VoxelShape shape = state.getShape(level, pos);
		if (shape.isEmpty() || shape.clip(from, to, pos) == null) return 1.0;

		return 1.0 - crossSection(shape.bounds(), to.subtract(from).normalize());
	}

	/** How much of a block's cross-section across a direction a box covers, 0 to 1. */
	static double crossSection(AABB box, Vec3 direction) {
		double x = Math.abs(direction.x);
		double y = Math.abs(direction.y);
		double z = Math.abs(direction.z);
		double shadow = x * box.getYsize() * box.getZsize() + y * box.getXsize() * box.getZsize() + z * box.getXsize() * box.getYsize();
		return Mth.clamp(shadow / Math.max(1.0e-9, x + y + z), 0.0, 1.0);
	}

	// ---- what is moving over it ----

	/** Fraction of the beam a row keeps after whatever is standing or moving between it and the sun, 0 to 1. */
	public static double entityBeamFraction(Level level, BlockPos arrayPos, double planeHeight, SunPosition sun) {
		if (!sun.up()) return 1.0;

		Vec3 towardsSun = towardsSun(sun);
		double planeY = arrayPos.getY() + planeHeight;
		AABB square = new AABB(arrayPos.getX(), planeY, arrayPos.getZ(), arrayPos.getX() + 1.0, planeY, arrayPos.getZ() + 1.0);
		// anything whose shadow can reach the square stands somewhere along the sun's direction from it
		AABB corridor = square.minmax(square.move(towardsSun.scale(ENTITY_SHADOW_REACH))).inflate(0.01);

		List<Entity> entities = level.getEntities((Entity) null, corridor, Shading::castsShadow);
		if (entities.isEmpty()) return 1.0;

		double covered = 0.0;
		for (Entity entity : entities) {
			covered += shadowOnSquare(entity.getBoundingBox(), square, towardsSun);
		}

		return Mth.clamp(1.0 - covered, 0.0, 1.0);
	}

	/** Bodies and vehicles; not dropped items, arrows, orbs or anyone who cannot be seen. */
	private static boolean castsShadow(Entity entity) {
		if (entity.isSpectator() || entity.isInvisible()) return false;

		return entity instanceof LivingEntity || entity instanceof AbstractMinecart || entity instanceof Boat;
	}

	/**
	 * The part of a one-block square at the module plane that a box's shadow covers: the box above the plane
	 * projected down the sun's direction onto it, and the overlap of that footprint with the square.
	 */
	static double shadowOnSquare(AABB box, AABB square, Vec3 towardsSun) {
		double planeY = square.minY;
		if (box.maxY <= planeY || towardsSun.y <= 0.0) return 0.0;

		double bottom = Math.max(box.minY, planeY);
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (double y : new double[]{bottom, box.maxY}) {
			// how far back along the ray this height lands on the plane
			double run = (y - planeY) / towardsSun.y;
			for (double x : new double[]{box.minX, box.maxX}) {
				double projected = x - towardsSun.x * run;
				minX = Math.min(minX, projected);
				maxX = Math.max(maxX, projected);
			}

			for (double z : new double[]{box.minZ, box.maxZ}) {
				double projected = z - towardsSun.z * run;
				minZ = Math.min(minZ, projected);
				maxZ = Math.max(maxZ, projected);
			}
		}

		double overlapX = Math.max(0.0, Math.min(maxX, square.maxX) - Math.max(minX, square.minX));
		double overlapZ = Math.max(0.0, Math.min(maxZ, square.maxZ) - Math.max(minZ, square.minZ));
		return Mth.clamp(overlapX * overlapZ, 0.0, 1.0);
	}

	// ---- the array shading itself ----

	/**
	 * Fraction of a row that the row in front of it is shading.  The geometry is the one across the rows, so what
	 * counts is the sun's profile angle in that plane, {@code tan(ap) = tan(a) / cos(sun azimuth - plane azimuth)}: a
	 * sun beside the rows or behind them casts no row's shadow on the next.
	 */
	public static double rowShadedFraction(double groundCoverRatio, double tiltDeg, double planeAzimuthDeg, SunPosition sun) {
		if (groundCoverRatio <= 0.0 || tiltDeg <= 0.0 || !sun.up()) return 0.0;

		double across = Math.cos(Math.toRadians(sun.azimuthDeg() - planeAzimuthDeg));
		if (across <= 1.0e-6) return 0.0;

		double tilt = Math.toRadians(tiltDeg);
		double profile = Math.atan(Math.tan(Math.toRadians(Math.min(89.99, sun.elevationDeg()))) / across);
		double reach = Math.cos(tilt) + Math.sin(tilt) / Math.tan(profile);
		double clear = groundCoverRatio * reach;
		if (clear <= 1.0) return 0.0;

		return Mth.clamp(1.0 - 1.0 / clear, 0.0, 1.0);
	}

	// ---- snow ----

	/** Snow lying on an array, in metres. */
	public static double snowDepthOver(Level level, BlockPos arrayPos) {
		BlockPos above = arrayPos.above();
		if (!level.hasChunkAt(above)) return 0.0;

		BlockState state = level.getBlockState(above);
		if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return 8.0 * METRES_PER_SNOW_LAYER;
		if (state.getBlock() instanceof SnowLayerBlock) return state.getValue(SnowLayerBlock.LAYERS) * METRES_PER_SNOW_LAYER;

		return 0.0;
	}

	// ---- soiling ----

	/** How fast dust builds up here, as a fraction of output lost per day. */
	public static double soilingPerDay(double downfall) {
		double dryness = 1.0 - Mth.clamp(downfall, 0.0, 1.0);
		return SOILING_PER_DAY_WET + (SOILING_PER_DAY_DRY - SOILING_PER_DAY_WET) * dryness;
	}

	/** Where soiling stops getting worse, as a fraction of output. */
	public static double soilingCeiling() {
		return SOILING_CEILING;
	}

	/** How much of the accumulated dust a rain shower takes off, as a fraction. */
	public static double washedByRain(double tiltDeg) {
		return tiltDeg >= 10.0 ? 1.0 : 0.55;
	}
}
