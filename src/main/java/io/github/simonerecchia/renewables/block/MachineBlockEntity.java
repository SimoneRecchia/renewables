package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.client.wire.ClientWires;
import io.github.simonerecchia.renewables.client.wire.InsulatorIndex;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.grid.InsulatorIds;
import io.github.simonerecchia.renewables.model.ExtraPose;
import io.github.simonerecchia.renewables.model.ModelDefinition;
import io.github.simonerecchia.renewables.model.ModelDefinitions;
import io.github.simonerecchia.renewables.model.PartBox;
import io.github.simonerecchia.renewables.model.PartBoxes;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

/**
 * The common body of a block entity that wires hang from: the fitting ids, where each wire ends, the power the
 * grid last delivered, and the lifecycle that keeps them honest on both sides.
 *
 * What it settles, so a machine does not have to:
 * <ul>
 * <li>ids are claimed when the entity <em>loads on the server</em>, never in the constructor (a block entity
 *     read from disk is built before its saved ids are read, so a constructor claim leaks a set of ids on every
 *     chunk load);</li>
 * <li>the wire anchors come from the shared fittings table, identically on client and server, and are
 *     recomputed only when something that moves them changes ({@link #refreshAnchors});</li>
 * <li>the client registers the machine for rendering and fitting lookup on load, and forgets it on removal,
 *     without a server ever naming a client class.</li>
 * </ul>
 *
 * Every block entity that carries a fitting extends this, from the pole to a length of ground-laid conductor, so
 * none of them keeps arrays of its own.  A machine says where on its own model a wire lands by overriding
 * {@link #localAnchor} (a tower hangs its chains from the bottom of the string, the inverter's cabinet is drawn
 * smaller than authored, a ground run has no model at all) and leaves the turn onto the world to
 * {@link #computeAnchor}, which is the renderer's own transform; it adds its state on top of
 * {@link #saveAdditional} / {@link #load}.
 */
public abstract class MachineBlockEntity extends BlockEntity implements InsulatorHost {
	private int[] insulatorIds = new int[0];
	private Vec3[] wirePositions = new Vec3[0];
	private double currentPower;

	protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	// ---- the fittings ----

	/** How many fittings this machine carries, from its model definition. */
	protected int fittingCount() {
		ModelDefinition definition = ModelDefinitions.of(getBlockState().getBlock());
		return definition == null ? 0 : definition.insulators().size();
	}

	private void ensureSizes() {
		int count = fittingCount();
		if (insulatorIds.length != count) insulatorIds = java.util.Arrays.copyOf(insulatorIds, count);
		if (wirePositions.length != count) wirePositions = java.util.Arrays.copyOf(wirePositions, count);
	}

	@Override
	public int[] getInsulatorIds() {
		ensureSizes();
		return insulatorIds.clone();
	}

	/** The id of one fitting, 0 while unassigned and -1 for an index the machine does not have. */
	public int getInsulatorId(int index) {
		ensureSizes();
		return index >= 0 && index < insulatorIds.length ? insulatorIds[index] : -1;
	}

	/** The index of the fitting with this id, or -1. */
	public int indexOfInsulator(int id) {
		ensureSizes();
		for (int i = 0; i < insulatorIds.length; i++) {
			if (insulatorIds[i] == id) return i;
		}

		return -1;
	}

	/**
	 * Where fitting {@code index} ends a wire, in the world; null when this machine cannot say yet.
	 *
	 * Read from the cache {@link #refreshAnchors} fills, and worked out on the spot if that has not run (a
	 * wire request can reach a machine in the instant between its being built and its being loaded).
	 */
	@Nullable
	@Override
	public Vec3 getWirePosition(int index) {
		ensureSizes();
		if (index < 0 || index >= wirePositions.length) return null;
		if (wirePositions[index] == null) wirePositions[index] = computeAnchor(index);

		return wirePositions[index];
	}

	/** Recomputes every anchor.  Call when the block loads, turns, changes state or is reconfigured. */
	public void refreshAnchors() {
		ensureSizes();
		for (int i = 0; i < wirePositions.length; i++) {
			wirePositions[i] = computeAnchor(i);
		}
	}

	/**
	 * A new state means anchors may have moved (a turned machine, a kiosk that is wall-mounted) and the clients
	 * have to be told by whoever changed it: this only keeps the numbers on this side true.
	 */
	@Override
	public void setBlockState(BlockState state) {
		super.setBlockState(state);
		refreshAnchors();
	}

	/**
	 * Where fitting {@code index} ends a wire, in the world.
	 *
	 * The point {@link #localAnchor} names on the model, put through the one transform the renderer draws with
	 * ({@link ExtraPose#toWorld}): turned onto the block's facing, set on the block, and - for a machine that is
	 * also an {@link ExtraPose} (the pole's arms, a wall-mounted kiosk, a turbine lowered onto its tower) - moved
	 * by what that says.  Null when there is no such point.
	 */
	@Nullable
	protected Vec3 computeAnchor(int index) {
		Vec3 local = localAnchor(index);
		if (local == null) return null;

		Direction authored = authored();
		return ExtraPose.toWorld(local, getBlockPos(), authored, facing(authored), this instanceof ExtraPose pose ? pose : null);
	}

	/**
	 * Where fitting {@code index} ends a wire on the model, in the model's own frame (blocks; x and z about the
	 * block's vertical axis, y up from its floor, facing as authored); null when the machine cannot say.
	 *
	 * The default is the middle of the top of the fitting's box, which is where a conductor rests on a vertical
	 * insulator or bushing.
	 */
	@Nullable
	protected Vec3 localAnchor(int index) {
		PartBox box = fittingBox(index);
		if (box == null) return null;

		Vector3f top = box.top();
		return new Vec3(top.x(), top.y(), top.z());
	}

	/** The box of fitting {@code index} in the shared fittings table, or null if the table has none. */
	@Nullable
	protected PartBox fittingBox(int index) {
		String group = FittingTypes.nameOf(ModelDefinitions.of(getBlockState().getBlock()), index);
		return group == null ? null : PartBoxes.box(getBlockState().getBlock(), group);
	}

	/** The facing the model was drawn at; one with no model is taken as drawn facing north. */
	protected Direction authored() {
		ModelDefinition definition = ModelDefinitions.of(getBlockState().getBlock());
		return definition == null ? Direction.NORTH : definition.authored();
	}

	/** Which way the block is turned; a machine with no facing is as it was authored. */
	protected Direction facing(Direction authored) {
		return getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
				? getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING) : authored;
	}

	// ---- power ----

	@Override
	public void deliverPower(double power) {
		currentPower = power;
	}

	@Override
	public double getCurrentPower() {
		return currentPower;
	}

	/** For a machine that spends some of what it was delivered before the grid delivers the next. */
	protected void setCurrentPower(double power) {
		currentPower = power;
	}

	// ---- persistence and sync ----

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putIntArray("insulatorIds", getInsulatorIds());
		tag.putDouble("currentPower", currentPower);
	}

	/**
	 * Reads the saved state.  From disk this runs before the machine is in a level, and {@link #onLoad} claims the ids;
	 * on a machine already in a server level it is data pasted over a live one ({@code /clone}, {@code /setblock} with
	 * data, a structure, a creative pick-block), so the ids that came with it are claimed again here: a copy is given
	 * new ones and the original keeps its spans.
	 */
	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		currentPower = tag.getDouble("currentPower");

		int[] saved = tag.getIntArray("insulatorIds");
		ensureSizes();
		boolean pasted = level instanceof ServerLevel && saved.length > 0;
		if (pasted) InsulatorIds.release(insulatorIds);

		for (int i = 0; i < Math.min(saved.length, insulatorIds.length); i++) {
			insulatorIds[i] = saved[i];
			if (saved[i] > 0) InsulatorIds.adopt(saved[i]);
		}

		if (pasted) InsulatorIds.claim(insulatorIds, (ServerLevel) level, worldPosition);
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		saveAdditional(tag);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		load(tag);
		refreshAnchors();
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			InsulatorIndex.register(this, getInsulatorIds());
			ClientWires.invalidate(getInsulatorIds());
		});
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
		CompoundTag tag = packet.getTag();
		if (tag != null) handleUpdateTag(tag);
	}

	// ---- lifecycle ----

	@Override
	public void onLoad() {
		super.onLoad();
		ClientTracking.track(this);

		if (level == null) return;

		ensureSizes();
		if (level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				refreshAnchors();
				InsulatorIndex.register(this, getInsulatorIds());
				ClientWires.invalidate(getInsulatorIds());
			});
		} else {
			InsulatorIds.claim(insulatorIds, (ServerLevel) level, worldPosition);
			refreshAnchors();
			setChanged();
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		ClientTracking.untrack(this);
		InsulatorIds.release(getInsulatorIds());

		if (level != null && level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
				InsulatorIndex.unregister(getInsulatorIds());
				ClientWires.invalidate(getInsulatorIds());
			});
		}
	}
}
