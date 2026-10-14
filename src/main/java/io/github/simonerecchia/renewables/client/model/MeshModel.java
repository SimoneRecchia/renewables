package io.github.simonerecchia.renewables.client.model;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A loaded OBJ model: one group per (object, material) pair, named {@code object_material}.
 *
 * STUB: owner M.  Only what the clean renderers read exists: the group map, iterated for names.  The real
 * groups carry vertices, normals, UVs, their texture and their box (docs/spec/models.md section 2).
 */
@OnlyIn(Dist.CLIENT)
public final class MeshModel {
	/** Groups by name, in file order. */
	public final Map<String, Group> groups = new LinkedHashMap<>();

	/** One drawable group of a model. */
	public static final class Group {
	}
}
