package io.github.simonerecchia.renewables.net;

import io.github.simonerecchia.renewables.api.power.TrackerMode;
import io.github.simonerecchia.renewables.block.PlantControllerBlockEntity;
import io.github.simonerecchia.renewables.block.PvArrayBlockEntity;
import io.github.simonerecchia.renewables.block.PvInverterBlockEntity;
import io.github.simonerecchia.renewables.block.UtilityPoleBlockEntity;
import io.github.simonerecchia.renewables.block.WindTurbineBlockEntity;
import io.github.simonerecchia.renewables.grid.SpanPlanner;
import io.github.simonerecchia.renewables.grid.SpanRequest;
import io.github.simonerecchia.renewables.net.packet.CreateSpanPayload;
import io.github.simonerecchia.renewables.net.packet.PlantControlPayload;
import io.github.simonerecchia.renewables.net.packet.PoleConfigPayload;
import io.github.simonerecchia.renewables.net.packet.SolarControlPayload;
import io.github.simonerecchia.renewables.net.packet.TurbineControlPayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

/**
 * What the server does with each client-to-server message.
 *
 * Each runs on the main server thread (the channel queues it).  The client is never believed: a command for a
 * machine goes through {@link PanelGuard}, numbers are rejected unless finite and then limited, and an action
 * the server does not know is dropped rather than read as some other action.
 */
final class ServerHandlers {
	private ServerHandlers() {
	}

	// ---- utility pole ----

	static void poleConfig(PoleConfigPayload message, Supplier<NetworkEvent.Context> context) {
		if (!allFinite(message.offsetX(), message.offsetY(), message.offsetZ(), message.yaw(), message.pitch())) return;

		// the arms are part of the world, so only a player who may build may move them
		ServerPlayer sender = context.get().getSender();
		if (sender == null || !sender.mayBuild()) return;

		UtilityPoleBlockEntity pole = PanelGuard.find(sender, message.blockPos(), UtilityPoleBlockEntity.class);
		if (pole == null) return;

		// one update for all five values, so the clients see one change rather than five
		pole.applyConfig(
				Mth.clamp(message.offsetX(), -PoleConfigPayload.MAX_OFFSET, PoleConfigPayload.MAX_OFFSET),
				Mth.clamp(message.offsetY(), -PoleConfigPayload.MAX_OFFSET, PoleConfigPayload.MAX_OFFSET),
				Mth.clamp(message.offsetZ(), -PoleConfigPayload.MAX_OFFSET, PoleConfigPayload.MAX_OFFSET),
				Mth.clamp(message.yaw(), -PoleConfigPayload.MAX_YAW, PoleConfigPayload.MAX_YAW),
				Mth.clamp(message.pitch(), -PoleConfigPayload.MAX_PITCH, PoleConfigPayload.MAX_PITCH));
	}

	// ---- wires ----

	/** The key span requests are counted under in {@link PanelGuard#admit}: they are about no single machine. */
	private static final long SPAN_REQUESTS = Long.MIN_VALUE;

	static void createSpan(CreateSpanPayload message, Supplier<NetworkEvent.Context> context) {
		ServerPlayer player = context.get().getSender();
		if (player == null || player.isSpectator()) return;
		if (!PanelGuard.admit(player, SPAN_REQUESTS)) return;

		// an unassigned id is not a fitting, and a span from a fitting to itself is not a span
		if (message.startId() <= 0 || message.endId() <= 0 || message.startId() == message.endId()) return;

		SpanPlanner.request(player, new SpanRequest(message.startId(), message.endId(), message.startPos(), message.endPos(),
				message.startFittingType(), message.endFittingType(), message.startPowerType(), message.endPowerType()));
	}

	// ---- control panels ----

	static void turbineControl(TurbineControlPayload message, Supplier<NetworkEvent.Context> context) {
		if (message.action() == null) return;

		WindTurbineBlockEntity turbine = PanelGuard.find(context.get().getSender(), message.blockPos(), WindTurbineBlockEntity.class,
				WindTurbineBlockEntity::getTowerSegments);
		if (turbine == null) return;

		switch (message.action()) {
			case TOGGLE_RUNNING -> turbine.setStoppedByPlayer(!turbine.isStoppedByPlayer());
			case CYCLE_REDSTONE_MODE -> turbine.setRedstoneMode(turbine.getRedstoneMode().next());
			case SET_POWER_LIMIT -> {
				if (!Double.isFinite(message.value())) return;

				turbine.setActivePowerLimit(Mth.clamp(message.value(), 0.0, turbine.spec().ratedPowerKw()));
			}
		}
	}

	static void solarControl(SolarControlPayload message, Supplier<NetworkEvent.Context> context) {
		SolarControlPayload.Action action = message.action();
		if (action == null) return;

		switch (action) {
			case INVERTER_TOGGLE_RUNNING, INVERTER_CYCLE_REDSTONE_MODE, INVERTER_SET_POWER_LIMIT, INVERTER_SET_POWER_FACTOR -> {
				PvInverterBlockEntity inverter = PanelGuard.find(context.get().getSender(), message.blockPos(), PvInverterBlockEntity.class);
				if (inverter != null) inverter(inverter, action, message.value());
			}
			case ARRAY_CYCLE_TRACKER_MODE, ARRAY_SET_TRACKER_ANGLE -> {
				PvArrayBlockEntity array = PanelGuard.find(context.get().getSender(), message.blockPos(), PvArrayBlockEntity.class);
				if (array != null) array(array, action, message.value());
			}
		}
	}

	private static void inverter(PvInverterBlockEntity inverter, SolarControlPayload.Action action, double value) {
		switch (action) {
			case INVERTER_TOGGLE_RUNNING -> inverter.setStoppedByPlayer(!inverter.isStoppedByPlayer());
			case INVERTER_CYCLE_REDSTONE_MODE -> inverter.setRedstoneMode(inverter.getRedstoneMode().next());
			case INVERTER_SET_POWER_LIMIT -> {
				if (Double.isFinite(value)) inverter.setActivePowerLimit(Mth.clamp(value, 0.0, inverter.spec().acPowerKw()));
			}
			// the machine takes the magnitude and holds it between its own minimum and unity
			case INVERTER_SET_POWER_FACTOR -> {
				if (Double.isFinite(value)) inverter.setPowerFactor(value);
			}
			default -> {
			}
		}
	}

	private static void array(PvArrayBlockEntity array, SolarControlPayload.Action action, double value) {
		// a fixed mounting has no tracker to command
		if (array.tracker() == null) return;

		switch (action) {
			case ARRAY_CYCLE_TRACKER_MODE -> array.setTrackerMode(array.trackerMode().next());
			// a hand angle only sticks in manual mode: in auto the controller would undo it a tick later
			case ARRAY_SET_TRACKER_ANGLE -> {
				if (!Double.isFinite(value)) return;

				array.setTrackerMode(TrackerMode.MANUAL);
				array.setManualRotation(value);
			}
			default -> {
			}
		}
	}

	static void plantControl(PlantControlPayload message, Supplier<NetworkEvent.Context> context) {
		if (message.action() == null) return;

		PlantControllerBlockEntity plant = PanelGuard.find(context.get().getSender(), message.blockPos(), PlantControllerBlockEntity.class);
		if (plant == null) return;

		switch (message.action()) {
			case CYCLE_MODE -> plant.setMode(plant.getMode().next());
			case SET_SETPOINT -> {
				if (!Double.isFinite(message.value())) return;

				// the panel's own scale is the plant's capacity, so a setpoint above it means nothing
				double capacity = plant.getCapacityKw();
				double kw = Math.max(0.0, message.value());
				plant.setSetpointKw(capacity > 0.0 ? Math.min(kw, capacity) : kw);
			}
		}
	}

	private static boolean allFinite(float... values) {
		for (float value : values) {
			if (!Float.isFinite(value)) return false;
		}

		return true;
	}
}
