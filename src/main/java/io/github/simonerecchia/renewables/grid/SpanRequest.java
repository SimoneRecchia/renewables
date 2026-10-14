package io.github.simonerecchia.renewables.grid;

import net.minecraft.core.BlockPos;

/**
 * What a player's client asked for when it joined two fittings with a conductor, exactly as it arrived.
 *
 * Nothing in here is trusted: {@link SpanPlanner} re-derives every one of these from the server's own
 * block entities before it believes any of it.
 */
public record SpanRequest(int startId, int endId, BlockPos startPos, BlockPos endPos,
		String startFittingType, String endFittingType, String startPowerType, String endPowerType) {
}
