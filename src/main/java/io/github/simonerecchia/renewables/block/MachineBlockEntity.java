package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.client.wire.ClientWires;
import io.github.simonerecchia.renewables.client.wire.InsulatorIndex;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.grid.InsulatorIds;
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
 * A machine overrides {@link #computeAnchor} when its anchor is not "the top of the fitting's porcelain, turned
 * with the block" (towers hang their chains; the pole may be offset and tilted), and adds its own state on top
 * of {@link #saveAdditional} / {@link #load}.
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

	@Nullable
	@Override
	public Vec3 getWirePosition(int index) {
		ensureSizes();
		return index >= 0 && index < wirePositions.length ? wirePositions[index] : null;
	}

	/** Recomputes every anchor.  Call when the block loads, turns, changes state or is reconfigured. */
	public void refreshAnchors() {
		ensureSizes();
		for (int i = 0; i < wirePositions.length; i++) {
			wirePositions[i] = computeAnchor(i);
		}
	}

	/**
	 * Where fitting {@code index} ends a wire, in the world.
	 *
	 * The default is the middle of the top of the fitting's box, in the model's frame, turned onto the block's
	 * facing and set on the block.  Null when the fittings table has no box for it.
	 */
	@Nullable
	protected Vec3 computeAnchor(int index) {
		ModelDefinition definition = ModelDefinitions.of(getBlockState().getBlock());
		String group = FittingTypes.nameOf(definition, index);
		if (group == null) return null;

		PartBox box = PartBoxes.box(getBlockState().getBlock(), group);
		if (box == null) return null;

		Vector3f top = box.top();
		Vec3 turned = ModelFacing.turned(new Vec3(top.x(), top.y(), top.z()), definition.authored(), facing(definition.authored()));
		return Vec3.atLowerCornerOf(getBlockPos()).add(0.5, 0.0, 0.5).add(turned);
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

	// ---- persistence and sync ----

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putIntArray("insulatorIds", getInsulatorIds());
		tag.putDouble("currentPower", currentPower);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		currentPower = tag.getDouble("currentPower");

		int[] saved = tag.getIntArray("insulatorIds");
		ensureSizes();
		for (int i = 0; i < Math.min(saved.length, insulatorIds.length); i++) {
			insulatorIds[i] = saved[i];
			if (saved[i] > 0) InsulatorIds.adopt(saved[i]);
		}
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
			InsulatorIds.claim(insulatorIds);
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
