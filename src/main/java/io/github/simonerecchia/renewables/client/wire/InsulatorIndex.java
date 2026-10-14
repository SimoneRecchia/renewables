package io.github.simonerecchia.renewables.client.wire;

import io.github.simonerecchia.renewables.grid.InsulatorHost;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Resolves a fitting id to the machine that owns it, on the client.
 *
 * Machines register when they load and whenever an update reaches them, and drop out when they are removed;
 * the references are weak, so a machine that is simply forgotten cannot be kept alive by this map.  An entry
 * whose block entity has since been removed is discarded when it is read.
 *
 * Cleared with the world, in {@code RenewablesClient.forgetWorld}.  Not cleared when the span list is replaced: the
 * machines are still loaded then, and nothing would register them again.
 */
@OnlyIn(Dist.CLIENT)
public final class InsulatorIndex {
	private static final Map<Integer, WeakReference<InsulatorHost>> HOSTS = new ConcurrentHashMap<>();

	private InsulatorIndex() {
	}

	public static void register(InsulatorHost host) {
		register(host, host.getInsulatorIds());
	}

	public static void register(InsulatorHost host, int[] ids) {
		for (int id : ids) {
			if (id > 0) HOSTS.put(id, new WeakReference<>(host));
		}
	}

	public static void unregister(int[] ids) {
		for (int id : ids) {
			HOSTS.remove(id);
		}
	}

	@Nullable
	public static InsulatorHost find(int id) {
		WeakReference<InsulatorHost> reference = HOSTS.get(id);
		if (reference == null) return null;

		InsulatorHost host = reference.get();
		if (host == null || (host instanceof net.minecraft.world.level.block.entity.BlockEntity entity && entity.isRemoved())) {
			HOSTS.remove(id);
			return null;
		}

		return host;
	}

	public static void clear() {
		HOSTS.clear();
	}
}
