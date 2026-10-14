package io.github.simonerecchia.renewables.catalog;

import static io.github.simonerecchia.renewables.catalog.Catalogue.id;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;

import java.util.List;

/**
 * The machines the mod ships, in the order a player builds them.
  *
 * Here the maker is {@link #MANUFACTURER}, so a C90 has a 90 m rotor.
 */
public final class TurbineCatalog {
	/** The fictional maker whose initial the model numbers carry. */
	public static final String MANUFACTURER = "Cube";

	private static final Catalogue<TurbineSpec> TURBINES = new Catalogue<>(TurbineSpec::id);

	/**
	 * The 10 kW machine, and the only one outside the C line: a Bergey Excel 10's 7 m rotor, 2.5 m/s cut-in and
	 * 0-400 rpm, direct drive.
	 */
	public static final TurbineSpec SW_10 = TURBINES.register(new TurbineSpec(
			id("sw_10"), "SW-10", "",
			10.0, 7.0, 0.35,
			2.5, 25.0, 25.0,
			60.0, 400.0,
			2, 4,
			3.3, TurbineSpec.Nacelle.SMALL_WIND));

	/** High-wind class: a small rotor worked hard, for an exposed site.  A Vestas V52-850's figures throughout. */
	public static final TurbineSpec C52_085 = TURBINES.register(new TurbineSpec(
			id("c52_085"), "C52-0.85", "IA",
			850.0, 52.0, 0.44,
			4.0, 25.0, 22.0,
			14.0, 31.4,
			4, 7,
			1.0, TurbineSpec.Nacelle.UTILITY));

	/** A Vestas V80-2.0 MW's figures. */
	public static final TurbineSpec C80_20 = TURBINES.register(new TurbineSpec(
			id("c80_20"), "C80-2.0", "IIA",
			2000.0, 80.0, 0.46,
			4.0, 25.0, 22.0,
			9.0, 19.0,
			6, 10,
			1.0, TurbineSpec.Nacelle.UTILITY));

	/** A Vestas V90-3.0 MW's figures: 8.6 to 18.4 rpm, ±30 % about its generator's synchronous speed. */
	public static final TurbineSpec C90_30 = TURBINES.register(new TurbineSpec(
			id("c90_30"), "C90-3.0", "IIA",
			3000.0, 90.0, 0.46,
			3.5, 25.0, 22.0,
			8.6, 18.4,
			8, 11,
			1.0, TurbineSpec.Nacelle.UTILITY));

	/** The C90's generator behind a rotor a fifth wider: same nameplate, reached in less wind.  A Vestas V112-3.0 MW's figures. */
	public static final TurbineSpec C112_30 = TURBINES.register(new TurbineSpec(
			id("c112_30"), "C112-3.0", "IIIA",
			3000.0, 112.0, 0.47,
			3.0, 25.0, 22.0,
			6.2, 17.7,
			8, 12,
			1.0, TurbineSpec.Nacelle.UTILITY));

	/**
	 * The top of the line, and the machine the authored model already was: its rotor measures 13.044 blocks from the hub, so this is the one that draws at 1:1 and every other model is it scaled down.
	 *
	 * Its class is the 4 MW, 130 m low-wind machine: a Siemens SWT-4.0-130 tops out at 14 rpm, and a Nordex
	 * N131/3900 runs from 7.9 rpm with the same 3 m/s cut-in and 25 m/s cut-out.
	 */
	public static final TurbineSpec C130_40 = TURBINES.register(new TurbineSpec(
			id("c130_40"), "C130-4.0", "IIIA",
			4000.0, 130.0, 0.47,
			3.0, 25.0, 22.5,
			7.9, 14.0,
			9, 13,
			1.0, TurbineSpec.Nacelle.UTILITY));

	private TurbineCatalog() {
	}

	/** Every known machine */
	public static List<TurbineSpec> all() {
		return TURBINES.all();
	}

	/**
	 * Full designation as a nameplate prints it, e.g.
	  *
	 * {@code Cube C130-4.0}.
	 */
	public static String fullName(TurbineSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}

	/**
	 * The machine a turbine falls back to when its saved model cannot be resolved — an id from a datapack that is no longer loaded, or a world older than the catalogue.
	 */
	public static TurbineSpec fallback() {
		return C130_40;
	}
}
