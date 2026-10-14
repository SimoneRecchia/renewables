package io.github.simonerecchia.renewables.grid;

/**
 * Which way a span lets power go, as far as the roles saved on its two ends are concerned.
 *
 * The other half of the rule is the machines' own: a machine says which others it may feed
 * ({@link InsulatorHost#feeds}), and a switch accepts from anyone.  A span carries power from A to B only when both
 * agree, so the cabin's two fittings, which are the only ones that are not bidirectional, are what make a
 * generator wired to the wrong one a dead span.
 */
final class FlowRules {
	private FlowRules() {
	}

	/** Whether a fitting with this stored role may send power out along a span. */
	static boolean sends(String role) {
		return !FittingTypes.POWER_INPUT.equals(role);
	}

	/** Whether a fitting with this stored role may take power in from a span. */
	static boolean receives(String role) {
		return !FittingTypes.POWER_OUTPUT.equals(role);
	}

	/**
	 * Whether the roles at the two ends allow power from A to B.
	 *
	 * An unknown role reads as bidirectional: a span from an older save must not become a dead wire because of a
	 * string this build does not recognise.  The one extra case is an input wired to an output on the same machine,
	 * a bridge through it; nothing in the mod can make one, because fittings of one machine share a bus and spans
	 * inside a bus are ignored.
	 */
	static boolean rolesAllow(String roleA, String roleB, boolean sameMachine) {
		if (sends(roleA) && receives(roleB)) return true;

		return sameMachine && FittingTypes.POWER_INPUT.equals(roleA) && FittingTypes.POWER_OUTPUT.equals(roleB);
	}

	/** The whole rule: the machines agree and so do the roles. */
	static boolean allows(boolean machineFeeds, boolean destinationPassesThrough, String roleA, String roleB, boolean sameMachine) {
		return (machineFeeds || destinationPassesThrough) && rolesAllow(roleA, roleB, sameMachine);
	}
}
