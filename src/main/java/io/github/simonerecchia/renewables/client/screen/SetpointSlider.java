package io.github.simonerecchia.renewables.client.screen;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A slider a player sets a machine with: it follows the machine while nobody is holding it, and sends when the drag
 * ends - wherever the button is let go - or at each step of the arrow keys.
 *
 * Sending per pixel would be a packet a frame, and a setpoint written back from the server while a hand is on
 * the slider fights the hand - so {@link #syncTo} refuses during a drag, and for a moment after a send while the
 * machine's answer is on its way, rather than every caller remembering to ask first.
 *
 * Vanilla gives a mouse release only to the widget under the pointer, while a drag goes on following the pointer
 * off the slider; {@link PlantScreen#mouseReleased} hands the release of a drag to {@link #release} wherever it is.
 */
public abstract class SetpointSlider extends AbstractSliderButton {
	/** How long the handle waits for the machine to report a value just sent before it follows the machine again. */
	private static final long ANSWER_WAIT_MS = 1000L;
	/** How near the machine's value has to come to the one sent for the wait to be over. */
	private static final double ANSWERED = 1.0e-3;

	private boolean beingDragged;
	private long waitUntil;

	protected SetpointSlider(int x, int y, int width, int height, double fraction) {
		super(x, y, width, height, Component.empty(), Mth.clamp(fraction, 0.0, 1.0));
		updateMessage();
	}

	/** Puts the slider where the machine says it is. */
	public void syncTo(double fraction) {
		double clamped = Mth.clamp(fraction, 0.0, 1.0);
		if (beingDragged) return;

		if (waitUntil != 0L) {
			if (Math.abs(clamped - value) < ANSWERED || System.currentTimeMillis() >= waitUntil) {
				waitUntil = 0L;
			} else {
				// the machine has not had the value just sent yet: pulling the handle back would undo the hand
				return;
			}
		}

		if (Math.abs(clamped - value) < 1.0e-4) return;

		value = clamped;
		updateMessage();
	}

	@Override
	protected final void applyValue() {
		// nothing until the drag ends: see release
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		beingDragged = true;
		super.onClick(mouseX, mouseY);
	}

	/** A release over the slider: it ends a drag begun here, and nothing else - a press elsewhere is not a setting. */
	@Override
	public void onRelease(double mouseX, double mouseY) {
		release(mouseX, mouseY);
	}

	/**
	 * Ends a drag begun on this slider, wherever the button was let go, and sends the setting.
	 *
	 * @return whether there was a drag to end
	 */
	public boolean release(double mouseX, double mouseY) {
		if (!beingDragged) return false;

		beingDragged = false;
		releaseSound(mouseX, mouseY);
		// a slider switched off under the hand (the array put back to automatic) sends nothing
		if (active) send();
		return true;
	}

	/** The arrow keys move the handle a pixel at a time, and with no drag to end each step is the setting. */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		double before = value;
		boolean used = super.keyPressed(keyCode, scanCode, modifiers);
		if (value != before && active) send();

		return used;
	}

	/** The click the vanilla slider makes when it is let go. */
	protected void releaseSound(double mouseX, double mouseY) {
		super.onRelease(mouseX, mouseY);
	}

	private void send() {
		waitUntil = System.currentTimeMillis() + ANSWER_WAIT_MS;
		commit();
	}

	/** Sends the setpoint the player has left the slider at. */
	protected abstract void commit();
}
