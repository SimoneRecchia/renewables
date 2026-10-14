package io.github.simonerecchia.renewables.model;

import org.joml.Vector3f;

/**
 * The bounding box of one named group of a machine's model, in the model's own frame: blocks, x and z
 * centred on the block's vertical axis, y up from its floor, facing as authored.
 */
public record PartBox(Vector3f min, Vector3f max) {
	/** The middle of the box. */
	public Vector3f center() {
		return new Vector3f(min).add(max).mul(0.5f);
	}

	/** Where a conductor rests on a vertical fitting: the middle of the top face. */
	public Vector3f top() {
		return new Vector3f((min.x() + max.x()) * 0.5f, max.y(), (min.z() + max.z()) * 0.5f);
	}
}
