package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.IElectricPowerConsumer;
import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;
import io.github.simonerecchia.renewables.api.power.RenewablesCapabilities;
import io.github.simonerecchia.renewables.core.ServerConfig;
import io.github.simonerecchia.renewables.grid.FittingTypes;
import io.github.simonerecchia.renewables.grid.InsulatorHost;
import io.github.simonerecchia.renewables.model.ExtraPose;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * The end of a line: turns what reaches it into Forge Energy for the blocks beside it, and powers every
 * consumer and every lamp in its field.
 *
 * <b>Each tick</b> (server; it runs before the grid recomputes, so it works on what was delivered the tick
 * before), in this order: the breaker's cool-down counts; Forge Energy is offered to the six neighbours; the
 * field is switched on, refreshed or switched off by what is left of the power; the event that came with the
 * power is forgotten.  The neighbours go first, so a battery against the box is fed before the lamps round it are.
 *
 * <b>The field</b> is every block within {@code ServerConfig.powerBoxRadius()} of it.  A block with a boolean
 * {@code powered} or {@code lit} state is switched on while the field is on - lamps, rails, furnaces, doors,
 * whatever it is, in this mod or another - and is <em>remembered as switched by this box</em>: when the field goes
 * out, only those are put back, and a furnace that was already burning, or a rail a lever was holding, is left
 * exactly as it was.  A block entity in range that offers {@link RenewablesCapabilities#ELECTRIC_CONSUMER} is
 * given its share of the power every tick ({@link PowerShare}).
 *
 * <b>The buffer</b> is real: what nobody used in a tick is stored, up to {@link FePool#CAPACITY}, and a neighbour
 * that takes Forge Energy draws on it before it touches the flow ({@link FePool}).
 *
 * The box also stands a little off the block when it hangs on a wall, which is the one {@link ExtraPose} it has:
 * the model, the pointing and the wire anchors all use it.
 */
public class PowerBoxBlockEntity extends MachineBlockEntity implements ExtraPose {
	/** Below this much delivered power (kW) the field is off. */
	public static final double FIELD_THRESHOLD_KW = 0.1;

	/** The six sides in the order they are offered Forge Energy: down, up, north, south, west, east. */
	private static final Direction[] PUSH_ORDER = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
			Direction.WEST, Direction.EAST};

	/** What is stored, in FE. */
	private int buffer;
	/** Ticks until the breaker re-arms; 0 when it is not tripped. */
	private int tripTicks;
	/** Whether the field is on, which is true from the tick the power passes the threshold. */
	private boolean fieldOn;
	/** The blocks this box switched on (packed positions): the only ones it puts back. */
	private LongSet claimed = new LongOpenHashSet();

	/** The event the grid delivered with this tick's power; it is forgotten at the end of the tick. */
	private PowerDeliveryEvent incoming = PowerDeliveryEvent.NONE;
	/** Consumers found by the last scan of the field. */
	private List<Consumer> consumers = new ArrayList<>();
	/** Whether the buffer has moved since it was last sent to the clients. */
	private boolean unsynced;
	/** Whether something saved has moved since it was last marked: the chunk is marked once a second, not every tick. */
	private boolean dirty;

	private final IEnergyStorage storage = new IEnergyStorage() {
		@Override
		public int receiveEnergy(int maxReceive, boolean simulate) {
			return 0;
		}

		@Override
		public int extractEnergy(int maxExtract, boolean simulate) {
			return extractFe(maxExtract, simulate);
		}

		@Override
		public int getEnergyStored() {
			return energyStored();
		}

		@Override
		public int getMaxEnergyStored() {
			return FePool.CAPACITY;
		}

		@Override
		public boolean canExtract() {
			return true;
		}

		@Override
		public boolean canReceive() {
			return false;
		}
	};

	private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> storage);

	/** A consumer found in the field, with the block entity that carries it so that a removed one can be dropped. */
	private record Consumer(BlockEntity owner, IElectricPowerConsumer consumer) {
	}

	public PowerBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.POWER_BOX.get(), pos, state);
	}

	// ---- the wall ----

	public boolean mounted() {
		return PowerBoxBlock.mounted(getBlockState());
	}

	/** Back by the backset on a wall, nowhere on the ground; in the model's own frame. */
	@Override
	public Vec3 poseOffset() {
		return mounted() ? new Vec3(0.0, 0.0, PowerBoxBlock.BACKSET) : Vec3.ZERO;
	}

	@Override
	public float poseYawDegrees() {
		return 0.0f;
	}

	@Override
	public float posePitchDegrees() {
		return 0.0f;
	}

	// ---- the grid ----

	@Override
	public String fittingType() {
		return FittingTypes.POWER_BOX;
	}

	/** Nothing leaves a kiosk by wire. */
	@Override
	public boolean feeds(InsulatorHost other) {
		return false;
	}

	/** Street bundle only: a kiosk is where a street ends, and neither a feeder nor a transmission line lands on it. */
	@Override
	public boolean takesConductor(ConductorSpec conductor, int index) {
		return conductor.voltageClass() == ConductorSpec.VoltageClass.LOW;
	}

	/**
	 * The disturbance that came with this tick's power.  The grid sets it before the box ticks and the box clears
	 * it after, so it is used once.
	 */
	public void setIncomingEvent(PowerDeliveryEvent event) {
		incoming = event == null ? PowerDeliveryEvent.NONE : event;
	}

	// ---- Forge Energy ----

	/** What the box can give a neighbour now: the buffer and this tick's flow, at most the capacity. */
	public int energyStored() {
		return FePool.stored(buffer, getCurrentPower());
	}

	/** Only what has been banked, without the flow: the figure that is saved. */
	public int energyBuffer() {
		return buffer;
	}

	/**
	 * Takes energy out, for a neighbour or a cable.  The buffer goes first; what the flow has to give comes off
	 * the field's power for the rest of the tick, so what a neighbour takes is not also given to the lamps.
	 */
	private int extractFe(int requested, boolean simulate) {
		FePool.Extraction taken = FePool.extract(buffer, getCurrentPower(), requested);
		if (!simulate && taken.taken() > 0) {
			buffer = taken.newBuffer();
			setCurrentPower(taken.newPowerKw());
			unsynced = true;
			dirty = true;
		}

		return taken.taken();
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.ENERGY) return energy.cast();

		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		energy.invalidate();
	}

	// ---- the tick ----

	/** One server tick.  See the class comment for what happens in which order. */
	public void serverTick(ServerLevel level) {
		if (tripTicks > 0) tripTicks--;

		pushEnergy(level);

		boolean active = getCurrentPower() > FIELD_THRESHOLD_KW;
		if (active) {
			refreshField(level, !fieldOn);
			if (!fieldOn) {
				fieldOn = true;
				setChanged();
			}
		} else {
			if (fieldOn) fieldOff(level);
			// the flow still goes somewhere: whatever a trickle is worth is banked
			bankUnused(getCurrentPower());
		}

		incoming = PowerDeliveryEvent.NONE;

		// the buffer is shown by the wrench and so goes to the people looking, and is marked to be saved, a second at a time
		if (level.getGameTime() % 20 == 0) {
			if (dirty) {
				dirty = false;
				setChanged();
			}
			if (unsynced) {
				unsynced = false;
				level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Offers Forge Energy to each neighbour that takes it, while there is any to offer. */
	private void pushEnergy(ServerLevel level) {
		for (Direction side : PUSH_ORDER) {
			int stored = energyStored();
			if (stored <= 0) return;

			BlockEntity neighbour = LoadedBlockEntities.find(level, worldPosition.relative(side), BlockEntity.class);
			if (neighbour == null) continue;

			neighbour.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).ifPresent(sink -> {
				int accepted = sink.receiveEnergy(Math.min(stored, FePool.MAX_PUSH), false);
				if (accepted > 0) extractFe(accepted, false);
			});
		}
	}

	// ---- the field ----

	/**
	 * Switches on what is in range and powers the consumers there.  The scan - every block in a sphere of up to
	 * seventeen thousand - is done every tick for the small radii and spread out for the big ones; a consumer
	 * found is still served every tick, from the list the last scan left.
	 */
	private void refreshField(ServerLevel level, boolean justSwitchedOn) {
		int radius = Math.max(1, ServerConfig.powerBoxRadius());
		int interval = 1 + PowerFieldShape.count(radius) / 2000;
		if (justSwitchedOn || level.getGameTime() % interval == 0) scan(level, radius);

		consumers.removeIf(entry -> entry.owner().isRemoved());
		if (consumers.isEmpty()) {
			bankUnused(getCurrentPower());
			return;
		}

		List<PowerShare.Demand> demands = new ArrayList<>(consumers.size());
		for (Consumer entry : consumers) {
			demands.add(new PowerShare.Demand(entry.consumer().getRequiredPowerKw(), entry.consumer().getMinimumPowerKw()));
		}

		PowerShare.Outcome outcome = PowerShare.share(getCurrentPower(), demands, incoming, tripTicks > 0, level.random::nextDouble);
		if (outcome.newTrip()) tripTicks = PowerShare.TRIP_TICKS;

		for (int i = 0; i < consumers.size(); i++) {
			PowerShare.Supply supply = outcome.supplies().get(i);
			consumers.get(i).consumer().onPowerSupplied(supply.suppliedKw(), supply.meetsMinimum(), supply.event());
		}

		bankUnused(outcome.unusedKw());
	}

	/** Walks the sphere once: lights what can be lit and remembers it, and finds the consumers. */
	private void scan(ServerLevel level, int radius) {
		int[] offsets = PowerFieldShape.offsets(radius);
		LongSet lit = new LongOpenHashSet();
		List<Consumer> found = new ArrayList<>();

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		long chunkKey = Long.MIN_VALUE;
		LevelChunk chunk = null;

		for (int i = 0; i < offsets.length; i += 3) {
			pos.set(worldPosition.getX() + offsets[i], worldPosition.getY() + offsets[i + 1], worldPosition.getZ() + offsets[i + 2]);
			if (level.isOutsideBuildHeight(pos)) continue;

			long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
			if (key != chunkKey) {
				chunkKey = key;
				chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
			}
			if (chunk == null) continue;

			BlockState state = chunk.getBlockState(pos);
			if (state.isAir()) continue;

			BooleanProperty property = FieldSwitch.of(state);
			if (property != null) {
				if (!state.getValue(property)) {
					// remembered as ours the moment it is ours: only what this box changed is put back
					level.setBlock(pos.immutable(), state.setValue(property, true), Block.UPDATE_ALL);
					lit.add(pos.asLong());
				} else if (claimed.contains(pos.asLong())) {
					lit.add(pos.asLong());
				}
			}

			if (state.hasBlockEntity()) {
				BlockEntity entity = chunk.getBlockEntity(pos);
				if (entity != null) {
					IElectricPowerConsumer consumer = entity.getCapability(RenewablesCapabilities.ELECTRIC_CONSUMER, null).orElse(null);
					if (consumer != null) found.add(new Consumer(entity, consumer));
				}
			}
		}

		// what was ours and is no longer a switchable block there is not ours to put back
		if (!lit.equals(claimed)) dirty = true;
		claimed = lit;
		consumers = found;
	}

	/**
	 * Puts back what this box switched on, tells every consumer the power has stopped, and forgets them.
	 * Called when the power drops below the threshold and when the block is taken away.
	 */
	public void fieldOff(ServerLevel level) {
		if (!claimed.isEmpty()) {
			for (long packed : claimed) {
				BlockPos pos = BlockPos.of(packed);
				LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
				if (chunk == null) continue;

				BlockState state = chunk.getBlockState(pos);
				BooleanProperty property = FieldSwitch.of(state);
				if (property != null && state.getValue(property)) level.setBlock(pos, state.setValue(property, false), Block.UPDATE_ALL);
			}
		}

		for (Consumer entry : consumers) {
			if (!entry.owner().isRemoved()) entry.consumer().onPowerSupplied(0.0, false, PowerDeliveryEvent.NONE);
		}

		claimed = new LongOpenHashSet();
		consumers = new ArrayList<>();
		fieldOn = false;
		setChanged();
	}

	/** Banks what nothing used this tick; see {@link FePool#bank}. */
	private void bankUnused(double unusedKw) {
		int banked = FePool.bank(buffer, unusedKw);
		if (banked != buffer) {
			buffer = banked;
			unsynced = true;
			dirty = true;
		}
	}

	// ---- saving ----

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putInt("buffer", buffer);
		tag.putInt("tripTicks", tripTicks);
		tag.putBoolean("fieldOn", fieldOn);
		tag.putLongArray("claimed", claimed.toLongArray());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		buffer = Math.max(0, Math.min(FePool.CAPACITY, tag.getInt("buffer")));
		tripTicks = Math.max(0, tag.getInt("tripTicks"));
		fieldOn = tag.getBoolean("fieldOn");
		claimed = new LongOpenHashSet(tag.getLongArray("claimed"));
	}
}
