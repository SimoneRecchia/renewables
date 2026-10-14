package io.github.simonerecchia.renewables.core;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.DispatchLedger;
import io.github.simonerecchia.renewables.block.TowerCollapse;
import io.github.simonerecchia.renewables.grid.Grid;
import io.github.simonerecchia.renewables.grid.InsulatorIds;
import io.github.simonerecchia.renewables.grid.WireLedger;
import io.github.simonerecchia.renewables.net.Net;
import io.github.simonerecchia.renewables.net.PanelGuard;
import io.github.simonerecchia.renewables.weather.LevelWeather;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The server-side hooks that are not about any one machine: the clock, the weather, the grid's tick, wire
 * synchronisation and shutdown.  Common code only; a client never loads anything from here that it could not.
 */
public final class ServerEvents {
	private ServerEvents() {
	}

	/**
	 * The Overworld exists, and no chunk has loaded yet: the insulator ids are bound to the world's counter before the
	 * first machine claims one.  (Machines near the spawn load before {@link ServerStartedEvent}.)
	 */
	@SubscribeEvent
	public static void onLevelLoad(LevelEvent.Load event) {
		if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) InsulatorIds.attach(level);
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		Renewables.LOGGER.info("Renewables mod initialized on server");

		// the weather manager of the Overworld is created now so its first use is not in the middle of a tick
		LevelWeather.get(event.getServer().overworld());
		WireLedger.onServerStarted(event.getServer());
	}

	/** A player's client starts with no spans: it is told about every one in the level it is in. */
	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) Net.syncWires(player);
	}

	/** The client drops its spans when its level changes, so a new level needs them sent again. */
	@SubscribeEvent
	public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) Net.syncWires(player);
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) Net.syncWires(player);
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			// the clock goes first: everything sampled during this tick, weather included, reads the time
			for (ServerLevel level : event.getServer().getAllLevels()) {
				RealTimeClock.apply(level);
				LevelWeather.tick(level);
			}

			return;
		}

		// the block entities have already run, so the grid sees this tick's residue of every energy budget
		for (ServerLevel level : event.getServer().getAllLevels()) {
			Grid.tick(level);
			// after the controllers have renewed their holds this tick, the lapsed ones are given back
			DispatchLedger.tick(level);
		}

		for (ServerLevel level : event.getServer().getAllLevels()) {
			RealTimeClock.holdClientClocks(level);
		}

		TowerCollapse.tickAll();
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		WireLedger.onServerStopping(event.getServer());
		clearStatics();
	}

	/**
	 * Again once the server has stopped, which Forge posts however the server ended: a crash in the tick loop skips
	 * {@link ServerStoppingEvent}, and the next world in this JVM must not inherit a dead server's levels.
	 */
	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		clearStatics();
	}

	/** Static state outlives a singleplayer world in the same JVM, so it is emptied here and not just replaced next time. */
	private static void clearStatics() {
		LevelWeather.clear();
		TowerCollapse.clear();
		Grid.clear();
		PanelGuard.clear();
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel() instanceof ServerLevel level) {
			LevelWeather.remove(level);
			TowerCollapse.forget(level);
			Grid.forget(level);
		}
	}
}
