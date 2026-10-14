package io.github.simonerecchia.renewables.net;

import io.github.simonerecchia.renewables.RenewablesClient;
import io.github.simonerecchia.renewables.net.packet.PowerUpdatePayload;
import io.github.simonerecchia.renewables.net.packet.SpanChangedPayload;
import io.github.simonerecchia.renewables.net.packet.WireSyncPayload;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

/**
 * Where a server-to-client message crosses from the common network code into the client-only code.
 *
 * This class is loaded on a dedicated server (the channel registers its handlers there too), so it must not
 * name a client class anywhere a verifier would look: every client call is inside a {@code DistExecutor}
 * lambda, which is not resolved unless the side is the client.
 */
final class ClientBridge {
	private ClientBridge() {
	}

	static void wireSync(WireSyncPayload message, Supplier<NetworkEvent.Context> context) {
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RenewablesClient.onWireSync(message));
	}

	static void spanChanged(SpanChangedPayload message, Supplier<NetworkEvent.Context> context) {
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RenewablesClient.onSpanChanged(message));
	}

	static void powerUpdate(PowerUpdatePayload message, Supplier<NetworkEvent.Context> context) {
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RenewablesClient.onPowerUpdate(message));
	}
}
