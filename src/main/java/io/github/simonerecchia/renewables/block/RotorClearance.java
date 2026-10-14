package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.wind.TurbineGeometry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Whether a turbine's rotor is out in the air: what is over its nacelle, in its hub and across the disc its blades
 * sweep, and how deep in water its tower stands.
 *
 * Without it a turbine sealed in a cube of stone made more than the one beside it in the open (3824 kW against 3720),
 * and one on the sea floor with its nacelle under water ran as though the sea were air.  The machine surveys itself
 * every second and whenever its nacelle has turned far enough to sweep somewhere else; the item that places one asks
 * first, and refuses a rotor that could not turn.
 *
 * @param covered      something solid, or a fluid, over the nacelle: a turbine in a cave, under the sea or under a roof
 * @param jammed       the hub is inside a block or a fluid, so the rotor cannot turn at all
 * @param openFraction the share of the swept disc that is open air, 0 to 1; the blocked share makes nothing
 * @param submerged    tower segments from the foot up that stand in water: the wind is measured from the water's surface
 */
public record RotorClearance(boolean covered, boolean jammed, double openFraction, int submerged) {
	/** A rotor in clear air on dry ground: what a machine assumes until it has looked. */
	public static final RotorClearance OPEN = new RotorClearance(false, false, 1.0, 0);
	/** A tower segment with water on at least this many of its four sides is standing in it, not beside a stream. */
	private static final int WET_SIDES = 3;

	/** No air reaches the rotor at all. */
	public boolean obstructed() {
		return covered || jammed;
	}

	/** The share of what the wind offers that this rotor can take: nothing if no air reaches it, else its open share. */
	public double exposure() {
		return obstructed() ? 0.0 : openFraction;
	}

	/**
	 * Looks round a turbine at {@code machine} on {@code segments} blocks of tower, its nacelle turned
	 * {@code modelYawDegrees} as the model draws it.  The machine's own tower and nacelle are not in its own way.
	 */
	public static RotorClearance survey(Level level, BlockPos machine, int segments, TurbineSpec spec, double modelYawDegrees) {
		BlockPos foot = machine.below(Math.max(0, segments));

		// the column over the nacelle: the heightmap stops at whatever blocks movement or holds a fluid, leaves aside, and
		// the nacelle itself is the top of it when nothing is above
		boolean covered = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, machine.getX(), machine.getZ()) > machine.getY() + 1;

		double[] hub = TurbineGeometry.hubOffset(spec, modelYawDegrees);
		boolean jammed = blocked(level, machine, foot, at(machine, hub));

		int open = 0;
		var disc = TurbineGeometry.rotorDisc(spec, modelYawDegrees);
		for (double[] point : disc) {
			if (!blocked(level, machine, foot, at(machine, point))) open++;
		}

		int submerged = 0;
		while (submerged < segments && wet(level, foot.above(submerged))) submerged++;

		return new RotorClearance(covered, jammed, disc.isEmpty() ? 1.0 : open / (double) disc.size(), submerged);
	}

	/** The block an offset from the middle of the floor of the turbine's block falls in. */
	private static BlockPos at(BlockPos machine, double[] offset) {
		return BlockPos.containing(machine.getX() + 0.5 + offset[0], machine.getY() + offset[1], machine.getZ() + 0.5 + offset[2]);
	}

	/**
	 * Whether a block is in the way of a blade: anything with collision, and any fluid.  The machine's own column, tower
	 * and nacelle, is not; nor is a block in a chunk that is not loaded, which a ticking turbine never has within reach of
	 * its blades.
	 */
	private static boolean blocked(Level level, BlockPos machine, BlockPos foot, BlockPos pos) {
		if (pos.getX() == machine.getX() && pos.getZ() == machine.getZ() && pos.getY() >= foot.getY() && pos.getY() <= machine.getY()) {
			return false;
		}

		if (!level.hasChunkAt(pos)) return false;

		BlockState state = level.getBlockState(pos);
		return !state.getFluidState().isEmpty() || !state.getCollisionShape(level, pos).isEmpty();
	}

	/** Whether a tower segment stands in water: a fluid on most of its sides. */
	private static boolean wet(Level level, BlockPos segment) {
		int sides = 0;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos beside = segment.relative(side);
			if (level.hasChunkAt(beside) && !level.getFluidState(beside).isEmpty()) sides++;
		}

		return sides >= WET_SIDES;
	}
}
