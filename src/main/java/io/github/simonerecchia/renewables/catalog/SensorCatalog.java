package io.github.simonerecchia.renewables.catalog;

import static io.github.simonerecchia.renewables.catalog.Catalogue.id;
import io.github.simonerecchia.renewables.api.power.SensorSpec;

import java.util.List;

/** The instruments a photovoltaic plant is monitored with. */
public final class SensorCatalog {
	/** The fictional instrument maker. */
	public static final String MANUFACTURER = "Kelvinsen";

	private static final Catalogue<SensorSpec> SENSORS = new Catalogue<>(SensorSpec::id);

	/** Class A pyranometer: global irradiance, 285 to 2800 nanometres.  A Kipp & Zonen CMP11's figures. */
	public static final SensorSpec SP_11 = SENSORS.register(new SensorSpec(
			id("sensor_pyranometer"), "SP-11", SensorSpec.Instrument.PYRANOMETER,
			"ISO 9060 Class A", 285.0, 2800.0,
			5.0, 2.0, 0.0, 4000.0, "W/m2"));

	/** Albedometer: two Class A pyranometers back to back */
	public static final SensorSpec SA_11 = SENSORS.register(new SensorSpec(
			id("sensor_albedometer"), "SA-11", SensorSpec.Instrument.ALBEDOMETER,
			"ISO 9060 Class A", 285.0, 2800.0,
			5.0, 3.0, 0.0, 1.0, ""));

	/** Class A pyranometer under a shadow ring: the diffuse component alone. */
	public static final SensorSpec SD_11 = SENSORS.register(new SensorSpec(
			id("sensor_diffuse"), "SD-11", SensorSpec.Instrument.DIFFUSE_PYRANOMETER,
			"ISO 9060 Class A", 285.0, 2800.0,
			5.0, 3.5, 0.0, 2000.0, "W/m2"));

	/**
	 * Reference cell: a single crystalline cell short-circuited across a shunt.  It answers in a tenth of a second
	 * (a Mencke & Tegtmeyer Si-V-10TC reaches 99 % in 0.15 s) and is calibrated to the 3 % IEC 61724-1 asks.
	 */
	public static final SensorSpec RC_1 = SENSORS.register(new SensorSpec(
			id("sensor_reference_cell"), "RC-1", SensorSpec.Instrument.REFERENCE_CELL,
			"IEC 60904-2 secondary", 350.0, 1150.0,
			0.1, 3.0, 0.0, 1500.0, "W/m2"));

	/**
	 * Platinum resistance thermometer, Class A, on the back sheet of a representative module.  Taped to glass it has
	 * a time constant of about 25 s (Campbell 110PV), so three of them to reach 95 %.
	 */
	public static final SensorSpec MT_1000 = SENSORS.register(new SensorSpec(
			id("sensor_module_temp"), "MT-1000", SensorSpec.Instrument.MODULE_TEMPERATURE,
			"IEC 60751 Class A", 0.0, 0.0,
			75.0, 0.5, -40.0, 110.0, "C"));

	/**
	 * Air temperature in a naturally aspirated radiation shield.  A Vaisala HMP155's probe reaches 63 % in 20 s in a
	 * 3 m/s breeze, so about a minute to 95 %.
	 */
	public static final SensorSpec AT_7 = SENSORS.register(new SensorSpec(
			id("sensor_ambient_temp"), "AT-7", SensorSpec.Instrument.AMBIENT_TEMPERATURE,
			"IEC 60751 Class A", 0.0, 0.0,
			60.0, 0.3, -50.0, 60.0, "C"));

	/** Ultrasonic snow gauge: distance to the surface below, 0.5 to 10 m, as a Campbell SR50A. */
	public static final SensorSpec SN_50 = SENSORS.register(new SensorSpec(
			id("sensor_snow"), "SN-50", SensorSpec.Instrument.SNOW_DEPTH,
			"ultrasonic", 0.0, 0.0,
			1.0, 1.0, 0.5, 10.0, "m"));

	/**
	 * Cup anemometer, as a Thies First Class Advanced II: class 1.8A to IEC 61400-12-1:2017, 0.3 to 75 m/s, 1 % of
	 * reading, and a distance constant under 3 m - under a second to 95 % in a 10 m/s wind.
	 */
	public static final SensorSpec WS_3 = SENSORS.register(new SensorSpec(
			id("sensor_anemometer"), "WS-3", SensorSpec.Instrument.ANEMOMETER,
			"IEC 61400-12-1 class 1.8A", 0.0, 0.0,
			1.0, 1.0, 0.0, 75.0, "m/s"));

	/**
	 * Wind vane, as a Thies First Class wind direction transmitter: ±1°, a 1.8 m distance constant, and a start
	 * at 0.5 m/s to ASTM D5096.  Vanes have no IEC 61400-12-1 class of their own.
	 */
	public static final SensorSpec WV_3 = SENSORS.register(new SensorSpec(
			id("sensor_vane"), "WV-3", SensorSpec.Instrument.WIND_VANE,
			"ASTM D5096, ±1°", 0.0, 0.0,
			1.0, 1.0, 0.0, 360.0, "deg"));

	private SensorCatalog() {
	}

	public static List<SensorSpec> all() {
		return SENSORS.all();
	}

	/** Full designation as a calibration certificate prints it. */
	public static String fullName(SensorSpec spec) {
		return MANUFACTURER + " " + spec.displayName();
	}
}
