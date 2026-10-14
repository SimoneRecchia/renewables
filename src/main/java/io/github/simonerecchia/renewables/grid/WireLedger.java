package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.catalog.ConductorCatalog;
import io.github.simonerecchia.renewables.net.Net;
import io.github.simonerecchia.renewables.net.packet.SpanChangedPayload;
import io.github.simonerecchia.renewables.registry.ModItems;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

/**
 * Every span saved in a world, per level, and what happens to it when a machine goes.
 *
 * The spans of a level live in that level's saved data ({@link WireData}); this class is the only way in.  A change
 * is announced to the players of that dimension, and removing a span gives its reels back: exactly the number it was
 * charged, as items at the start end of the span, in stacks of at most 64.
 *
 * All of it runs on the server thread.
 */
public final class WireLedger {
	/** The most spans one fitting may carry: more is not a line, it is a fan, and the grid's cost grows with it. */
	public static final int MAX_PER_FITTING = 16;
	/** The most spans one level may hold: the simulation rebuilds its graph every tick. */
	public static final int MAX_PER_LEVEL = 4096;

	private static final int STACK = 64;

	private WireLedger() {
	}

	/** Every span of a level, in no particular order.  A copy: it does not change under the caller. */
	public static List<WireConnection> spans(ServerLevel level) {
		return WireData.of(level).store().all();
	}

	/** How many spans the level holds. */
	public static int size(ServerLevel level) {
		return WireData.of(level).store().size();
	}

	/** Whether these two fittings are already joined, in either order. */
	public static boolean joined(ServerLevel level, int a, int b) {
		return WireData.of(level).store().joined(a, b);
	}

	/** How many spans hang from a fitting. */
	public static int countAt(ServerLevel level, int id) {
		return WireData.of(level).store().countAt(id);
	}

	/**
	 * Saves a new span and tells the level's players.  The caller has already checked everything and charged for it.
	 *
	 * @return false if the pair was already joined or the ids are not valid; nothing changed then
	 */
	public static boolean add(ServerLevel level, WireConnection span) {
		WireData data = WireData.of(level);
		if (!data.store().add(span)) return false;

		data.setDirty();
		Net.sendToLevel(level, new SpanChangedPayload(span, true));
		return true;
	}

	/**
	 * Removes every span attached to any of these fittings, tells the level's players and refunds the reels.  Ids
	 * that are not positive are ignored.  Called when a machine is taken out of the world, and when the grid finds
	 * a span whose end is gone.
	 */
	public static void removeAttachedTo(ServerLevel level, int[] insulatorIds) {
		Set<Integer> ids = new LinkedHashSet<>();
		for (int id : insulatorIds) {
			if (id > 0) ids.add(id);
		}

		if (ids.isEmpty()) return;

		WireData data = WireData.of(level);
		List<WireConnection> removed = data.store().removeAttachedTo(ids);
		if (removed.isEmpty()) return;

		data.setDirty();
		for (WireConnection span : removed) {
			Net.sendToLevel(level, new SpanChangedPayload(span, false));
			refund(level, span);
		}
	}

	/** The world is open: read every level's spans, and count their ids as used. */
	public static void onServerStarted(MinecraftServer server) {
		int total = 0;
		for (ServerLevel level : server.getAllLevels()) {
			List<WireConnection> spans = spans(level);
			for (WireConnection span : spans) {
				InsulatorIds.adopt(span.startInsulatorId());
				InsulatorIds.adopt(span.endInsulatorId());
			}

			total += spans.size();
			if (!spans.isEmpty()) Renewables.LOGGER.info("{} span(s) in {}", spans.size(), level.dimension().location());
		}

		Renewables.LOGGER.info("Wire ledger ready: {} span(s) in all", total);
	}

	/** The world is closing: mark every level's spans for saving so the engine writes them out. */
	public static void onServerStopping(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			WireData.of(level).setDirty();
		}
	}

	/**
	 * Gives back what a span cost.
	 *
	 * The items land where the span <em>started</em>, even when it is the other end that was broken: that is the
	 * block the player strung it from.  If that chunk is not loaded they fall at the other end instead, because an
	 * item placed in an unloaded chunk would be lost.  A span of a conductor this build does not know, or one made
	 * for free, refunds nothing.
	 */
	private static void refund(ServerLevel level, WireConnection span) {
		if (span.chargedItems() <= 0) return;

		ConductorSpec conductor = ConductorCatalog.byPath(span.wireType());
		if (conductor == null) return;

		RegistryObject<Item> reel = ModItems.CONDUCTORS.get(conductor.id());
		if (reel == null || !reel.isPresent()) return;

		BlockPos at = level.isLoaded(span.startBlockPos()) ? span.startBlockPos()
				: level.isLoaded(span.endBlockPos()) ? span.endBlockPos() : null;
		if (at == null) {
			Renewables.LOGGER.warn("Could not refund {} reel(s) for span {} - {}: neither end is loaded",
					span.chargedItems(), span.startInsulatorId(), span.endInsulatorId());
			return;
		}

		for (ItemStack stack : stacks(reel.get(), span.chargedItems())) {
			Block.popResource(level, at, stack);
		}
	}

	/** {@code count} of an item as full stacks and a remainder. */
	static List<ItemStack> stacks(Item item, int count) {
		List<ItemStack> stacks = new ArrayList<>();
		int size = Math.max(1, Math.min(STACK, item.getMaxStackSize()));
		for (int left = count; left > 0; left -= size) {
			stacks.add(new ItemStack(item, Math.min(size, left)));
		}

		return stacks;
	}
}
