package io.github.simonerecchia.renewables.client.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads an OBJ and the material libraries it names into a {@link MeshModel}.
 *
 * It asks a {@link Source} for bytes and knows nothing else about where they come from, which is what lets the
 * tests feed it the real files from the classpath while the game feeds it the resource manager.
 */
final class ObjModelLoader {
	/** Opens a file by its path inside the mod's assets, e.g. {@code models/utility_pole/utility_pole.obj}. */
	@FunctionalInterface
	interface Source {
		/** The file's bytes; an IOException (or FileNotFoundException) when it is not there. */
		InputStream open(String path) throws IOException;
	}

	private ObjModelLoader() {
	}

	/**
	 * @throws IOException if the OBJ itself cannot be read.  A missing material library is not an error: it is a
	 *                     warning on the model, and the groups that wanted it fall back to the block's texture.
	 */
	static MeshModel load(String objPath, Source source) throws IOException {
		ObjMesh mesh;
		try (InputStream stream = open(source, objPath); Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			mesh = ObjReader.read(reader);
		}

		List<String> warnings = new ArrayList<>();
		Map<String, String> materials = MtlReader.newLibrary();
		String folder = objPath.substring(0, objPath.lastIndexOf('/') + 1);

		for (String library : mesh.materialLibraries) {
			// later libraries win on a repeated material name
			try (InputStream stream = open(source, folder + library); Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				MtlReader.read(reader, materials);
			} catch (IOException | RuntimeException e) {
				warnings.add(objPath + ": material library \"" + library + "\" could not be read (" + e + ")");
			}
		}

		return ModelBuilder.build(objPath, mesh, materials, warnings);
	}

	private static InputStream open(Source source, String path) throws IOException {
		InputStream stream = source.open(path);
		if (stream == null) throw new java.io.FileNotFoundException(path);

		return stream;
	}
}
