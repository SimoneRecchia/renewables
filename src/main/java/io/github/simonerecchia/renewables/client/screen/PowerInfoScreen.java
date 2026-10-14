package io.github.simonerecchia.renewables.client.screen;

import io.github.simonerecchia.renewables.api.Nameplate;
import io.github.simonerecchia.renewables.block.PowerBoxBlockEntity;
import io.github.simonerecchia.renewables.block.UtilityPoleBlockEntity;
import io.github.simonerecchia.renewables.grid.InsulatorHost;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The power wrench's read-only card for a machine that has no panel of its own: what it is, and what the grid is
 * delivering to it (docs/spec/grid-blocks.md section 10.1).
 *
 * Nothing is sent to the server; it only reads what the block entity on the client already knows, so the figure is
 * the one the network last delivered.  The card closes itself when the machine stops existing, and does not pause the
 * game, because the reading is live.
 */
public class PowerInfoScreen extends Screen {
	private static final int TEXT_COLOUR = 0xC6C6C6;
	private static final int TITLE_COLOUR = 0xFFFFFF;
	private static final int LINE_GAP = 2;
	private static final int TITLE_GAP = 8;

	/** What a power box can pass to its neighbours: a tank this big, handed out this fast. */
	private static final int KIOSK_CAPACITY_FE = 100_000;
	private static final int KIOSK_TRANSFER_FE_PER_TICK = 2_000;
	/** Forge Energy per kilowatt delivered. */
	private static final int FE_PER_KW = 50;

	private final BlockPos target;

	public PowerInfoScreen(BlockPos target) {
		super(Component.translatable("screen.renewables.power_info.title"));
		this.target = target.immutable();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The machine has gone (broken, or its chunk unloaded): there is nothing left to read. */
	@Override
	public void tick() {
		super.tick();

		Level level = Minecraft.getInstance().level;
		if (level == null || level.getBlockEntity(target) == null) onClose();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);

		int y = height / 4;
		graphics.drawCenteredString(font, title, width / 2, y, TITLE_COLOUR);
		y += font.lineHeight + TITLE_GAP;

		for (Component line : lines()) {
			graphics.drawCenteredString(font, line, width / 2, y, TEXT_COLOUR);
			y += font.lineHeight + LINE_GAP;
		}

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	/** The rows, top to bottom; the first is an error when there is nothing to describe. */
	private List<Component> lines() {
		List<Component> lines = new ArrayList<>();
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			lines.add(Component.translatable("screen.renewables.power_info.world_unavailable"));
			return lines;
		}

		BlockEntity machine = level.getBlockEntity(target);
		if (machine == null) {
			lines.add(Component.translatable("screen.renewables.power_info.block_removed"));
			return lines;
		}

		lines.add(machine.getBlockState().getBlock().getName());

		if (machine instanceof InsulatorHost host) {
			lines.add(Component.translatable("tooltip.renewables.power.amount", Nameplate.fmt("%.1f", host.getCurrentPower())));
		} else {
			lines.add(Component.translatable("screen.renewables.power_info.no_power_data"));
		}

		if (machine instanceof PowerBoxBlockEntity kiosk) {
			long available = Math.min(KIOSK_CAPACITY_FE, (long) Math.floor(Math.max(0.0, kiosk.getCurrentPower()) * FE_PER_KW));
			lines.add(Component.translatable("tooltip.renewables.power.fe", available, KIOSK_TRANSFER_FE_PER_TICK));
		}

		if (machine instanceof UtilityPoleBlockEntity pole) {
			lines.add(Component.translatable("screen.renewables.power_info.offsets",
					Nameplate.fmt("%.2f", pole.offsetX()), Nameplate.fmt("%.2f", pole.offsetY()), Nameplate.fmt("%.2f", pole.offsetZ())));
			lines.add(Component.translatable("screen.renewables.power_info.yaw_pitch",
					Nameplate.fmt("%.1f", Math.toDegrees(pole.yawRadians())), Nameplate.fmt("%.1f", Math.toDegrees(pole.pitchRadians()))));
		}

		return lines;
	}
}
