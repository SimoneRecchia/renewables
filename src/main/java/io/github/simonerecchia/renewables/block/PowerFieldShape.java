package io.github.simonerecchia.renewables.block;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * The offsets a power box's field covers: every integer point within a radius of it, except its own.
 *
 * A sphere of whole blocks, not a cube and not a rounded-off one: an offset is in the field when
 * {@code dx^2 + dy^2 + dz^2 <= radius^2}.  That is 6 blocks at radius 1, 514 at 5 (the default) and 17 076 at
 * 16 (the largest the configuration allows), and the box walks all of them every tick or two while it is on, so
 * the list is worked out once per radius and handed out as one flat array.
 *
 * No Minecraft types, so the counts above are a test.
 */
public final class PowerFieldShape {
	private static final Map<Integer, int[]> CACHE = new ConcurrentHashMap<>();

	private PowerFieldShape() {
	}

	/**
	 * {@code x0, y0, z0, x1, y1, z1, ...} for every offset in the sphere, nearest-axis order not promised.
	 * The array is shared: do not write to it.
	 */
	public static int[] offsets(int radius) {
		int r = Math.max(1, radius);
		return CACHE.computeIfAbsent(r, PowerFieldShape::build);
	}

	/** How many offsets that is, which is the length of {@link #offsets} over three. */
	public static int count(int radius) {
		return offsets(radius).length / 3;
	}

	private static int[] build(int r) {
		int limit = r * r;
		int[] out = new int[3 * 17_100];
		int size = 0;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if ((dx == 0 && dy == 0 && dz == 0) || dx * dx + dy * dy + dz * dz > limit) continue;

					if (size + 3 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
					out[size++] = dx;
					out[size++] = dy;
					out[size++] = dz;
				}
			}
		}

		return java.util.Arrays.copyOf(out, size);
	}
}
