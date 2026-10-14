package io.github.simonerecchia.renewables.client.model;

import io.github.simonerecchia.renewables.model.PartBox;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

/**
 * A loaded OBJ model: one group per (object, material) pair, named {@code object_material} (docs/spec/models.md
 * section 2), with the vertices already triangulated and de-indexed, and the box of every group worked out once.
 *
 * The groups are the unit everything else works in.  A renderer poses and hides them by name or prefix, the picker
 * tests their boxes, and the generator of {@code fittings.json} writes them.  They keep the order in which the file
 * first used them, so drawing and logs are the same from one run to the next.
 *
 * Nothing in here touches the game: the class is built from text and can be read without Minecraft running.
 */
@OnlyIn(Dist.CLIENT)
public final class MeshModel {
	/** Groups by name, in file order.  Read-only. */
	public final Map<String, Group> groups;

	private final Map<String, PartBox> boxes;
	private final List<String> warnings;

	MeshModel(Map<String, Group> groups, List<String> warnings) {
		this.groups = Collections.unmodifiableMap(groups);
		this.warnings = List.copyOf(warnings);

		Map<String, PartBox> computed = new LinkedHashMap<>();
		for (Group group : groups.values()) {
			if (group.box != null) computed.put(group.name, group.box);
		}
		this.boxes = Collections.unmodifiableMap(computed);
	}

	/** The group names, in file order. */
	public Set<String> names() {
		return groups.keySet();
	}

	@Nullable
	public Group group(String name) {
		return groups.get(name);
	}

	/** The box of one group in the model's frame, or null if there is no such group or it has no vertices. */
	@Nullable
	public PartBox box(String name) {
		return boxes.get(name);
	}

	/** Every group's box, computed once when the model was built. */
	public Map<String, PartBox> boxes() {
		return boxes;
	}

	/** The box round every group whose name starts with {@code prefix}, or null when none has any vertex. */
	@Nullable
	public PartBox union(String prefix) {
		Vector3f min = null;
		Vector3f max = null;

		for (Map.Entry<String, PartBox> entry : boxes.entrySet()) {
			if (!entry.getKey().startsWith(prefix)) continue;

			PartBox box = entry.getValue();
			if (min == null) {
				min = new Vector3f(box.min());
				max = new Vector3f(box.max());
			} else {
				min.min(box.min());
				max.max(box.max());
			}
		}

		return min == null ? null : new PartBox(min, max);
	}

	/** The middle of {@link #union}, or null. */
	@Nullable
	public Vector3f centerOf(String prefix) {
		PartBox box = union(prefix);
		return box == null ? null : box.center();
	}

	/**
	 * What went slightly wrong while building: a missing material library, two groups that collided on one key, a
	 * material with no texture.  The cache logs each once; the model is usable regardless.
	 */
	public List<String> warnings() {
		return warnings;
	}

	/** One drawable group of a model: the vertices of one object in one material. */
	public static final class Group {
		/** {@code object_material}, or just the material for a file with no named objects. */
		public final String name;
		public final String object;
		/** The material name, {@code default} for faces that were not given one. */
		public final String material;
		/** The {@code map_Kd} file of the material, or null when it has none; the folder and namespace are the renderer's. */
		@Nullable
		public final String textureFile;
		/** x, y, z per vertex; three vertices make a triangle. */
		public final float[] positions;
		/** x, y, z per vertex, as declared or the face's flat normal. */
		public final float[] normals;
		/** u, v per vertex, with v already flipped to the PNG's convention. */
		public final float[] uvs;
		/** Min and max of every vertex, or null for a group with none. */
		@Nullable
		public final PartBox box;

		Group(String name, String object, String material, @Nullable String textureFile, float[] positions, float[] normals,
				float[] uvs) {
			this.name = name;
			this.object = object;
			this.material = material;
			this.textureFile = textureFile;
			this.positions = positions;
			this.normals = normals;
			this.uvs = uvs;
			this.box = boxOf(positions);
		}

		public int vertexCount() {
			return positions.length / 3;
		}

		/** A marker group: the one-point faces the generators write to say where a hinge or a hub is.  Never drawn. */
		public boolean isMarker() {
			return name.startsWith("pivot_");
		}

		@Nullable
		private static PartBox boxOf(float[] positions) {
			if (positions.length == 0) return null;

			float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
			float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
			for (int i = 0; i < positions.length; i += 3) {
				minX = Math.min(minX, positions[i]);
				minY = Math.min(minY, positions[i + 1]);
				minZ = Math.min(minZ, positions[i + 2]);
				maxX = Math.max(maxX, positions[i]);
				maxY = Math.max(maxY, positions[i + 1]);
				maxZ = Math.max(maxZ, positions[i + 2]);
			}

			Vector3f min = new Vector3f(minX, minY, minZ);
			Vector3f max = new Vector3f(maxX, maxY, maxZ);
			return new PartBox(min, max);
		}
	}
}
