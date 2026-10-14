package io.github.simonerecchia.renewables.net;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The one check every control-panel command passes before it touches a machine.
 *
 * A panel can be opened from as far as the wrench reaches, so the server must agree to take commands from at
 * least that far; and a client can send a command for any position it likes, so the server must decide for
 * itself whether this player could really be standing at that machine.  A failed check drops the command
 * silently: there is nobody honest to tell.
 */
public final class PanelGuard {
	/** How far the power wrench reaches, in blocks. */
	public static final double WRENCH_REACH = 16.0;
	/** How far a panel command is accepted from: the wrench's reach and a little slack for walking away. */
	public static final double REACH = WRENCH_REACH + 2.0;

	/**
	 * How many requests about one thing a player may make in one server tick.  A client sends one a click, but the
	 * packets of two quick clicks can arrive together; a modified client sending hundreds cannot.
	 */
	static final int PER_TICK = 4;

	/** What each player has asked for this tick.  Weak keys: a player who leaves takes his entry along. */
	private static final Map<ServerPlayer, Requests> REQUESTS = new WeakHashMap<>();

	private record Requests(int tick, Map<Long, Integer> counts) {
	}

	private PanelGuard() {
	}

	/**
	 * The block entity at {@code pos} if the player may command it, otherwise null.
	 *
	 * In order: the player may change the world at all (not a spectator, not in adventure mode - a command moves a
	 * pole's arms or stops a turbine, which is building, not pressing a button), the player has not already sent
	 * {@link #PER_TICK} commands for this machine this tick (each one is a full update to everyone watching it), the
	 * chunk is loaded (without loading it), the position is inside the world border, the player may
	 * interact there (spawn protection and the like), the block entity is of the expected kind, and the player is
	 * close enough.  Close enough is measured to the machine's vertical axis, at the player's own height clamped to
	 * the machine's span, so someone halfway up a turbine tower is not far from its panel.
	 *
	 * @param footDrop blocks the machine reaches below its block entity: the tower segments of a turbine, else 0
	 */
	@Nullable
	public static <T extends BlockEntity> T find(@Nullable ServerPlayer player, BlockPos pos, Class<T> type,
			ToIntFunction<T> footDrop) {
		if (player == null) return null;
		if (player.isSpectator() || !player.mayBuild()) return null;
		if (!admit(player, pos.asLong())) return null;

		ServerLevel level = player.serverLevel();
		if (!level.isLoaded(pos)) return null;
		if (!level.getWorldBorder().isWithinBounds(pos)) return null;
		if (!player.mayInteract(level, pos)) return null;

		BlockEntity entity = level.getBlockEntity(pos);
		if (!type.isInstance(entity)) return null;

		T machine = type.cast(entity);
		double foot = pos.getY() - Math.max(0, footDrop.applyAsInt(machine));
		double top = pos.getY() + 1.0;
		double y = Mth.clamp(player.getY(), foot, top);

		double dx = player.getX() - (pos.getX() + 0.5);
		double dy = player.getY() - y;
		double dz = player.getZ() - (pos.getZ() + 0.5);

		return dx * dx + dy * dy + dz * dz <= REACH * REACH ? machine : null;
	}

	/** {@link #find(ServerPlayer, BlockPos, Class, ToIntFunction)} for a machine that stands on its own block. */
	@Nullable
	public static <T extends BlockEntity> T find(@Nullable ServerPlayer player, BlockPos pos, Class<T> type) {
		return find(player, pos, type, machine -> 0);
	}

	/**
	 * Counts a request from the player about {@code key} - a machine's position, or a kind of request that is about
	 * no single machine - and says whether it is within {@link #PER_TICK} for this server tick.
	 */
	static boolean admit(ServerPlayer player, long key) {
		int tick = player.server.getTickCount();
		Requests requests = REQUESTS.get(player);
		if (requests == null || requests.tick() != tick) {
			requests = new Requests(tick, new HashMap<>());
			REQUESTS.put(player, requests);
		}

		return requests.counts().merge(key, 1, Integer::sum) <= PER_TICK;
	}

	/** The world has closed: no player of it is remembered. */
	public static void clear() {
		REQUESTS.clear();
	}
}
