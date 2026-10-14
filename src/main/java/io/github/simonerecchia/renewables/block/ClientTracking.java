package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.client.ClientBlockEntities;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Tells the renderers which machines exist, and does it without mentioning them on a server. */
final class ClientTracking {
	private ClientTracking() {
	}

	static void track(BlockEntity machine) {
		if (onClient(machine)) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBlockEntities.track(machine));
		}
	}

	static void untrack(BlockEntity machine) {
		if (onClient(machine)) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBlockEntities.untrack(machine));
		}
	}

	private static boolean onClient(BlockEntity machine) {
		Level level = machine.getLevel();
		return level != null && level.isClientSide();
	}
}
