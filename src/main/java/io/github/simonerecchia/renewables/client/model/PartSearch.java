package io.github.simonerecchia.renewables.client.model;

import io.github.simonerecchia.renewables.model.PartBox;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * The questions a picker answers about one machine, with the machine already resolved to its model, its frame and its
 * list of fittings.  Kept apart from {@link PartPicker}, which only finds those three in the world, so that the
 * answers can be tested without a level.
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

	PartSearch(MeshModel model, PartFrame frame, List<String> fittings) {
		this.model = model;
		this.frame = frame;
		this.fittings = fittings;
	}

	/** Whether the ray passes through the box of this group. */
	boolean isPointed(String part, Vec3 origin, Vec3 direction) {
		PartBox box = model.box(part);
		return box != null && !Double.isNaN(frame.entry(box, origin, direction));
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

			double t = frame.entry(entry.getValue(), origin, direction);
			if (!Double.isNaN(t) && t < best.t()) best = new Hit(entry.getKey(), t);
		}

		return best;
	}

	/** The middle of a group's box, in the world. */
	@Nullable
	Vec3 center(String part) {
		PartBox box = model.box(part);
		if (box == null) return null;

		return frame.toWorld(new Vec3(box.center().x(), box.center().y(), box.center().z()));
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

			double t = frame.entry(box, origin, direction);
			if (!Double.isNaN(t) && t < best.t()) best = new Hit(name, t);
		}

		return best;
	}
}
