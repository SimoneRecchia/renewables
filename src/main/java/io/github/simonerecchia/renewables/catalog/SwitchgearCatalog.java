package io.github.simonerecchia.renewables.catalog;

import static io.github.simonerecchia.renewables.catalog.Catalogue.id;
import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.SwitchgearSpec;
import io.github.simonerecchia.renewables.api.power.SwitchgearSpec.Duty;

import java.util.List;

/**
 * The two switches, both of the 36 kV class, because a player's own network is the plant's 33 kV collector.
 *
 * They are a pair rather than two choices: the breaker is what you open under load and the disconnector is
 * what you open after it to make the gap you can see. Ratings are the real ones for a 630 A feeder unit on the
 * three-phase line it is in - root three times the line's 33 kV times 630 A, 36 MW - so the figure a breaker
 * trips above is the figure its nameplate would carry.  (The power goes with the system's nominal voltage, not
 * with the 36 kV the insulation is built for.)
 */
public final class SwitchgearCatalog {
	private static final Catalogue<SwitchgearSpec> SWITCHES = new Catalogue<>(SwitchgearSpec::id);

	/** A 630 A three-pole air-break disconnector: porcelain posts, a copper blade, a hand lever. */
	public static final SwitchgearSpec MV_DISCONNECTOR = SWITCHES.register(new SwitchgearSpec(
			id("mv_disconnector"), "AB-36/630", Duty.DISCONNECTOR,
			ConductorSpec.VoltageClass.MEDIUM, 36_000.0, 3, 36_000.0, "mv_disconnector"));

	/** A 630 A three-pole vacuum circuit breaker: cast epoxy poles over a spring mechanism. */
	public static final SwitchgearSpec MV_BREAKER = SWITCHES.register(new SwitchgearSpec(
			id("mv_breaker"), "VB-36/630", Duty.BREAKER,
			ConductorSpec.VoltageClass.MEDIUM, 36_000.0, 3, 36_000.0, "mv_breaker"));

	private SwitchgearCatalog() {
	}

	public static List<SwitchgearSpec> all() {
		return SWITCHES.all();
	}

}
