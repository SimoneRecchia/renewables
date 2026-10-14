package io.github.simonerecchia.renewables.core;



import net.minecraft.world.level.GameRules;

/** The mod's game rules. */
public final class ModGameRules {
	/** @see RealTimeClock */
	public static GameRules.Key<GameRules.BooleanValue> REAL_TIME_CLOCK;

	private ModGameRules() {
	}

	public static void register() {
		REAL_TIME_CLOCK = GameRules.register("renewablesRealTimeClock", GameRules.Category.MISC, GameRules.BooleanValue.create(false));
	}
}
