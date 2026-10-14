package io.github.simonerecchia.renewables.core;



import net.minecraft.world.level.GameRules;

/** The mod's game rules. */
public final class ModGameRules {
	/** @see RealTimeClock */
	public static GameRules.Key<GameRules.BooleanValue> REAL_TIME_CLOCK;

	private ModGameRules() {
	}

	/**
	 * Main thread only, from common setup's enqueueWork: GameRules.register writes an unsynchronised static map that a
	 * mod constructor, on a loading worker, would race another mod for.  A world's saved rules are read after this.
	 */
	public static void register() {
		REAL_TIME_CLOCK = GameRules.register("renewablesRealTimeClock", GameRules.Category.MISC, GameRules.BooleanValue.create(false));
	}
}
