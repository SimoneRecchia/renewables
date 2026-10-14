package io.github.simonerecchia.renewables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The mods.toml the jar ships, as processResources expands it, against what the README promises.  A launcher and a
 * modpack read the toml and a player reads the README; they said All Rights Reserved and "to be decided", and the toml
 * admitted Minecraft 1.20.2-1.20.6 and Forge 48 on, where the APIs the mod calls are gone.
 */
class ModMetadataTest {
	private static String toml;
	private static String readme;

	@BeforeAll
	static void read() throws IOException {
		try (InputStream in = ModMetadataTest.class.getClassLoader().getResourceAsStream("META-INF/mods.toml")) {
			assertNotNull(in, "the expanded mods.toml is on the test classpath");
			toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		readme = Files.readString(Path.of("README.md"));
	}

	/** The versionRange of the dependency on {@code modId}. */
	private static String range(String modId) {
		Matcher m = Pattern.compile("modId=\"" + modId + "\"\\s+mandatory=\\w+\\s+versionRange=\"([^\"]+)\"").matcher(toml);
		assertTrue(m.find(), "mods.toml declares a dependency on " + modId);
		return m.group(1);
	}

	@Test
	void theLicenceIsTheReadmesPlaceholder() {
		Matcher licence = Pattern.compile("(?m)^license=\"([^\"]*)\"").matcher(toml);
		assertTrue(licence.find(), "mods.toml has a licence");
		Matcher section = Pattern.compile("## License\\s+(.+?)\\.").matcher(readme);
		assertTrue(section.find(), "the README has a License section");
		assertEquals(section.group(1), licence.group(1));
	}

	@Test
	void onlyTheMinecraftItIsBuiltFor() {
		assertEquals("[1.20.1]", range("minecraft"));
	}

	@Test
	void forgeFromTheReadmesVersionAndNotIntoTheNextMajor() {
		Matcher readmeForge = Pattern.compile("\\*\\*Forge\\*\\* (\\d+)\\.(\\d+)").matcher(readme);
		assertTrue(readmeForge.find(), "the README names a Forge version");
		String major = readmeForge.group(1);
		assertEquals("[" + major + "." + readmeForge.group(2) + "," + (Integer.parseInt(major) + 1) + ")", range("forge"));
	}
}
