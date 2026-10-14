package io.github.simonerecchia.renewables.catalog;

import static io.github.simonerecchia.renewables.catalog.Catalogue.id;
import io.github.simonerecchia.renewables.api.power.TrackerSpec;

import io.github.simonerecchia.renewables.Renewables;
import net.minecraft.resources.ResourceLocation;

/** The trackers the mod ships. */
public final class TrackerCatalog {
	/** The fictional maker whose racking and trackers the arrays are built on. */
	public static final String MANUFACTURER = "Meridian";


	/**
	 * Horizontal single axis, one drive per row, self-powered: ±60° as a Nextracker NX Horizon, and a flat stow at
	 * 20 m/s, inside the 12 to 22 m/s the trackers on the market stow at.
	 */
	public static final TrackerSpec HORIZON_R = new TrackerSpec(
			id("tracker_horizon_r"), "Horizon R", 1,
			60.0, 0.0,
			4.4, 2.0,
			0.0, 0.0, 60.0,
			20.0, 0.04,
			true, 120.0);

	/** Azimuth-elevation dual axis on a pedestal: down to a 10° sun, as a Mechatron M18's 0-80° zenith range. */
	public static final TrackerSpec ZENITH_AE = new TrackerSpec(
			id("tracker_zenith_ae"), "Zenith AE", 2,
			80.0, 10.0,
			3.0, 1.0,
			0.0, 0.0, 70.0,
			18.0, 0.04,
			true, 250.0);

	private TrackerCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Renewables.MOD_ID, path);
	}

	/** Full name as a sell sheet prints it. */
	public static String fullName(TrackerSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}
}
