package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.item.ConductorItem;
import io.github.simonerecchia.renewables.model.ModelDefinitions;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Validates a request to string a span and, if it holds, creates it.
 *
 * The client is never believed.  Everything in the request is looked up again from the server's own block
 * entities, the conductor is read from the player's hands, and the reels are taken from the player's inventory;
 * the packet's contents only say <em>which</em> two fittings were meant.  The packet handler has already rejected a
 * non-positive or repeated id, a spectator and an oversized string.
 *
 * The checks, in order, and the first to fail ends it (silently unless a message is named):
 * <ol>
 * <li>for each end, start first: the chunk is loaded ({@code chunk_unloaded}), the position is inside the world
 *     border, the player may interact there ({@code no_permission});</li>
 * <li>there is a block entity at both, and it is a wire host;</li>
 * <li>each id belongs to a fitting of the machine at that position ({@code failed_insulator});</li>
 * <li>the machine type the client named is the machine's real one;</li>
 * <li>both anchors can be worked out ({@code anchor_unavailable});</li>
 * <li>the pair is not already joined, in either order ({@code already_connected}), and neither fitting nor the level
 *     is full;</li>
 * <li>there is a conductor reel in a hand;</li>
 * <li>both fittings take that conductor's voltage class ({@code wrong_class});</li>
 * <li>the anchors are no further apart than the conductor spans ({@code too_far}), and are within the player's
 *     reach;</li>
 * <li>the reels are paid for ({@code not_enough}): the very last check, so that everything that can fail fails for
 *     free.</li>
 * </ol>
 * The roles stored on the span are the machines', not the packet's.  Creating a span does not recalculate the
 * grid, and does not check that power could flow along it: a span that carries nothing is allowed.
 */
public final class SpanPlanner {
	/** How far from the player's eyes the end they clicked last may be: the client scans a 16-block cube, and a tower is tall. */
	static final double REACH = 32.0;

	private SpanPlanner() {
	}

	public static void request(ServerPlayer player, SpanRequest request) {
		if (player == null) return;

		ServerLevel level = player.serverLevel();

		// 1. both ends are somewhere the player may touch
		if (!reachable(player, level, request.startPos())) return;
		if (!reachable(player, level, request.endPos())) return;

		// 2. there is a wire host at both
		InsulatorHost start = hostAt(level, request.startPos());
		InsulatorHost end = hostAt(level, request.endPos());
		if (start == null || end == null) return;

		// 3. the ids are the machines' own
		int startIndex = indexOf(start, request.startId());
		int endIndex = indexOf(end, request.endId());
		if (startIndex < 0 || endIndex < 0) {
			say(player, Component.translatable("message.renewables.wire.failed_insulator"));
			return;
		}

		// 4. the client named the right kind of machine
		if (!start.fittingType().equals(request.startFittingType()) || !end.fittingType().equals(request.endFittingType())) return;

		// 5. both anchors exist on the server
		Vec3 startAnchor = start.getWirePosition(startIndex);
		Vec3 endAnchor = end.getWirePosition(endIndex);
		if (startAnchor == null || endAnchor == null) {
			say(player, Component.translatable("message.renewables.wire.anchor_unavailable"));
			return;
		}

		// 6. a fitting is never joined to itself (the handler has dropped equal ids; this is the same fitting by another name)
		if (request.startId() == request.endId()) return;

		if (WireLedger.joined(level, request.startId(), request.endId())) {
			say(player, Component.translatable("message.renewables.wire.already_connected"));
			return;
		}

		if (WireLedger.countAt(level, request.startId()) >= WireLedger.MAX_PER_FITTING
				|| WireLedger.countAt(level, request.endId()) >= WireLedger.MAX_PER_FITTING
				|| WireLedger.size(level) >= WireLedger.MAX_PER_LEVEL) {
			say(player, Component.translatableWithFallback("message.renewables.wire.too_many",
					"No more spans can be strung there: a fitting carries at most %s.", WireLedger.MAX_PER_FITTING));
			return;
		}

		// 7. the conductor is whatever the player is holding, never what the packet says
		ConductorItem reel = reelInHand(player);
		if (reel == null) {
			say(player, Component.translatableWithFallback("message.renewables.wire.no_conductor", "Hold a reel of conductor to string a span."));
			return;
		}

		ConductorSpec conductor = reel.spec();

		// 8. the voltage class suits both fittings
		if (!start.takesConductor(conductor, startIndex) || !end.takesConductor(conductor, endIndex)) {
			say(player, Component.translatable("message.renewables.wire.wrong_class", reel.getDescription()));
			return;
		}

		// 9. the span is no longer than the conductor allows, and the player could have clicked both ends
		double length = startAnchor.distanceTo(endAnchor);
		if (!(length <= conductor.maxSpan())) {
			say(player, Component.translatable("message.renewables.wire.too_far",
					String.format(Locale.ROOT, "%.1f", length), (int) conductor.maxSpan()));
			return;
		}

		// the end clicked last is near the player; the first may have been left a whole span behind
		if (player.getEyePosition().distanceTo(endAnchor) > REACH
				|| player.getEyePosition().distanceTo(startAnchor) > REACH + conductor.maxSpan()) {
			say(player, Component.translatableWithFallback("message.renewables.wire.out_of_reach", "Too far away to string that."));
			return;
		}

		// 10. pay, last: nothing that can still fail comes after it
		int cost = player.isCreative() ? 0 : conductor.cost(length);
		if (cost > 0 && !charge(player, reel, cost)) return;

		// 11. the span.  Roles are the machines'
		String startRole = role(start, level, request.startPos(), startIndex, request.startPowerType());
		String endRole = role(end, level, request.endPos(), endIndex, request.endPowerType());
		WireConnection span = new WireConnection(request.startId(), request.endId(), conductor.id().getPath(),
				request.startPos(), request.endPos(), start.fittingType(), end.fittingType(), startRole, endRole, cost);

		if (!WireLedger.add(level, span)) {
			// raced with something between the check and here: give back what was taken
			refundCharge(player, reel, cost);
			return;
		}

		say(player, Component.translatable("message.renewables.wire.connected", request.startId(), request.endId()));
	}

	/** The chunk is loaded, the position inside the border, and the player allowed to interact there. */
	private static boolean reachable(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			say(player, Component.translatable("message.renewables.wire.chunk_unloaded", pos.getX(), pos.getY(), pos.getZ()));
			return false;
		}

		if (!level.getWorldBorder().isWithinBounds(pos)) return false;

		if (!player.mayInteract(level, pos)) {
			say(player, Component.translatable("message.renewables.wire.no_permission", pos.getX(), pos.getY(), pos.getZ()));
			return false;
		}

		return true;
	}

	@Nullable
	private static InsulatorHost hostAt(ServerLevel level, BlockPos pos) {
		BlockEntity entity = level.getBlockEntity(pos);
		return entity instanceof InsulatorHost host ? host : null;
	}

	/** The index of the fitting with this id on the machine, or -1.  An id that is not positive is nobody's. */
	private static int indexOf(InsulatorHost host, int id) {
		if (id <= 0) return -1;

		int[] ids = host.getInsulatorIds();
		for (int i = 0; i < ids.length; i++) {
			if (ids[i] == id) return i;
		}

		return -1;
	}

	/** The first reel of conductor in the main hand, else the off hand. */
	@Nullable
	private static ConductorItem reelInHand(ServerPlayer player) {
		if (player.getMainHandItem().getItem() instanceof ConductorItem reel) return reel;
		if (player.getOffhandItem().getItem() instanceof ConductorItem reel) return reel;

		return null;
	}

	/**
	 * Takes {@code cost} reels of this conductor from anywhere in the player's inventory.  All of them or none: the
	 * count is made first, so a player who is short loses nothing.
	 */
	private static boolean charge(ServerPlayer player, ConductorItem reel, int cost) {
		var inventory = player.getInventory();
		int held = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(reel)) held += stack.getCount();
		}

		if (held < cost) {
			say(player, Component.translatable("message.renewables.wire.not_enough", cost, reel.getDescription()));
			return false;
		}

		int left = cost;
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.is(reel)) continue;

			int taken = Math.min(left, stack.getCount());
			stack.shrink(taken);
			left -= taken;
		}

		inventory.setChanged();
		player.containerMenu.broadcastChanges();
		return true;
	}

	/** The reels go back to the player, or to the ground at their feet if the inventory is full. */
	private static void refundCharge(ServerPlayer player, ConductorItem reel, int cost) {
		for (ItemStack stack : WireLedger.stacks(reel, cost)) {
			if (!player.getInventory().add(stack)) player.drop(stack, false);
		}
	}

	/**
	 * The power role of a fitting end: the machine's say, and only if it has none the client's.  A machine answers
	 * with one of the three roles every time, so the client's word is a fallback that is not expected to be used.
	 */
	private static String role(InsulatorHost host, ServerLevel level, BlockPos pos, int index, @Nullable String reported) {
		BlockEntity entity = level.getBlockEntity(pos);
		String group = null;
		if (entity != null) group = FittingTypes.nameOf(ModelDefinitions.of(entity.getBlockState().getBlock()), index);

		String said = host.powerType(group == null ? "" : group);
		if (said != null && isRole(said)) return said;

		return reported != null && isRole(reported) ? reported : FittingTypes.POWER_BIDIRECTIONAL;
	}

	private static boolean isRole(String role) {
		return FittingTypes.POWER_INPUT.equals(role) || FittingTypes.POWER_OUTPUT.equals(role) || FittingTypes.POWER_BIDIRECTIONAL.equals(role);
	}

	private static void say(ServerPlayer player, Component message) {
		player.displayClientMessage(message, true);
	}
}
