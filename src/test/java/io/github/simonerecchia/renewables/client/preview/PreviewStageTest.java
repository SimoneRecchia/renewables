package io.github.simonerecchia.renewables.client.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The stage's commands are read by the server's parser, which wants a decimal point whatever the system speaks. */
class PreviewStageTest {
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
	void theTeleportIsWrittenWithDecimalPointsOnAnItalianSystem() {
		String command = PreviewStage.teleport(new PreviewStage.View("front", 0.0f, 8.0f, 7.0, 1.6));

		assertEquals("tp @s 512.50 97.60 505.50 0.0 8.0", command);
	}

	@Test
	void theFillsAreWrittenInPlainDigits() {
		assertEquals("fill 502 96 502 522 120 522 air",
				PreviewStage.command("fill %d %d %d %d %d %d air", 502, 96, 502, 522, 120, 522));
	}
}
