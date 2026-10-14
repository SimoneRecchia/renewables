package io.github.simonerecchia.renewables.client.wire;

import javax.annotation.Nullable;
import net.minecraft.world.InteractionResult;

/**
 * A press of the use button with a reel and the crosshair on nothing (docs/spec/client-net.md section 2.3, entry B):
 * whether it is the reel's to take, and what the item answers for it.
 *
 * The press reaches the reel through {@code Item.use}, once per hand that holds something, main hand first, and
 * stops at the first hand that answers SUCCESS or CONSUME ({@code Minecraft.startUseItem}).  So a press the reel took
 * answers one of those and the other hand never sees it - one press, one step of the link machine, at most one span
 * request.
 */
public final class AirClick {
	private AirClick() {
	}

	/**
	 * Whether this hand's use is a click in the air for the reel in it.
	 *
	 * @param crosshairOnNothing false when the crosshair is on a block or an entity: {@code use} also follows a
	 *                           {@code useOn} that passed, and that click was on a block
	 * @param offHand            whether the reel is in the off hand
	 * @param mainHoldsReel      whether the main hand holds a reel too, which has already had this press
	 */
	public static boolean takes(boolean crosshairOnNothing, boolean offHand, boolean mainHoldsReel) {
		return crosshairOnNothing && !(offHand && mainHoldsReel);
	}

	/**
	 * What the item answers once a fitting was picked: a swing for starting and for completing, none for cancelling,
	 * and none when the fitting could not be used (a message already says why).  Never PASS: the press was the reel's.
	 */
	public static InteractionResult answer(@Nullable LinkMachine.Step step) {
		return step == LinkMachine.Step.STARTED || step == LinkMachine.Step.COMPLETED
				? InteractionResult.SUCCESS : InteractionResult.CONSUME;
	}
}
