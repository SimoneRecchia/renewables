package io.github.simonerecchia.renewables.catalog;

import static io.github.simonerecchia.renewables.catalog.Catalogue.id;
import io.github.simonerecchia.renewables.api.power.PvModuleSpec;

import io.github.simonerecchia.renewables.Renewables;
import net.minecraft.resources.ResourceLocation;

/** The photovoltaic modules the mod ships */
public final class PvModuleCatalog {
	/** The fictional maker whose initial the model numbers carry. */
	public static final String MANUFACTURER = "Helion";


	/**
	 * The p-type workhorse: 108 half-cut PERC cells, 415 W, glass and backsheet, so monofacial.
	 * Figures of a JA Solar JAM54S30-415/MR.
	 */
	public static final PvModuleSpec HP_108_415 = new PvModuleSpec(
			id("hp_108_415"), "HP-108-415", PvModuleSpec.Cell.PERC, 108,
			1.722, 1.134, 0.030, 21.5,
			415.0, 31.61, 13.13, 37.45, 14.02,
			-0.0035, -0.00275, 0.00045,
			45.0, 0.0, 1000.0, 25.0);

	/**
	 * Heterojunction, 132 half-cut cells, 430 W, on a black backsheet.  Figures of a REC Alpha Pure 2 430: 66 cells in
	 * series at the 0.75 V a heterojunction cell makes is the 49.3 V it opens at.
	 */
	public static final PvModuleSpec HJ_132_430 = new PvModuleSpec(
			id("hj_132_430"), "HJ-132-430", PvModuleSpec.Cell.HJT, 132,
			1.865, 1.040, 0.030, 21.7,
			430.0, 41.80, 10.29, 49.30, 10.88,
			-0.0024, -0.0024, 0.00040,
			44.0, 0.0, 1000.0, 25.0);

	/**
	 * Back contact, 66 whole cells, 440 W.  Figures of a Maxeon 6 SPR-MAX6-440: 66 cells in series open at 48 V, so
	 * the 80 V the mod used to give it would have taken 112 of them.
	 */
	public static final PvModuleSpec HB_066_440 = new PvModuleSpec(
			id("hb_066_440"), "HB-066-440", PvModuleSpec.Cell.IBC, 66,
			1.872, 1.032, 0.040, 20.9,
			440.0, 40.50, 10.87, 48.20, 11.58,
			-0.0029, -0.00239, 0.00057,
			43.0, 0.0, 1000.0, 20.0);

	/** Thin film cadmium telluride, 268 cells, 530 W.  Figures of a First Solar Series 7 TR1, FS-7530A-TR1. */
	public static final PvModuleSpec HT_268_530 = new PvModuleSpec(
			id("ht_268_530"), "HT-268-530", PvModuleSpec.Cell.CDTE, 268,
			2.300, 1.216, 0.032, 39.7,
			530.0, 186.90, 2.84, 226.70, 3.05,
			-0.0032, -0.0028, 0.00040,
			45.0, 0.0, 1500.0, 5.0);

	/**
	 * The utility standard: 144 half-cut TOPCon cells, 580 W, bifacial and dual glass.  Figures of a Jinko Tiger Neo
	 * JKM580N-72HL4-BDV.
	 */
	public static final PvModuleSpec HN_144_580 = new PvModuleSpec(
			id("hn_144_580"), "HN-144-580", PvModuleSpec.Cell.TOPCON, 144,
			2.278, 1.134, 0.030, 32.0,
			580.0, 42.59, 13.62, 51.47, 14.37,
			-0.0029, -0.0025, 0.00045,
			45.0, 0.80, 1500.0, 30.0);

	/**
	 * The large format: 132 half-cut cells off 210 mm wafers, 700 W, bifacial.  Figures of a Trina Vertex N
	 * TSM-700NEG21C.20.
	 */
	public static final PvModuleSpec HN_132_700 = new PvModuleSpec(
			id("hn_132_700"), "HN-132-700", PvModuleSpec.Cell.TOPCON, 132,
			2.384, 1.303, 0.033, 38.3,
			700.0, 40.50, 17.29, 48.60, 18.32,
			-0.0029, -0.0024, 0.00040,
			43.0, 0.80, 1500.0, 35.0);

	private PvModuleCatalog() {
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation(Renewables.MOD_ID, path);
	}

	/** Full designation as a nameplate prints it. */
	public static String fullName(PvModuleSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}
}
