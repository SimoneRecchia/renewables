package io.github.simonerecchia.renewables.client.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Reads the two things a material library says that a machine model uses: the names of the materials and the
 * diffuse texture file of each (docs/spec/models.md section 1.4).  Colours, opacity and illumination model are
 * ignored on purpose: the machines draw white and opaque, and transparency comes from the PNG's own pixels.
 */
final class MtlReader {
	private MtlReader() {
	}

	/** Reads one library into {@code into}: material name -> texture file, or null for a material without one. */
	static void read(Reader source, Map<String, String> into) throws IOException {
		BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
		String current = null;

		String line;
		while ((line = reader.readLine()) != null) {
			// a "#name" texture is a Forge texture variable of a chunk model, and reads as no texture at all
			line = ObjReader.stripComment(line).trim();
			if (line.isEmpty()) continue;

			String keyword = firstWord(line);
			String rest = line.substring(keyword.length()).trim();

			if (keyword.equals("newmtl")) {
				current = rest.isEmpty() ? null : rest;
				if (current != null) into.put(current, null);
			} else if (keyword.equals("map_Kd") && current != null) {
				into.put(current, textureFile(rest));
			}
		}
	}

	/** The file a {@code map_Kd} names: the last word when options precede it, otherwise the whole rest of the line. */
	@Nullable
	static String textureFile(String rest) {
		if (rest.isEmpty()) return null;

		String[] words = rest.split("\\s+");
		for (String word : words) {
			if (word.startsWith("-")) return words[words.length - 1];
		}

		return rest;
	}

	private static String firstWord(String line) {
		int end = 0;
		while (end < line.length() && !Character.isWhitespace(line.charAt(end))) end++;

		return line.substring(0, end);
	}

	/** Convenience for the tests and the loader: a fresh, ordered map. */
	static Map<String, String> newLibrary() {
		return new LinkedHashMap<>();
	}
}
