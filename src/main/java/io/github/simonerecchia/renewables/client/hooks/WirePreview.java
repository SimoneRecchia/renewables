package io.github.simonerecchia.renewables.client.hooks;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.block.MachineShell;
import io.github.simonerecchia.renewables.client.hooks.FittingSelector.Selection;
import io.github.simonerecchia.renewables.client.wire.InsulatorIndex;
import io.github.simonerecchia.renewables.client.wire.PendingLink;
import io.github.simonerecchia.renewables.client.wire.WireProfile;
import io.github.simonerecchia.renewables.client.wire.WireRenderer;
import io.github.simonerecchia.renewables.client.wire.WireStyle;
import io.github.simonerecchia.renewables.client.wire.WireStyles;
import io.github.simonerecchia.renewables.core.ClientConfig;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.item.ConductorItem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The cable from the first fitting to wherever the player is aiming, drawn while a connection is pending
 * (docs/spec/client-net.md section 2.5).
 *
 * Drawn exactly as a real span: the same shape, sag and bundle, in the conductor in hand (a thin black wire if the
 * hand holds none).  The free end snaps to the insulator a click would pick, and otherwise hangs at the hand.  A span
 * that the reel in hand could not reach is red.  Only the local player sees it.
 */
@OnlyIn(Dist.CLIENT)
public final class WirePreview {
	/** An end nearer than this to the start is "the same place": the cable then follows the hand instead. */
	private static final double MIN_SNAP_DISTANCE = 0.1;

	private static final WireProfile PROFILE = new WireProfile();
	private static final InteractionHand[] HANDS = {InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND};

	private WirePreview() {
	}

	/** Where the preview's free end is. */
	public record End(Vec3 point, boolean snapped) {
	}

	/** Draws the preview; called by the wire renderer inside its pass, with the camera's position. */
	public static void draw(VertexConsumer consumer, PoseStack.Pose pose, ClientLevel level, LocalPlayer player,
			PendingLink pending, Vec3 camera, float partialTick) {
		Vec3 start = startOf(pending);
		End end = endOf(level, player, pending, start, partialTick);

		ConductorSpec reel = heldReel(player);
		WireStyle style = reel == null ? WireStyle.GENERIC : WireStyles.of(reel);
		int colour = style.colour();
		if (reel != null && end.snapped() && end.point().distanceTo(start) > reel.maxSpan()) {
			colour = WireRenderer.TOO_LONG_COLOUR;
		}

		Vec3 to = end.point();
		if (!PROFILE.rebuild(to.x - start.x, to.y - start.y, to.z - start.z, style.sag())) return;

		int light = WireRenderer.sampleLight(level, start.x, start.y, start.z);
		double nearest = Math.min(start.distanceTo(camera), to.distanceTo(camera));
		WireRenderer.drawCable(consumer, pose, PROFILE, style, start.x, start.y, start.z, camera.x, camera.y, camera.z,
				light, light, light, colour, nearest);
	}

	/** Where the pending fitting is: its live anchor with snapping on, so it follows the pole if it is reconfigured. */
	private static Vec3 startOf(PendingLink pending) {
		if (ClientConfig.previewSnapping()) {
			InsulatorHost host = InsulatorIndex.find(pending.insulatorId());
			if (host != null) {
				Vec3 anchor = host.getWirePosition(pending.index());
				if (anchor != null) return anchor;
			}
		}

		return pending.point();
	}

	/**
	 * The free end: the insulator a click would pick now, if it is somewhere other than the start; else the hand.
	 *
	 * "What a click would pick" mirrors the two inputs: aiming at a block, the insulators of the machine that block
	 * belongs to; aiming at nothing, any insulator within reach of the air click.
	 */
	public static End endOf(ClientLevel level, LocalPlayer player, @Nullable PendingLink pending, Vec3 start, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);

		Selection selection = null;
		HitResult hit = Minecraft.getInstance().hitResult;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos host = MachineShell.hostOr(level, blockHit.getBlockPos());
			selection = FittingSelector.select(level, host, eye, look);
		} else if (hit == null || hit.getType() == HitResult.Type.MISS) {
			selection = FittingSelector.nearestInReach(level, player.blockPosition(), eye, look);
		}

		if (selection != null && selection.point().distanceTo(start) > MIN_SNAP_DISTANCE) {
			return new End(selection.point(), true);
		}

		// the hand: at the feet plus half the eye height, half a block ahead
		Vec3 hand = player.getPosition(partialTick).add(0.0, 0.5 * player.getEyeHeight(), 0.0).add(look.scale(0.5));
		return new End(hand, false);
	}

	/** The spec of the reel in the main hand, else in the off hand, else null. */
	@Nullable
	public static ConductorSpec heldReel(LocalPlayer player) {
		for (InteractionHand hand : HANDS) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof ConductorItem reel) return reel.spec();
		}

		return null;
	}
}
