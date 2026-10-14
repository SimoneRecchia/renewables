package io.github.simonerecchia.renewables.client.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns what the reader found into groups (docs/spec/models.md section 2): splits the faces by object and material,
 * triangulates them as fans, and de-indexes positions, normals and UVs into flat arrays a vertex buffer can take.
 */
final class ModelBuilder {
	private static final String DEFAULT_MATERIAL = "default";

	private ModelBuilder() {
	}

	/**
	 * @param materials material name -> texture file (null where a material has none), as read from the libraries
	 * @param warnings  where problems that do not stop the model go
	 */
	static MeshModel build(String source, ObjMesh mesh, Map<String, String> materials, List<String> warnings) {
		boolean named = false;
		for (ObjMesh.Face face : mesh.faces) {
			if (!face.object.equals(ObjMesh.DEFAULT_OBJECT)) {
				named = true;
				break;
			}
		}

		Map<String, Pending> pending = new LinkedHashMap<>();
		for (ObjMesh.Face face : mesh.faces) {
			String material = face.material == null || face.material.isEmpty() ? DEFAULT_MATERIAL : face.material;
			// no object named anywhere: the material is all there is to tell the parts apart
			String object = named ? face.object : ObjMesh.DEFAULT_OBJECT;
			String key = named ? object + "_" + material : material;

			Pending group = pending.get(key);
			if (group == null) {
				group = new Pending(key, object, material);
				pending.put(key, group);
			} else if (!group.collided && (!group.object.equals(object) || !group.material.equals(material))) {
				// object "a_b" in material "c" and object "a" in material "b_c" are one key.  Merging keeps every face.
				warnings.add(source + ": group \"" + key + "\" is both object \"" + group.object + "\" in material \""
						+ group.material + "\" and object \"" + object + "\" in material \"" + material + "\": merged");
				group.collided = true;
			}

			group.add(mesh, face);
		}

		Map<String, MeshModel.Group> groups = new LinkedHashMap<>();
		Set<String> textureless = new LinkedHashSet<>();
		for (Pending group : pending.values()) {
			String texture = materials.get(group.material);
			if (texture == null) textureless.add(group.material);

			groups.put(group.key, new MeshModel.Group(group.key, group.object, group.material, texture,
					group.positions.toArray(), group.normals.toArray(), group.uvs.toArray()));
		}

		for (String material : textureless) {
			warnings.add(source + ": material \"" + material + "\" has no texture (not in a library, or no map_Kd): "
					+ "its groups use the block's fallback texture");
		}

		return new MeshModel(groups, warnings);
	}

	/** A group while its faces are still being added. */
	private static final class Pending {
		final String key;
		final String object;
		final String material;
		final Floats positions = new Floats();
		final Floats normals = new Floats();
		final Floats uvs = new Floats();
		boolean collided;

		Pending(String key, String object, String material) {
			this.key = key;
			this.object = object;
			this.material = material;
		}

		void add(ObjMesh mesh, ObjMesh.Face face) {
			// a fan from vertex 0: right for the convex polygons the generators write, and the order of the file
			for (int i = 1; i < face.position.length - 1; i++) {
				triangle(mesh, face, 0, i, i + 1);
			}
		}

		private void triangle(ObjMesh mesh, ObjMesh.Face face, int a, int b, int c) {
			int[] corners = {a, b, c};

			float[] flat = null;
			if (!face.hasNormals) flat = flatNormal(mesh, face, a, b, c);

			for (int corner : corners) {
				int p = face.position[corner] * 3;
				positions.add(mesh.positions.get(p));
				positions.add(mesh.positions.get(p + 1));
				positions.add(mesh.positions.get(p + 2));

				if (flat != null) {
					normals.add(flat[0]);
					normals.add(flat[1]);
					normals.add(flat[2]);
				} else {
					int n = face.normal[corner] * 3;
					normals.add(mesh.normals.get(n));
					normals.add(mesh.normals.get(n + 1));
					normals.add(mesh.normals.get(n + 2));
				}

				if (face.hasUvs) {
					int t = face.uv[corner] * 2;
					uvs.add(mesh.uvs.get(t));
					// the file's v counts up from the bottom of the picture, the PNG's counts down
					uvs.add(1.0f - mesh.uvs.get(t + 1));
				} else {
					uvs.add(0.0f);
					uvs.add(0.0f);
				}
			}
		}

		/** (v1 - v0) x (v2 - v0), normalised; straight up for a triangle with no area, which has no direction. */
		private static float[] flatNormal(ObjMesh mesh, ObjMesh.Face face, int a, int b, int c) {
			int p0 = face.position[a] * 3;
			int p1 = face.position[b] * 3;
			int p2 = face.position[c] * 3;

			double ux = mesh.positions.get(p1) - mesh.positions.get(p0);
			double uy = mesh.positions.get(p1 + 1) - mesh.positions.get(p0 + 1);
			double uz = mesh.positions.get(p1 + 2) - mesh.positions.get(p0 + 2);
			double vx = mesh.positions.get(p2) - mesh.positions.get(p0);
			double vy = mesh.positions.get(p2 + 1) - mesh.positions.get(p0 + 1);
			double vz = mesh.positions.get(p2 + 2) - mesh.positions.get(p0 + 2);

			double x = uy * vz - uz * vy;
			double y = uz * vx - ux * vz;
			double z = ux * vy - uy * vx;
			double length = Math.sqrt(x * x + y * y + z * z);
			if (!(length > 0.0) || Double.isInfinite(length)) return new float[] {0.0f, 1.0f, 0.0f};

			return new float[] {(float) (x / length), (float) (y / length), (float) (z / length)};
		}
	}

	/** A growable float array. */
	private static final class Floats {
		private float[] data = new float[96];
		private int size;

		void add(float value) {
			if (size == data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
			data[size++] = value;
		}

		float[] toArray() {
			return java.util.Arrays.copyOf(data, size);
		}
	}
}
