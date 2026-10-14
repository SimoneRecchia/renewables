package io.github.simonerecchia.renewables.client.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * What an OBJ file states, and nothing more: the three global attribute lists, the faces as polygons that
 * still remember which object and material they were written under, and the material libraries named.
 *
 * Nothing here knows about Minecraft, so the reader can be tested against the real files without a game.
 */
final class ObjMesh {
	/** Name every face carries until the file says an {@code o} or a {@code g}. */
	static final String DEFAULT_OBJECT = "default";

	final Floats positions = new Floats();
	final Floats uvs = new Floats();
	final Floats normals = new Floats();
	final List<Face> faces = new ArrayList<>();
	final List<String> materialLibraries = new ArrayList<>();

	int positionCount() {
		return positions.size() / 3;
	}

	int uvCount() {
		return uvs.size() / 2;
	}

	int normalCount() {
		return normals.size() / 3;
	}

	/**
	 * One polygon.  Indices are zero-based into the lists above; {@code uv} and {@code normal} entries are -1 where
	 * the file gave none (or gave one out of range), and a face is only "textured" or "normalled" when every vertex is.
	 */
	static final class Face {
		final int[] position;
		final int[] uv;
		final int[] normal;
		final String object;
		@Nullable
		final String material;
		final boolean hasUvs;
		final boolean hasNormals;

		Face(int[] position, int[] uv, int[] normal, String object, @Nullable String material) {
			this.position = position;
			this.uv = uv;
			this.normal = normal;
			this.object = object;
			this.material = material;
			this.hasUvs = all(uv);
			this.hasNormals = all(normal);
		}

		private static boolean all(int[] indices) {
			for (int index : indices) {
				if (index < 0) return false;
			}

			return true;
		}
	}

	/** A growable list of floats, because a model holds tens of thousands and boxing each one is a waste. */
	static final class Floats {
		private float[] data = new float[1024];
		private int size;

		void add(float value) {
			if (size == data.length) data = Arrays.copyOf(data, data.length * 2);
			data[size++] = value;
		}

		float get(int index) {
			return data[index];
		}

		int size() {
			return size;
		}
	}
}
