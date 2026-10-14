package io.github.simonerecchia.renewables.catalog;

import static io.github.simonerecchia.renewables.catalog.Catalogue.id;
import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.ConductorSpec.VoltageClass;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * The three overhead line conductors, which are three real products.  Resistances are the DC figures at 20 °C,
 * which is what a loss is worked from; currents are the whole phase's, for the bundle as a whole.
 */
public final class ConductorCatalog {
	private static final Catalogue<ConductorSpec> CONDUCTORS = new Catalogue<>(ConductorSpec::id);

	/** Aerial bundled cable, 4 by 70 mm2 aluminium, 0.6/1 kV (HD 626): 0.443 ohm/km, 205 A in air. */
	public static final ConductorSpec ABC_70 = CONDUCTORS.register(new ConductorSpec(
			id("abc_conductor"), "NFA2X 4x70",
			VoltageClass.LOW,
			70.0, 1000.0, 205.0, 0.443,
			1, 0.0,
			0.055, 0x232428,
			0.055, 40.0, 4));

	/**
	 * All-aluminium-alloy conductor, 228 mm2, bare (EN 50182 / NF C 34-125): 0.146 ohm/km, 480 A for a 25 K rise
	 * over 30 °C air.  Strung on a 33 kV collector, so on insulators for the 36 kV it is built to.
	 */
	public static final ConductorSpec AAAC_228 = CONDUCTORS.register(new ConductorSpec(
			id("mv_conductor"), "Aster 228",
			VoltageClass.MEDIUM,
			228.0, 36000.0, 480.0, 0.146,
			1, 0.0,
			0.032, 0x9BA0A6,
			0.030, 90.0, 8));

	/**
	 * Aluminium conductor steel-reinforced, 4 by 592 mm2 - a quad Curlew bundle.  One Curlew is 0.0542 ohm/km at
	 * 20 °C and carries 1046 A at 75 °C; four in parallel are 0.0136 ohm/km and 4184 A a phase.
	 */
	public static final ConductorSpec ACSR_592_QUAD = CONDUCTORS.register(new ConductorSpec(
			id("hv_conductor"), "Curlew ACSR, quad",
			VoltageClass.HIGH,
			592.0, 420000.0, 4184.0, 0.0136,
			// The spacing has to exceed the sub-conductors' own diameter, and at 0.045 it did not: four
			// wires 0.052 across sitting 0.045 apart is a bundle drawn through itself, strung and laid.
			// Real quad Curlew is 450 mm on a 31.6 mm conductor; this one is drawn ten times oversize and
			// the spacing is not, which is what keeps a bundle a bundle at the size it is seen from.
			4, 0.075,
			0.026, 0xA8ADB4,
			0.022, 160.0, 16));

	private ConductorCatalog() {
	}

	public static List<ConductorSpec> all() {
		return CONDUCTORS.all();
	}


	/** The spec a saved connection names, or null if it names something this build has not got. */
	@Nullable
	public static ConductorSpec byPath(String path) {
		if (path == null || path.isEmpty() || path.equals("default")) return null;

		return CONDUCTORS.byId(path.indexOf(':') >= 0 ? new ResourceLocation(path) : id(path));
	}

}
