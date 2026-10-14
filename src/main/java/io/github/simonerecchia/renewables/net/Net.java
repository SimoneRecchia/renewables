package io.github.simonerecchia.renewables.net;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.grid.WireConnection;
import io.github.simonerecchia.renewables.grid.WireLedger;
import io.github.simonerecchia.renewables.net.packet.CreateSpanPayload;
import io.github.simonerecchia.renewables.net.packet.PlantControlPayload;
import io.github.simonerecchia.renewables.net.packet.PoleConfigPayload;
import io.github.simonerecchia.renewables.net.packet.PowerUpdatePayload;
import io.github.simonerecchia.renewables.net.packet.SolarControlPayload;
import io.github.simonerecchia.renewables.net.packet.SpanChangedPayload;
import io.github.simonerecchia.renewables.net.packet.TurbineControlPayload;
import io.github.simonerecchia.renewables.net.packet.WireSyncPayload;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's one network channel, {@code renewables:main}.
 *
 * <b>A message's id is its position in {@link #init}, and ids must never move</b>: new messages go at the end.
 * The protocol version is checked on both sides and compared exactly, so a client with a different message set
 * is refused at the handshake instead of being thrown out by a decode error half way through a session.  Bump
 * {@link #PROTOCOL} with every change to a message.
 *
 * Everything a handler does is queued onto the main thread of its side; nothing heavy runs on a network thread.
 */
public final class Net {
	public static final String PROTOCOL = "1";

	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(Renewables.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

	private static boolean registered;

	private Net() {
	}

	/** Registers every message.  Called once from the common setup, before anything can be sent. */
	public static synchronized void init() {
		if (registered) return;
		registered = true;

		int id = 0;
		CHANNEL.messageBuilder(WireSyncPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(WireSyncPayload::write).decoder(WireSyncPayload::read)
				.consumerMainThread(ClientBridge::wireSync).add();
		CHANNEL.messageBuilder(PoleConfigPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
				.encoder(PoleConfigPayload::write).decoder(PoleConfigPayload::read)
				.consumerMainThread(ServerHandlers::poleConfig).add();
		CHANNEL.messageBuilder(CreateSpanPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
				.encoder(CreateSpanPayload::write).decoder(CreateSpanPayload::read)
				.consumerMainThread(ServerHandlers::createSpan).add();
		CHANNEL.messageBuilder(TurbineControlPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
				.encoder(TurbineControlPayload::write).decoder(TurbineControlPayload::read)
				.consumerMainThread(ServerHandlers::turbineControl).add();
		CHANNEL.messageBuilder(SolarControlPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
				.encoder(SolarControlPayload::write).decoder(SolarControlPayload::read)
				.consumerMainThread(ServerHandlers::solarControl).add();
		CHANNEL.messageBuilder(SpanChangedPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(SpanChangedPayload::write).decoder(SpanChangedPayload::read)
				.consumerMainThread(ClientBridge::spanChanged).add();
		CHANNEL.messageBuilder(PowerUpdatePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(PowerUpdatePayload::write).decoder(PowerUpdatePayload::read)
				.consumerMainThread(ClientBridge::powerUpdate).add();
		CHANNEL.messageBuilder(PlantControlPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
				.encoder(PlantControlPayload::write).decoder(PlantControlPayload::read)
				.consumerMainThread(ServerHandlers::plantControl).add();
	}

	/** Client to server. */
	public static void sendToServer(Object message) {
		CHANNEL.sendToServer(message);
	}

	public static void sendToPlayer(ServerPlayer player, Object message) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
	}

	/** Every connected player, in every dimension.  Prefer {@link #sendToLevel} for anything that is about one level. */
	public static void sendToAll(Object message) {
		CHANNEL.send(PacketDistributor.ALL.noArg(), message);
	}

	/** The players in one dimension: the only ones who can resolve the fittings a span message names. */
	public static void sendToLevel(ServerLevel level, Object message) {
		CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), message);
	}

	/** The players who currently have the chunk at {@code pos} loaded on their side. */
	public static void sendToTracking(ServerLevel level, BlockPos pos, Object message) {
		CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)), message);
	}

	/**
	 * Gives a player every span of the level they are in, replacing what their client held: at login, and
	 * again when they change dimension or respawn, because the client forgets everything when its level goes.
	 */
	public static void syncWires(ServerPlayer player) {
		List<WireConnection> all = WireLedger.spans(player.serverLevel());

		if (all.isEmpty()) {
			// still has to go out: it is what tells the client to forget the spans of the level it left
			sendToPlayer(player, new WireSyncPayload(true, List.of()));
			return;
		}

		for (int from = 0; from < all.size(); from += WireSyncPayload.BATCH) {
			List<WireConnection> batch = new ArrayList<>(all.subList(from, Math.min(all.size(), from + WireSyncPayload.BATCH)));
			sendToPlayer(player, new WireSyncPayload(from == 0, batch));
		}
	}
}
