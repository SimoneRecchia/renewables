package io.github.simonerecchia.renewables.client.hooks;

import io.github.simonerecchia.renewables.client.hooks.FittingSelector.Selection;
import io.github.simonerecchia.renewables.client.wire.ClickGate;
import io.github.simonerecchia.renewables.client.wire.ClientWires;
import io.github.simonerecchia.renewables.client.wire.LinkMachine;
import io.github.simonerecchia.renewables.client.wire.PendingLink;
import io.github.simonerecchia.renewables.client.wire.ReleaseGate;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.net.Net;
import io.github.simonerecchia.renewables.net.packet.CreateSpanPayload;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * What a click with a reel does once an insulator has been picked: starts a connection, cancels it, or finishes it by
 * asking the server for the span.
 *
 * The state is {@link ClientWires#link()}; this is the glue between a {@link Selection} and the machine, the gates that
 * make one press one click, and the request.  The server re-checks everything it is sent (distance, class, cost,
 * duplicates), so there is nothing to validate here beyond "the fitting has an id".
 */
@OnlyIn(Dist.CLIENT)
public final class WireLinker {
	private static final ClickGate TICK_GATE = new ClickGate();
	private static final ReleaseGate RELEASE_GATE = new ReleaseGate();

	private WireLinker() {
	}

	/** One tick: lets the release gate see whether the use button is down. */
	public static void observeUseButton(boolean down) {
		RELEASE_GATE.observe(down);
	}

	/** Whether this press, at this game time, is a new click: not a repeat of a held button, not the second hand's echo. */
	public static boolean admitPress(long gameTime) {
		return TICK_GATE.admit(gameTime) && RELEASE_GATE.admit();
	}

	/** A new world: no gate remembers the last one. */
	public static void reset() {
		TICK_GATE.reset();
		RELEASE_GATE.reset();
	}

	/**
	 * Feeds a picked insulator to the machine.
	 *
	 * @return what the click did, or null when it could not be used: the fitting has no id yet, or its machine cannot
	 *         say where a wire would end (a message says so)
	 */
	@Nullable
	public static LinkMachine.Step accept(Player player, Selection selection) {
		if (selection.id() <= 0 || selection.anchor() == null) {
			player.displayClientMessage(Component.translatable("message.renewables.wire.anchor_unavailable"), true);
			return null;
		}

		InsulatorHost host = selection.host();
		PendingLink here = new PendingLink(selection.hostPos(), selection.index(), selection.id(), host.fittingType(),
				host.powerType(selection.part()), selection.point());

		LinkMachine.Click<PendingLink> click = ClientWires.link().click(here);
		if (click.step() == LinkMachine.Step.COMPLETED) {
			PendingLink first = click.first();
			Net.sendToServer(new CreateSpanPayload(first.insulatorId(), here.insulatorId(), first.pos(), here.pos(),
					first.fittingType(), here.fittingType(), first.powerType(), here.powerType()));
		}

		return click.step();
	}

	/** The nudge when a click found no insulator, on the action bar. */
	public static void aimHint(Player player) {
		player.displayClientMessage(Component.translatableWithFallback("message.renewables.wire.aim_at_insulator",
				"Aim at an insulator."), true);
	}
}
