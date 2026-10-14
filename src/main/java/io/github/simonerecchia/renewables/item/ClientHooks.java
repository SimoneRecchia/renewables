package io.github.simonerecchia.renewables.item;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Where the items and the pole reach into the client, without naming it.
 *
 * Three things the server-side classes of this package cannot do themselves belong to the client: stringing a
 * wire needs the pending connection and the model picker, the wrench needs the same picker and a screen, and the
 * pole's screen is a screen.  A block or an item is loaded on a dedicated server, so none of them may mention
 * the classes that do those things; the client installs its answers here once, at setup, and a server never
 * does, which is also why nothing here is ever called on the logical server.
 *
 * Until something is installed each hook does nothing, which is what a build without a client looks like.
 */
public final class ClientHooks {
	/** A click with a reel on a machine that carries fittings; true when the click was used. */
	@FunctionalInterface
	public interface WireClick {
		boolean click(Level level, Player player, InteractionHand hand, BlockPos host, BlockHitResult hit);
	}

	/** A click with a reel on nothing, the reaching click; what it came to is what the item answers. */
	@FunctionalInterface
	public interface WireClickInAir {
		InteractionResult click(Level level, Player player, InteractionHand hand);
	}

	/** A click with the wrench, on a block ({@code hit} set) or on the air (null); true when a screen opened. */
	@FunctionalInterface
	public interface WrenchUse {
		boolean use(Level level, Player player, @Nullable BlockHitResult hit);
	}

	/** Open the configuration screen of the pole at this position. */
	@FunctionalInterface
	public interface PoleScreen {
		void open(BlockPos pole);
	}

	private static volatile WireClick wireClick = (level, player, hand, host, hit) -> false;
	private static volatile WireClickInAir wireClickInAir = (level, player, hand) -> InteractionResult.PASS;
	private static volatile WrenchUse wrenchUse = (level, player, hit) -> false;
	private static volatile PoleScreen poleScreen = pole -> {
	};

	private ClientHooks() {
	}

	public static void installWireClick(WireClick hook) {
		wireClick = hook;
	}

	public static void installWireClickInAir(WireClickInAir hook) {
		wireClickInAir = hook;
	}

	public static void installWrenchUse(WrenchUse hook) {
		wrenchUse = hook;
	}

	public static void installPoleScreen(PoleScreen hook) {
		poleScreen = hook;
	}

	/** Client side only: a reel was used on the machine at {@code host}. */
	public static boolean wireClick(Level level, Player player, InteractionHand hand, BlockPos host, BlockHitResult hit) {
		return wireClick.click(level, player, hand, host, hit);
	}

	/** Client side only: a reel was used with the crosshair on nothing. */
	public static InteractionResult wireClickInAir(Level level, Player player, InteractionHand hand) {
		return wireClickInAir.click(level, player, hand);
	}

	/** Client side only: the wrench was used. */
	public static boolean wrenchUse(Level level, Player player, @Nullable BlockHitResult hit) {
		return wrenchUse.use(level, player, hit);
	}

	/** Client side only: an empty hand was put to a pole. */
	public static void openPoleConfiguration(BlockPos pole) {
		poleScreen.open(pole);
	}
}
