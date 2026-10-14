package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.DcCableSpec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which rows a collector is really wired to, copper and harnesses together.
  *
 * {@link DcNetwork} follows copper and stops at machines.
 */
public final class PvStrings {
	private PvStrings() {
	}

	/** Every row a run of this gauge wires from this machine */
	public static List<DcNetwork.Reach> reachable(Level level, BlockPos origin, DcCableSpec cable, int maxRuns) {
		Map<BlockPos, DcNetwork.Reach> best = new LinkedHashMap<>();
		Deque<DcNetwork.Reach> pending = new ArrayDeque<>();
		for (DcNetwork.Reach reach : DcNetwork.reachable(level, origin, cable, PvArrayBlock::row, maxRuns)) {
			if (PvArrayBlock.meets(level.getBlockState(reach.pos()), reach.from())) pending.add(reach);
		}

		while (!pending.isEmpty()) {
			DcNetwork.Reach at = pending.poll();
			DcNetwork.Reach had = best.get(at.pos());
			if (had != null && had.runs() <= at.runs()) continue;

			best.put(at.pos(), at);
			onward(level, at, maxRuns, pending);
		}

		List<DcNetwork.Reach> found = new ArrayList<>(best.values());
		found.sort(Comparator.comparingInt(DcNetwork.Reach::runs));
		return found;
	}

	/**
	 * The rows the connection goes on to from this one: where its own lead meets the next row, and where the next
	 * row's lead comes into this one's socket - the same two pairings the renderer draws as plugged together, so a
	 * row that looks wired is wired whichever way round it was put down.
	 */
	private static void onward(Level level, DcNetwork.Reach at, int maxRuns, Deque<DcNetwork.Reach> pending) {
		if (at.runs() >= maxRuns) return;

		BlockState state = level.getBlockState(at.pos());
		for (Direction face : Direction.Plane.HORIZONTAL) {
			if (face == at.from()) continue;

			BlockPos next = at.pos().relative(face);
			if (!level.isLoaded(next)) continue;

			BlockState beside = level.getBlockState(next);
			boolean leadOut = PvArrayBlock.gives(state, face) && PvArrayBlock.meets(beside, face.getOpposite());
			boolean leadIn = PvArrayBlock.takes(state, face) && PvArrayBlock.gives(beside, face.getOpposite());
			if (!leadOut && !leadIn) continue;

			pending.add(new DcNetwork.Reach(next, at.runs() + 1, at.buried(), face.getOpposite()));
		}
	}
}
