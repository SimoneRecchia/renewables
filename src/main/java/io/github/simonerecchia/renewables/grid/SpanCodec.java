package io.github.simonerecchia.renewables.grid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * How a level's spans are written into the world.
 *
 * The root has a {@code version} and a list of spans; a span is a compound of the two ids, the conductor's
 * name, the two block positions (packed longs), the two machine types, the two power roles, and the reels it cost.
 * Reading is forgiving on purpose: a span that is missing a field is skipped and reported, never allowed to stop
 * the world loading, and a version newer than this build is read as far as it can be understood.  An unknown
 * conductor name is kept as it is - the span survives a conductor being removed from the mod, it just cannot be
 * refunded.
 */
final class SpanCodec {
	static final int VERSION = 1;

	private static final String K_VERSION = "version";
	private static final String K_SPANS = "spans";
	private static final String K_START = "start";
	private static final String K_END = "end";
	private static final String K_WIRE = "wire";
	private static final String K_START_POS = "startPos";
	private static final String K_END_POS = "endPos";
	private static final String K_START_TYPE = "startType";
	private static final String K_END_TYPE = "endType";
	private static final String K_START_POWER = "startPower";
	private static final String K_END_POWER = "endPower";
	private static final String K_CHARGED = "charged";

	private SpanCodec() {
	}

	static CompoundTag write(Collection<WireConnection> spans, CompoundTag root) {
		ListTag list = new ListTag();
		for (WireConnection span : spans) {
			CompoundTag tag = new CompoundTag();
			tag.putInt(K_START, span.startInsulatorId());
			tag.putInt(K_END, span.endInsulatorId());
			tag.putString(K_WIRE, span.wireType());
			tag.putLong(K_START_POS, span.startBlockPos().asLong());
			tag.putLong(K_END_POS, span.endBlockPos().asLong());
			tag.putString(K_START_TYPE, span.startBlockType());
			tag.putString(K_END_TYPE, span.endBlockType());
			tag.putString(K_START_POWER, span.startPowerType());
			tag.putString(K_END_POWER, span.endPowerType());
			tag.putInt(K_CHARGED, span.chargedItems());
			list.add(tag);
		}

		root.putInt(K_VERSION, VERSION);
		root.put(K_SPANS, list);
		return root;
	}

	/**
	 * @param problem told, once per span that could not be read, what was wrong with it
	 */
	static List<WireConnection> read(CompoundTag root, Consumer<String> problem) {
		int version = root.getInt(K_VERSION);
		if (version > VERSION) problem.accept("spans saved by a newer version (" + version + "), reading what is understood");

		List<WireConnection> spans = new ArrayList<>();
		ListTag list = root.getList(K_SPANS, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag tag = list.getCompound(i);

			if (!tag.contains(K_START, Tag.TAG_INT) || !tag.contains(K_END, Tag.TAG_INT)
					|| !tag.contains(K_START_POS, Tag.TAG_LONG) || !tag.contains(K_END_POS, Tag.TAG_LONG)) {
				problem.accept("span " + i + " has no ids or no positions, dropped");
				continue;
			}

			int start = tag.getInt(K_START);
			int end = tag.getInt(K_END);
			if (start <= 0 || end <= 0 || start == end) {
				problem.accept("span " + i + " joins ids " + start + " and " + end + ", dropped");
				continue;
			}

			spans.add(new WireConnection(start, end, tag.getString(K_WIRE),
					BlockPos.of(tag.getLong(K_START_POS)), BlockPos.of(tag.getLong(K_END_POS)),
					tag.getString(K_START_TYPE), tag.getString(K_END_TYPE),
					role(tag.getString(K_START_POWER)), role(tag.getString(K_END_POWER)),
					tag.getInt(K_CHARGED)));
		}

		return spans;
	}

	/** A role this build does not know reads as bidirectional, which is what a machine with no opinion says. */
	private static String role(String saved) {
		return switch (saved) {
			case FittingTypes.POWER_INPUT, FittingTypes.POWER_OUTPUT, FittingTypes.POWER_BIDIRECTIONAL -> saved;
			default -> FittingTypes.POWER_BIDIRECTIONAL;
		};
	}
}
