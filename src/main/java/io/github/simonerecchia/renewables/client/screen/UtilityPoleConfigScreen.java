package io.github.simonerecchia.renewables.client.screen;

import io.github.simonerecchia.renewables.api.Nameplate;
import io.github.simonerecchia.renewables.block.UtilityPoleBlockEntity;
import io.github.simonerecchia.renewables.net.Net;
import io.github.simonerecchia.renewables.net.packet.PoleConfigPayload;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Where a utility pole's arms are set: an offset on each axis, a yaw and a pitch, five text fields and Done/Cancel
 * (docs/spec/grid-blocks.md section 3.6, client-net.md section 7.2).
 *
 * The fields start at what the pole has now, each with the range the server takes beside it.  A field that does not
 * hold a number keeps the last good value (and is shown in red); one that holds a number outside the range is shown
 * in amber, because what is sent is the limit, not what is typed.  Done sends all five - clamped to what the server
 * accepts, so the pole ends up where the fields say - and closes; Cancel and Escape close without sending.  The angles
 * are typed in degrees and sent in radians.  The game is not paused.
 */
public class UtilityPoleConfigScreen extends Screen {
	private static final int FIELD_WIDTH = 60;
	private static final int FIELD_HEIGHT = 20;
	private static final int ROW_STEP = 24;
	private static final int BUTTON_WIDTH = 80;
	/** From the title's baseline to the first row of fields. */
	private static final int TITLE_GAP = 20;
	private static final int LABEL_COLOUR = 0xFFFFFF;
	private static final int RANGE_COLOUR = 0xA0A0A0;
	private static final int TEXT_COLOUR = 0xE0E0E0;
	private static final int INVALID_COLOUR = 0xFF5555;
	/** A number, but not one the server takes: what goes is the limit. */
	private static final int CLAMPED_COLOUR = 0xFFB040;

	private static final String[] LEFT_KEYS = {"offset_x", "offset_y", "offset_z"};
	private static final String[] RIGHT_KEYS = {"yaw", "pitch"};

	private final BlockPos pole;

	/** The working values: offsets in blocks, angles in radians. */
	private float offsetX;
	private float offsetY;
	private float offsetZ;
	private float yaw;
	private float pitch;

	private EditBox xField;
	private EditBox yField;
	private EditBox zField;
	private EditBox yawField;
	private EditBox pitchField;

	public UtilityPoleConfigScreen(BlockPos pole) {
		super(Component.translatable("screen.renewables.utility_pole_config.title"));
		this.pole = pole.immutable();

		Level level = Minecraft.getInstance().level;
		if (level != null && level.getBlockEntity(pole) instanceof UtilityPoleBlockEntity entity) {
			offsetX = entity.offsetX();
			offsetY = entity.offsetY();
			offsetZ = entity.offsetZ();
			yaw = entity.yawRadians();
			pitch = entity.pitchRadians();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int top = height / 2 - 40;
		// centred on the real width from the real text, so a label never starts off the left edge of a narrow GUI
		PoleConfigText.Layout layout = PoleConfigText.layout(width, FIELD_WIDTH, widest(LEFT_KEYS),
				font.width(offsetRange()), widest(RIGHT_KEYS), font.width(degreesRange(PoleConfigPayload.MAX_YAW)));
		int left = layout.leftField();
		int right = layout.rightField();

		xField = field(left, top, PoleConfigText.offset(offsetX), "offset_x",
				text -> offsetX = read(xField, text, offsetX, PoleConfigPayload.MAX_OFFSET));
		yField = field(left, top + ROW_STEP, PoleConfigText.offset(offsetY), "offset_y",
				text -> offsetY = read(yField, text, offsetY, PoleConfigPayload.MAX_OFFSET));
		zField = field(left, top + 2 * ROW_STEP, PoleConfigText.offset(offsetZ), "offset_z",
				text -> offsetZ = read(zField, text, offsetZ, PoleConfigPayload.MAX_OFFSET));
		yawField = field(right, top, PoleConfigText.degrees(yaw), "yaw",
				text -> yaw = readDegrees(yawField, text, yaw, PoleConfigPayload.MAX_YAW));
		pitchField = field(right, top + ROW_STEP, PoleConfigText.degrees(pitch), "pitch",
				text -> pitch = readDegrees(pitchField, text, pitch, PoleConfigPayload.MAX_PITCH));
		setInitialFocus(xField);

		int buttons = top + 3 * ROW_STEP + 8;
		int centre = width / 2;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> done())
				.bounds(centre - BUTTON_WIDTH - 4, buttons, BUTTON_WIDTH, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
				.bounds(centre + 4, buttons, BUTTON_WIDTH, 20).build());
	}

	/** The longest of a column's labels. */
	private int widest(String[] keys) {
		int widest = 0;
		for (String key : keys) {
			widest = Math.max(widest, font.width(Component.translatable("screen.renewables.utility_pole_config." + key)));
		}

		return widest;
	}

	/** A text field with its label to the left, holding {@code initial}, calling {@code changed} as it is edited. */
	private EditBox field(int x, int y, String initial, String key, Consumer<String> changed) {
		Component label = Component.translatable("screen.renewables.utility_pole_config." + key);
		EditBox box = new EditBox(font, x, y, FIELD_WIDTH, FIELD_HEIGHT, label);
		box.setMaxLength(16);
		box.setFilter(PoleConfigText::allowed);
		box.setValue(initial);
		box.setResponder(changed);
		addRenderableWidget(box);
		return box;
	}

	/**
	 * Reads an offset field: a number is kept, limited to the range the server takes (amber when it had to be), and
	 * anything else leaves the last value.
	 */
	private static float read(EditBox box, String text, float previous, float limit) {
		Float parsed = PoleConfigText.parse(text);
		if (parsed == null) {
			box.setTextColor(INVALID_COLOUR);
			return previous;
		}

		box.setTextColor(PoleConfigText.within(parsed, limit) ? TEXT_COLOUR : CLAMPED_COLOUR);
		return PoleConfigText.clamp(parsed, limit);
	}

	/** The same for an angle typed in degrees and kept in radians. */
	private static float readDegrees(EditBox box, String text, float previous, float limitRadians) {
		Float parsed = PoleConfigText.parse(text);
		if (parsed == null) {
			box.setTextColor(INVALID_COLOUR);
			return previous;
		}

		box.setTextColor(PoleConfigText.withinDegrees(parsed, limitRadians) ? TEXT_COLOUR : CLAMPED_COLOUR);
		return PoleConfigText.clamp((float) Math.toRadians(parsed), limitRadians);
	}

	private void done() {
		// the fields were clamped as they were typed; what the pole already held is clamped here, so one rule covers both
		Net.sendToServer(new PoleConfigPayload(pole,
				PoleConfigText.clamp(offsetX, PoleConfigPayload.MAX_OFFSET),
				PoleConfigText.clamp(offsetY, PoleConfigPayload.MAX_OFFSET),
				PoleConfigText.clamp(offsetZ, PoleConfigPayload.MAX_OFFSET),
				PoleConfigText.clamp(yaw, PoleConfigPayload.MAX_YAW),
				PoleConfigText.clamp(pitch, PoleConfigPayload.MAX_PITCH)));
		onClose();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);

		graphics.drawCenteredString(font, title, width / 2, xField.getY() - TITLE_GAP, LABEL_COLOUR);

		label(graphics, xField, "offset_x", offsetRange());
		label(graphics, yField, "offset_y", offsetRange());
		label(graphics, zField, "offset_z", offsetRange());
		label(graphics, yawField, "yaw", degreesRange(PoleConfigPayload.MAX_YAW));
		label(graphics, pitchField, "pitch", degreesRange(PoleConfigPayload.MAX_PITCH));
	}

	/** The name of a field, to its left and level with its text, and the range it takes to its right. */
	private void label(GuiGraphics graphics, EditBox box, String key, Component range) {
		Component text = Component.translatable("screen.renewables.utility_pole_config." + key);
		int y = box.getY() + (FIELD_HEIGHT - font.lineHeight) / 2;
		graphics.drawString(font, text, box.getX() - PoleConfigText.LABEL_GAP - font.width(text), y, LABEL_COLOUR);
		graphics.drawString(font, range, box.getX() + FIELD_WIDTH + PoleConfigText.LABEL_GAP, y, RANGE_COLOUR);
	}

	private static Component offsetRange() {
		return Component.translatable("screen.renewables.utility_pole_config.range", Nameplate.figure(PoleConfigPayload.MAX_OFFSET));
	}

	private static Component degreesRange(float limitRadians) {
		return Component.translatable("screen.renewables.utility_pole_config.range_degrees",
				Nameplate.fmt("%.0f", Math.toDegrees(limitRadians)));
	}
}
