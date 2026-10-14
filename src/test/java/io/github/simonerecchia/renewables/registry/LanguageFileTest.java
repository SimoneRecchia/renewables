package io.github.simonerecchia.renewables.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.catalog.SensorCatalog;
import io.github.simonerecchia.renewables.catalog.SwitchgearCatalog;
import io.github.simonerecchia.renewables.wind.TurbineGeometry;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The figures the grey lines state, against the code that decides them.  A tooltip is written by tools/gen_core_data.py
 * and a figure by a catalogue, and nothing else holds the two together: the met mast said seven instruments for nine,
 * the switches 24 kV on a 33 kV collector.
 */
class LanguageFileTest {
	private static final String[] NUMBERS = {"Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
			"Ten", "Eleven", "Twelve"};

	private static JsonObject lang;

	@BeforeAll
	static void read() throws IOException {
		try (InputStream in = LanguageFileTest.class.getClassLoader().getResourceAsStream("assets/renewables/lang/en_us.json")) {
			assertNotNull(in, "the language file is on the test classpath");
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static String text(String key) {
		assertTrue(lang.has(key), key + " is in the language file");
		return lang.get(key).getAsString();
	}

	@Test
	void theMetMastCountsTheInstrumentsItCarries() {
		String tooltip = text("tooltip.renewables.met_station");
		String count = NUMBERS[SensorCatalog.all().size()];
		assertTrue(tooltip.startsWith(count + " instruments"), tooltip);
	}

	@Test
	void everyMediumVoltageLineSaysTheCollectorsVoltage() {
		String kv = String.format(Locale.ROOT, "%.0f kV", ConductorSpec.VoltageClass.MEDIUM.nominalVolts() / 1000.0);
		for (String key : List.of("tooltip.renewables.mv_conductor", "tooltip.renewables.mv_disconnector",
				"tooltip.renewables.mv_breaker", "tooltip.renewables.tx_machine", "tooltip.renewables.tx_substation")) {
			assertTrue(text(key).contains(kv), key + " says " + kv + ": " + text(key));
		}

		for (String key : lang.keySet()) {
			assertFalse(lang.get(key).getAsString().contains("24 kV"), key + " still names the old 24 kV class");
		}
	}

	@Test
	void theBreakerTripsWhereItsRatingSays() {
		String mw = String.format(Locale.ROOT, "above %.0f MW", SwitchgearCatalog.MV_BREAKER.ratedKw() / 1000.0);
		assertTrue(text("tooltip.renewables.mv_breaker").contains(mw), text("tooltip.renewables.mv_breaker"));
	}

	@Test
	void theTowerStatesBothHeightsItFoldsAt() {
		String tooltip = text("tooltip.renewables.turbine_tower");
		assertTrue(tooltip.contains("Above " + TurbineGeometry.SAFE_SEGMENTS + " blocks"), tooltip);
		assertTrue(tooltip.contains("above " + TurbineGeometry.MAX_SEGMENTS + " as it is built"), tooltip);
	}
}
