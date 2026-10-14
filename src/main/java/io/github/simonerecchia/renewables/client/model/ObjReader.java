package io.github.simonerecchia.renewables.client.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import javax.annotation.Nullable;

/**
 * Reads the subset of OBJ the project's generators write (docs/spec/models.md section 1).
 *
 * The reader is deliberately forgiving: a malformed number is zero, a face with a bad position index is dropped, an
 * index into the texture or normal list that is out of range only costs that vertex its UV or normal.  A model
 * with one bad line should lose that line, not the whole machine.  Unknown statements are ignored without a word,
 * and a face with no area is kept, because the {@code pivot_*} markers are exactly that.
 */
final class ObjReader {
	private ObjReader() {
	}

	static ObjMesh read(Reader source) throws IOException {
		ObjMesh mesh = new ObjMesh();
		String object = ObjMesh.DEFAULT_OBJECT;
		String material = null;

		BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
		String line;
		while ((line = reader.readLine()) != null) {
			line = stripComment(line).trim();
			if (line.isEmpty()) continue;

			String[] tokens = line.split("\\s+");
			switch (tokens[0]) {
				case "v" -> readTriple(mesh.positions, tokens);
				case "vn" -> readTriple(mesh.normals, tokens);
				case "vt" -> {
					mesh.uvs.add(number(tokens, 1));
					mesh.uvs.add(number(tokens, 2));
				}
				case "o", "g" -> object = tokens.length > 1 ? tokens[1] : ObjMesh.DEFAULT_OBJECT;
				// the current material survives a change of object: it is only an usemtl that changes it
				case "usemtl" -> material = tokens.length > 1 ? tokens[1] : null;
				case "mtllib" -> {
					for (int i = 1; i < tokens.length; i++) mesh.materialLibraries.add(tokens[i]);
				}
				case "f" -> {
					ObjMesh.Face face = readFace(mesh, tokens, object, material);
					if (face != null) mesh.faces.add(face);
				}
				default -> {
					// s, l, p, vp, curves and the rest are not part of what a machine model uses
				}
			}
		}

		return mesh;
	}

	/** Everything from the first {@code #} on is a comment, a trailing one included. */
	static String stripComment(String line) {
		int hash = line.indexOf('#');
		return hash < 0 ? line : line.substring(0, hash);
	}

	private static void readTriple(ObjMesh.Floats into, String[] tokens) {
		// only the first three components count: a fourth is a weight, and three more a vertex colour
		into.add(number(tokens, 1));
		into.add(number(tokens, 2));
		into.add(number(tokens, 3));
	}

	/** A component, or 0 when it is missing, not a number, or not finite. */
	private static float number(String[] tokens, int index) {
		if (index >= tokens.length) return 0.0f;

		try {
			float value = Float.parseFloat(tokens[index]);
			return Float.isFinite(value) ? value : 0.0f;
		} catch (NumberFormatException e) {
			return 0.0f;
		}
	}

	@Nullable
	private static ObjMesh.Face readFace(ObjMesh mesh, String[] tokens, String object, @Nullable String material) {
		int count = tokens.length - 1;
		if (count < 3) return null;

		int[] position = new int[count];
		int[] uv = new int[count];
		int[] normal = new int[count];

		for (int i = 0; i < count; i++) {
			String token = tokens[i + 1];
			int firstSlash = token.indexOf('/');
			int secondSlash = firstSlash < 0 ? -1 : token.indexOf('/', firstSlash + 1);

			String p = firstSlash < 0 ? token : token.substring(0, firstSlash);
			String t = firstSlash < 0 ? "" : token.substring(firstSlash + 1, secondSlash < 0 ? token.length() : secondSlash);
			String n = secondSlash < 0 ? "" : token.substring(secondSlash + 1);

			position[i] = resolve(p, mesh.positionCount());
			// one unusable position and the whole face is dropped
			if (position[i] < 0) return null;

			uv[i] = resolve(t, mesh.uvCount());
			normal[i] = resolve(n, mesh.normalCount());
		}

		return new ObjMesh.Face(position, uv, normal, object, material);
	}

	/**
	 * A 1-based index, or a negative one counting back from the end of the list as it stands now, as a 0-based
	 * index; -1 when it is empty, not a number, zero or out of range.
	 */
	static int resolve(String text, int size) {
		if (text.isEmpty()) return -1;

		int index;
		try {
			index = Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return -1;
		}

		int zeroBased = index < 0 ? size + index : index - 1;
		return index == 0 || zeroBased < 0 || zeroBased >= size ? -1 : zeroBased;
	}
}
