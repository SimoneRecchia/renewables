package io.github.simonerecchia.renewables.core;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The server-side settings, stored with the world in {@code serverconfig/Renewables/server.toml}.
 *
 * Every value is read at the moment it is used and never copied at start-up, so a pack author who edits the
 * file while a world is open sees it take effect, and a world saved with its own numbers keeps them.
 */
public final class ServerConfig {
	public static final ForgeConfigSpec SPEC;

	private static final ForgeConfigSpec.IntValue POWER_BOX_RADIUS;
	private static final ForgeConfigSpec.BooleanValue EXTERNAL_ENERGY;
	private static final ForgeConfigSpec.DoubleValue TURBINE_EXPORT_FRACTION;
	private static final ForgeConfigSpec.DoubleValue TURBINE_MAX_JOULES;
	private static final ForgeConfigSpec.DoubleValue PV_EXPORT_FRACTION;
	private static final ForgeConfigSpec.DoubleValue PV_MAX_JOULES;
	private static final ForgeConfigSpec.IntValue MAX_CABLE_RUN;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

		builder.push("power");
		POWER_BOX_RADIUS = builder
				.comment("Radius (in blocks) of power field around a Power Box")
				.defineInRange("powerBoxRadius", 5, 1, 16);
		builder.pop();

		builder.push("energy");
		EXTERNAL_ENERGY = builder
				.comment("Let generators feed the energy systems of other mods (Mekanism Joules and Forge Energy) directly")
				.define("externalEnergyEnabled", true);
		TURBINE_EXPORT_FRACTION = builder
				.comment("Share of a turbine's nameplate power that it offers to other mods' cables, from 0 to 1.",
						"A fraction rather than a fixed number of Joules because turbines run from 10 kW to 4 MW, and any",
						"absolute cap would either strangle the big ones or hand the small ones too much.",
						"1.0 means what a machine produces is what it offers. Whatever is not taken stays on the wire grid.")
				.defineInRange("turbineExportFraction", 1.0, 0.0, 1.0);
		TURBINE_MAX_JOULES = builder
				.comment("Optional absolute ceiling, in Joules per tick, applied after the fraction. 0 disables it.",
						"For reference a 4 MW turbine makes 500000 J/t at 125 J per kW before either limit.")
				.defineInRange("turbineMaxJoulesPerTick", 0.0, 0.0, 1.0e9);
		PV_EXPORT_FRACTION = builder
				.comment("As turbineExportFraction, for the photovoltaic inverter: same scale and same default, so a plant of",
						"either kind meets a modpack's economy on the same terms. 1 means the AC figure on the inverter's",
						"panel is what reaches the cable. The rest stays on the wire grid.")
				.defineInRange("pvExportFraction", 1.0, 0.0, 1.0);
		PV_MAX_JOULES = builder
				.comment("Optional absolute ceiling, in Joules per tick, applied after the photovoltaic fraction. 0 disables it.")
				.defineInRange("pvMaxJoulesPerTick", 0.0, 0.0, 1.0e9);
		builder.pop();

		builder.push("photovoltaics");
		MAX_CABLE_RUN = builder
				.comment("Longest run of DC cable a machine will follow, in blocks.",
						"32 blocks is 320 m at 10 m a block: past that a string's voltage drop is already a bad idea. This is a",
						"safety net against a cable laid across a continent, not a design limit - physics is the limit, and the",
						"loss in percent is on the panel.")
				.defineInRange("maxCableRun", 32, 2, 256);
		builder.pop();

		SPEC = builder.build();
	}

	private ServerConfig() {
	}

	/** Radius of the field a power box powers, in blocks. */
	public static int powerBoxRadius() {
		return POWER_BOX_RADIUS.get();
	}

	/** Whether a generator may offer its output to other mods' energy systems at all. */
	public static boolean externalEnergyEnabled() {
		return EXTERNAL_ENERGY.get();
	}

	public static double turbineExportFraction() {
		return TURBINE_EXPORT_FRACTION.get();
	}

	/** The turbine ceiling in Joules per tick; a configured 0 means no ceiling at all. */
	public static double turbineMaxJoulesPerTick() {
		return ceiling(TURBINE_MAX_JOULES.get());
	}

	public static double pvExportFraction() {
		return PV_EXPORT_FRACTION.get();
	}

	/** The photovoltaic ceiling in Joules per tick; a configured 0 means no ceiling at all. */
	public static double pvMaxJoulesPerTick() {
		return ceiling(PV_MAX_JOULES.get());
	}

	/** How far a machine will follow DC cable, in blocks. */
	public static int maxCableRun() {
		return MAX_CABLE_RUN.get();
	}

	/**
	 * How far from a machine another one still counts as its neighbour for DC-side searches.
	 *
	 * The old settings had a key of its own for this, which no code read any more; the cable run is the only
	 * reach left, so the name stays as an alias of it for the callers that want a radius.
	 */
	public static int arraySearchRadius() {
		return maxCableRun();
	}

	/** Zero or less is "no limit", which as a ceiling is the largest number there is. */
	private static double ceiling(double configured) {
		return configured <= 0.0 ? Double.MAX_VALUE : configured;
	}
}
