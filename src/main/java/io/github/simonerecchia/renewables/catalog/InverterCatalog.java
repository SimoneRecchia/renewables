package io.github.simonerecchia.renewables.catalog;

import static io.github.simonerecchia.renewables.catalog.Catalogue.id;
import io.github.simonerecchia.renewables.api.power.InverterSpec;

import java.util.List;

/**
 * The inverters the mod ships, smallest first.
  *
 * The maker is {@link #MANUFACTURER}.
 */
public final class InverterCatalog {
	/** The fictional maker whose initial the model numbers carry. */
	public static final String MANUFACTURER = "Volterra";

	private static final Catalogue<InverterSpec> INVERTERS = new Catalogue<>(InverterSpec::id);

	/**
	 * Ten kilowatts, two trackers, no fan: a Huawei SUN2000-10KTL-M1 (high-current version), cooled through its own
	 * skin.  Its peak sits high on the load curve, which is the only way a 98.6 % peak goes with a 98.1 % European.
	 */
	public static final InverterSpec VX_10 = INVERTERS.register(new InverterSpec(
			id("inverter_10"), "VX-10K",
			10.0, 11.0, 15.0,
			2, 2, 13.5, true, false, 140.0, 980.0, 1100.0, 200.0,
			0.986, 0.981, 0.60,
			5.5,
			400.0, 50.0, 0.8,
			45.0, 60.0, InverterSpec.Cooling.NATURAL));

	/**
	 * A hundred and ten kilowatts, nine trackers, 1000 volt strings: a Sungrow SG110CX, 110 kVA at 45 °C onto a
	 * 400 V grid.
	 */
	public static final InverterSpec VX_110 = INVERTERS.register(new InverterSpec(
			id("inverter_110"), "VX-110K",
			110.0, 110.0, 165.0,
			9, 18, 26.0, true, false, 200.0, 1000.0, 1100.0, 250.0,
			0.987, 0.985, 0.50,
			2.0,
			400.0, 50.0, 0.8,
			45.0, 60.0, InverterSpec.Cooling.FAN));

	/**
	 * Three hundred and fifty kilowatts, sixteen trackers, 1500 volt strings: a Sungrow SG350HX, 352 kVA at 30 °C,
	 * 320 at 40 and 295 at 50, onto 800 V.
	 */
	public static final InverterSpec VX_350 = INVERTERS.register(new InverterSpec(
			id("inverter_350"), "VX-350K",
			352.0, 352.0, 528.0,
			16, 32, 30.0, true, true, 500.0, 1500.0, 1500.0, 550.0,
			0.990, 0.988, 0.50,
			6.0,
			800.0, 50.0, 0.8,
			30.0, 60.0, InverterSpec.Cooling.FAN));

	/**
	 * Two and a half megawatts in one cabinet, one tracker for the lot: an SMA Sunny Central 2500-EV, full power to
	 * 35 °C, 3200 A of direct current, 550 V out, and 370 W to keep it awake overnight.
	 */
	public static final InverterSpec VC_2500 = INVERTERS.register(new InverterSpec(
			id("inverter_2500"), "VC-2500K",
			2500.0, 2500.0, 3125.0,
			1, 288, 3200.0, false, true, 850.0, 1425.0, 1500.0, 928.0,
			0.986, 0.983, 0.50,
			370.0,
			550.0, 50.0, 0.8,
			35.0, 60.0, InverterSpec.Cooling.FORCED_AIR));

	private InverterCatalog() {
	}

	/** Every inverter */
	public static List<InverterSpec> all() {
		return INVERTERS.all();
	}

	/** Full designation as a nameplate prints it. */
	public static String fullName(InverterSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}

	/** The machine an inverter falls back to when its saved id cannot be resolved. */
	public static InverterSpec fallback() {
		return VX_110;
	}
}
