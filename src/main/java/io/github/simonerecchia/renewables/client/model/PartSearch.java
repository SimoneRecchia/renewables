package io.github.simonerecchia.renewables.client.model;

import io.github.simonerecchia.renewables.model.PartBox;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * The questions a picker answers about one machine, with the machine already resolved to its model, its frame, its
 * list of fittings and how its renderer poses each group.  Kept apart from {@link PartPicker}, which only finds those
 * in the world, so that the answers can be tested without a level.
 *
 * Where boxes overlap along the ray the nearest wins (the smallest entry parameter); on a tie the earlier name wins,
 * which for the fittings is the lower index (docs/spec/models.md section 11.4).
 */
final class PartSearch {
	/** A group and where along the ray it was entered. */
	record Hit(@Nullable String name, double t) {
		static final Hit NONE = new Hit(null, Double.POSITIVE_INFINITY);

		boolean found() {
			return name != null;
		}
	}

	private final MeshModel model;
	private final PartFrame frame;
	private final List<String> fittings;
	private final GroupPoses.Posed posed;

	/** A machine whose groups are all drawn as authored. */
	PartSearch(MeshModel model, PartFrame frame, List<String> fittings) {
		this(model, frame, fittings, GroupPoses.AS_AUTHORED);
	}

	/** A machine whose renderer moves or scales its groups: {@code posed} says how, and which are not drawn at all. */
	PartSearch(MeshModel model, PartFrame frame, List<String> fittings, GroupPoses.Posed posed) {
		this.model = model;
		this.frame = frame;
		this.fittings = fittings;
		this.posed = posed;
	}

	/** Whether the ray passes through the box of this group. */
	boolean isPointed(String part, Vec3 origin, Vec3 direction) {
		PartBox box = model.box(part);
		return box != null && !Double.isNaN(entry(part, box, origin, direction));
	}

	/** The fitting entered first. */
	Hit fitting(Vec3 origin, Vec3 direction) {
		return nearest(fittings, origin, direction, Hit.NONE);
	}

	/** Any drawn group entered first, a fitting winning a tie with the body it sits on. */
	Hit any(Vec3 origin, Vec3 direction) {
		Hit best = fitting(origin, direction);

		for (Map.Entry<String, PartBox> entry : model.boxes().entrySet()) {
			// a marker is a point that says where a hinge is, not something a player can point at
			if (entry.getKey().startsWith("pivot_")) continue;

			double t = entry(entry.getKey(), entry.getValue(), origin, direction);
			if (!Double.isNaN(t) && t < best.t()) best = new Hit(entry.getKey(), t);
		}

		return best;
	}

	/** The middle of a group's box as it is drawn, in the world. */
	@Nullable
	Vec3 center(String part) {
		PartBox box = model.box(part);
		Matrix4fc pose = posed.of(part);
		if (box == null || pose == null) return null;

		Vector3f c = pose.transformPosition(box.center(), new Vector3f());
		return frame.toWorld(new Vec3(c.x(), c.y(), c.z()));
	}

	/** Where the ray enters the nearest group, in the world. */
	@Nullable
	Vec3 entryPoint(Vec3 origin, Vec3 direction) {
		Hit hit = any(origin, direction);
		return hit.found() ? origin.add(direction.scale(hit.t())) : null;
	}

	/** The named groups' nearest hit; a strict comparison, so the earlier name keeps a tie. */
	private Hit nearest(List<String> names, Vec3 origin, Vec3 direction, Hit start) {
		Hit best = start;
		for (String name : names) {
			PartBox box = model.box(name);
			if (box == null) continue;

			double t = entry(name, box, origin, direction);
			if (!Double.isNaN(t) && t < best.t()) best = new Hit(name, t);
		}

		return best;
	}

	/**
	 * Where the ray enters a group's box as the group is drawn, or NaN.  The ray is carried into the group's own frame
	 * by the inverse of its pose; the map is affine, so the parameter is the same along both.
	 */
	private double entry(String name, PartBox box, Vec3 worldOrigin, Vec3 worldDirection) {
		Matrix4fc pose = posed.of(name);
		if (pose == null) return Double.NaN;
		if (pose == GroupPoses.IDENTITY) return frame.entry(box, worldOrigin, worldDirection);

		Vec3 o = frame.toLocalPoint(worldOrigin);
		Vec3 d = frame.toLocalDirection(worldDirection);
		Matrix4f inverse = pose.invertAffine(new Matrix4f());
		Vector3f lo = inverse.transformPosition((float) o.x, (float) o.y, (float) o.z, new Vector3f());
		Vector3f ld = inverse.transformDirection((float) d.x, (float) d.y, (float) d.z, new Vector3f());

		return Rays.entry(lo.x, lo.y, lo.z, ld.x, ld.y, ld.z,
				box.min().x(), box.min().y(), box.min().z(), box.max().x(), box.max().y(), box.max().z());
	}
}
