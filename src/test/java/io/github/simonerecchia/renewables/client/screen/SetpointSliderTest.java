package io.github.simonerecchia.renewables.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

/**
 * The slider sends once per gesture: a drag ends with one send wherever the button is let go, an arrow key is one
 * send per step, and a release with no press on the slider sends nothing.  No game: the slider's sound is the only
 * thing that would need one, and it is stubbed.
 */
class SetpointSliderTest {
	private static final int LEFT = 11;
	private static final int TOP = 200;
	private static final int WIDTH = 266;
	private static final int KEY_ENTER = 257;
	private static final int KEY_RIGHT = 262;

	private static final class Slider extends SetpointSlider {
		int sent;
		double lastSent = Double.NaN;

		Slider(double fraction) {
			super(LEFT, TOP, WIDTH, 20, fraction);
		}

		double value() {
			return value;
		}

		@Override
		protected void updateMessage() {
		}

		@Override
		protected void releaseSound(double mouseX, double mouseY) {
		}

		@Override
		protected void commit() {
			sent++;
			lastSent = value;
		}
	}

	/** A panel with the slider in it and focused, as it is once the press on it has landed. */
	private static final class Panel extends PlantScreen<BlockEntity> {
		private final Slider slider;

		Panel(Slider slider) {
			super(Component.empty(), new ResourceLocation("renewables", "textures/gui/pv_inverter.png"), 288, 252,
					BlockPos.ZERO, BlockEntity.class);
			this.slider = slider;
			addRenderableWidget(slider);
		}

		@Override
		public GuiEventListener getFocused() {
			return slider;
		}

		@Override
		protected void drawPanel(GuiGraphics graphics) {
		}
	}

	@Test
	void aDragLetGoPastTheEndOfTheSliderStillSends() {
		Slider slider = new Slider(0.5);
		Panel panel = new Panel(slider);

		slider.onClick(LEFT + WIDTH / 2.0, TOP + 10);
		slider.mouseDragged(LEFT + WIDTH + 40, TOP + 10, 0, 40, 0);
		// let go well to the right of the slider: vanilla would give this release to nothing
		assertTrue(panel.mouseReleased(LEFT + WIDTH + 40, TOP + 10, 0));

		assertEquals(1, slider.sent);
		assertEquals(1.0, slider.lastSent, 1.0e-9);
	}

	@Test
	void theDragIsOverOnceItIsLetGo() {
		Slider slider = new Slider(0.5);
		Panel panel = new Panel(slider);

		slider.onClick(LEFT + WIDTH / 2.0, TOP + 10);
		panel.mouseReleased(LEFT + WIDTH + 40, TOP + 10, 0);

		// following the machine again once its answer has come: the drag did not stay stuck on
		slider.syncTo(slider.value());
		slider.syncTo(0.2);
		assertEquals(0.2, slider.value(), 1.0e-9);
	}

	@Test
	void aReleaseOverTheSliderWithNoPressOnItSendsNothing() {
		Slider slider = new Slider(0.5);
		slider.onRelease(LEFT + 20, TOP + 10);

		assertEquals(0, slider.sent);
	}

	@Test
	void aReleaseOverTheSliderAfterADragOnItSendsOnce() {
		Slider slider = new Slider(0.5);
		Panel panel = new Panel(slider);

		slider.onClick(LEFT + 40, TOP + 10);
		panel.mouseReleased(LEFT + 40, TOP + 10, 0);
		// and the vanilla path, if it is reached too, finds no drag left to end
		slider.onRelease(LEFT + 40, TOP + 10);

		assertEquals(1, slider.sent);
	}

	@Test
	void theArrowKeysSendEachStep() {
		Slider slider = new Slider(0.5);
		// Enter lets the arrows move the handle
		slider.keyPressed(KEY_ENTER, 0, 0);
		slider.keyPressed(KEY_RIGHT, 0, 0);
		slider.keyPressed(KEY_RIGHT, 0, 0);

		assertEquals(2, slider.sent);
		assertTrue(slider.lastSent > 0.5);
	}

	@Test
	void theHandleWaitsForTheMachineToTakeWhatWasSent() {
		Slider slider = new Slider(0.5);
		slider.keyPressed(KEY_ENTER, 0, 0);
		slider.keyPressed(KEY_RIGHT, 0, 0);
		double sent = slider.value();

		// the machine has not answered yet: the old value must not pull the handle back
		slider.syncTo(0.5);
		assertEquals(sent, slider.value(), 1.0e-9);

		// it has, and from then on the handle follows the machine
		slider.syncTo(sent);
		slider.syncTo(0.3);
		assertEquals(0.3, slider.value(), 1.0e-9);
	}

	@Test
	void aSliderSwitchedOffUnderTheHandSendsNothing() {
		Slider slider = new Slider(0.5);
		Panel panel = new Panel(slider);

		slider.onClick(LEFT + 40, TOP + 10);
		slider.active = false;
		assertTrue(panel.mouseReleased(LEFT + 40, TOP + 10, 0));

		assertEquals(0, slider.sent);
		assertFalse(Double.isFinite(slider.lastSent));
	}
}
