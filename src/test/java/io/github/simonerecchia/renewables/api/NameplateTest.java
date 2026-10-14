package io.github.simonerecchia.renewables.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.simonerecchia.renewables.api.power.TransformerSpec;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A figure a player reads is written with a decimal point whatever the system speaks: the panels always were, and
 * a tooltip or a nameplate reading beside them with a comma was the same number in two spellings.
 */
class NameplateTest {
	private Locale saved;

	@BeforeEach
	void italian() {
		saved = Locale.getDefault();
		Locale.setDefault(Locale.ITALY);
	}

	@AfterEach
	void restore() {
		Locale.setDefault(saved);
	}

	@Test
	void aDatasheetFigureIsBareWhenWholeAndHasAPointWhenNot() {
		assertEquals("6", Nameplate.figure(6.0));
		assertEquals("1500", Nameplate.figure(1500.0));
		assertEquals("2.5", Nameplate.figure(2.5));
	}

	@Test
	void aTransformersRatioIsWrittenWithPoints() {
		TransformerSpec spec = new TransformerSpec(new ResourceLocation("renewables", "test"), "test",
				TransformerSpec.Duty.MACHINE, 1000.0, 11_000.0, 400.0, 10.0, 1.0, "test", 3);

		assertEquals("11.0/0.400 kV", spec.ratioText());
	}
}
