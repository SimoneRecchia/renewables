package io.github.simonerecchia.renewables.client.screen;

/**
 * Where a panel sits on a screen that may be shorter than it, and how far the wheel has moved it.
 *
 * The smallest screen a panel has to work on is the default 854x480 window at automatic scale: 427x240 GUI pixels.
 * A panel that fits is centred, as before; one that does not starts against the top edge - the nameplate and the
 * state are what a player opens it to read - and the wheel moves it up until its bottom edge, and the widgets that
 * sit there, are on screen.  Never further: every pixel of the panel is reachable and none of the screen is wasted.
 * No Minecraft types, so it is tested without a game.
 */
public final class PanelScroll {
	/** GUI pixels one notch of the wheel moves the panel: a line of text and a little. */
	public static final int STEP = 12;

	private int scroll;

	/** The panel's top edge on a screen {@code screenHeight} tall, keeping the scroll inside what it can be now. */
	public int top(int screenHeight, int panelHeight) {
		int overflow = panelHeight - screenHeight;
		if (overflow <= 0) {
			scroll = 0;
			return (screenHeight - panelHeight) / 2;
		}

		scroll = Math.max(0, Math.min(overflow, scroll));
		return -scroll;
	}

	/**
	 * One movement of the wheel, positive away from the player (towards the top of the panel).
	 *
	 * @return how many pixels the panel moved down the screen: negative when it moved up, zero when it could not move
	 */
	public int wheel(double delta, int screenHeight, int panelHeight) {
		int before = top(screenHeight, panelHeight);
		scroll -= (int) Math.signum(delta) * STEP;
		return top(screenHeight, panelHeight) - before;
	}

	/** Whether the panel is taller than the screen, so that some of it is off it at any one time. */
	public static boolean overflows(int screenHeight, int panelHeight) {
		return panelHeight > screenHeight;
	}

	/** Pixels the panel has been moved up by. */
	public int scroll() {
		return scroll;
	}
}
