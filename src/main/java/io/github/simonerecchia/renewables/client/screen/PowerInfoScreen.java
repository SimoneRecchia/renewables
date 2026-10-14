package io.github.simonerecchia.renewables.client.screen;

import io.github.simonerecchia.renewables.api.Nameplate;
import io.github.simonerecchia.renewables.block.FePool;
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
			// what is stored against what it can hold, and what left in the last tick: the second figure used to be the
			// most it pushes one neighbour, printed as if it were a rate after a quantity
			lines.add(Component.translatable("tooltip.renewables.power.fe", kiosk.energyBuffer(), FePool.CAPACITY));
			lines.add(Component.translatable("tooltip.renewables.power.fe_out", kiosk.energyOut()));
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
