package io.github.simonerecchia.renewables.client.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.item.ConductorItem;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class AirClickTest {
	/**
	 * Vanilla posts its click-on-empty event only when the hand is empty, so a reel never saw it: the click in the air
	 * has to arrive through the item's own use, or it does not arrive at all.
	 */
	@Test
	void theReelAnswersAClickOnNothingItself() throws NoSuchMethodException {
		// getDeclaredMethod throws when ConductorItem leaves use() to Item, whose default answers PASS
		var use = ConductorItem.class.getDeclaredMethod("use", Level.class, Player.class, InteractionHand.class);
		assertEquals(InteractionResultHolder.class, use.getReturnType());
	}

	@Test
	void aClickOnABlockIsNotAClickInTheAir() {
		// use() also follows a useOn that passed on a block, a run that could not be laid there
		assertFalse(AirClick.takes(false, false, true));
		assertFalse(AirClick.takes(false, true, false));
	}

	@Test
	void theMainHandsReelTakesThePressAndTheOffHandsDoesNotTakeItAgain() {
		assertTrue(AirClick.takes(true, false, true));
		assertFalse(AirClick.takes(true, true, true), "the same press, the second time");
	}

	@Test
	void aReelInTheOffHandAloneTakesIt() {
		assertTrue(AirClick.takes(true, true, false));
	}

	@Test
	void startingAndCompletingSwingAndCancellingDoesNot() {
		assertEquals(InteractionResult.SUCCESS, AirClick.answer(LinkMachine.Step.STARTED));
		assertEquals(InteractionResult.SUCCESS, AirClick.answer(LinkMachine.Step.COMPLETED));
		assertEquals(InteractionResult.CONSUME, AirClick.answer(LinkMachine.Step.CANCELLED));
	}

	@Test
	void aPressTheReelTookNeverFallsThroughToTheOtherHand() {
		// Minecraft.startUseItem tries the next hand only after an answer that does not consume the action
		for (LinkMachine.Step step : LinkMachine.Step.values()) {
			assertTrue(AirClick.answer(step).consumesAction(), step.name());
		}

		assertTrue(AirClick.answer(null).consumesAction(), "a fitting that could not be used is still this press");
		assertFalse(AirClick.answer(null).shouldSwing());
	}
}
