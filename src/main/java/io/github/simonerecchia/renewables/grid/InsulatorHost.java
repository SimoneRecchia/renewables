package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;
import io.github.simonerecchia.renewables.api.power.Dispatchable;
import io.github.simonerecchia.renewables.api.power.PowerDeliveryEvent;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * A block entity a wire can be hung from.
 *
 * Everything that wires touch asks the same four questions of a machine, and before this there was an
 * {@code instanceof} chain per question in eight files - so adding a machine meant finding all eight.
 * {@link FittingTypes} is the only place that still names a type.
 */
public interface InsulatorHost {
	/** The persistent id of each of this machine's fittings, in the order the model definition lists them. */
	int[] getInsulatorIds();

	/** Where fitting {@code index} is in the world, which is where a wire ends. */
	Vec3 getWirePosition(int index);

	/**
	 * Where a wire running towards {@code toward} ends on fitting {@code index}: {@link #getWirePosition} for every
	 * fitting that holds its wires at one point.  A tension tower holds each phase with two dead-ends, one each side of
	 * the arm, and a span ends at the one on its own side; the server's single point stays the middle of the two.
	 */
	default Vec3 getWirePosition(int index, Vec3 toward) {
		return getWirePosition(index);
	}

	/**
	 * The name a saved connection stores for this kind of machine.
	 *
	 * Never change one: every wire in every existing world holds it.
	 */
	String fittingType();

	/** Whether power may flow from a fitting on this machine to one on that. */
	boolean feeds(InsulatorHost other);

	/**
	 * Whether power passes through this machine rather than stopping at it: a switch, and nothing else.
	 *
	 * Every machine's {@link #feeds} is a list of the machine types it may send to, and the switches are on
	 * none of those eight lists, because they were added to the mod after all of them were written. So
	 * nothing could send power *to* a disconnector or a breaker: a switch put in a line silently blocked it,
	 * and the machinery that makes a switch a switch - {@link #busOf}, the refusal to open under load, the
	 * trip - could never engage, because no power ever arrived.
	 *
	 * A switch has no role of its own; it takes the role of the line it is in, which is why it cannot be on a
	 * list of roles. What may reach it is settled by {@link #takesConductor}: a span to a switch can only be
	 * strung at the switch's own voltage, so only the machines at that voltage can be at the other end of it.
	 */
	default boolean passesThrough() {
		return false;
	}

	/**
	 * Which internal bus a fitting is bonded to.
	  *
	 * Every fitting on one machine shares a bus, so power crosses it - that is what makes a pole a pole and
	 * a transformer a transformer. A switch is the exception: its line side is bus 0 and its load side bus 1, and
	 * the grid solver joins the two through the contacts only while it is {@link #closed}. Without this a switch
	 * could not stop anything, because a cluster is keyed on the block - nor tell the power crossing it from the
	 * power that merely meets at one of its terminals. A transformer whose protection has tripped is the other
	 * exception: every bushing a bus of its own, so nothing crosses the tank.
	 */
	default int busOf(int index) {
		return 0;
	}

	/**
	 * Whether a switch's contacts are closed, joining its two buses; asked only of a machine that
	 * {@link #passesThrough}.  What the grid then delivers to it is the power crossing them.
	 */
	default boolean closed() {
		return true;
	}

	/**
	 * Whether a conductor of this class may be strung to this fitting.
	  *
	 * A pole carries a street bundle or a medium-voltage line; a tower carries transmission and nothing
	 * else. Without this the five conductors are interchangeable, which is what makes them look like four
	 * too many - and a 400 kV quad bundle could be strung to a garden kiosk.
	 */
	default boolean takesConductor(ConductorSpec conductor, int index) {
		return true;
	}

	/**
	 * The conductor this machine is a laid length of: a ground-laid run, and nothing else answers.  Lengths of one
	 * conductor that touch are one conductor, so the grid carries power between them as if a block of it were strung
	 * there ({@link #laidNeighbours}).
	 */
	@Nullable
	default ConductorSpec laidConductor() {
		return null;
	}

	/** Where the lengths this run is joined to lie, by its own shape; empty for every other machine. */
	default List<BlockPos> laidNeighbours() {
		return List.of();
	}

	/**
	 * The longest span this machine will hold, in blocks: a lattice tower's ruling span, and no limit of its own for
	 * anything else.  A span is held to the shorter of this at each end and its conductor's own limit.
	 */
	default double maxSpanBlocks() {
		return Double.POSITIVE_INFINITY;
	}

	/** Whether a fitting is an output, an input, or either. */
	default String powerType(String partName) {
		return "bidirectional";
	}

	/**
	 * Whether this machine draws power from the network, rather than passing it on: where the grid's flow ends.  The
	 * power box, and nothing else; a line with no load at its end carries no current.
	 */
	default boolean isLoad() {
		return false;
	}

	/**
	 * Whether this machine burns something carrying power from one of its fittings to another: a transformer, and
	 * nothing else - every other machine's fittings share a few metres of busbar.
	 */
	default boolean losesInPassing() {
		return false;
	}

	/** What this machine burns carrying {@code throughKw} across itself, in kW; read only when {@link #losesInPassing}. */
	default double throughLossKw(double throughKw) {
		return 0.0;
	}

	/** What the network delivered here, for a machine that shows a reading. */
	default void deliverPower(double power) {
	}

	/**
	 * The disturbance that came with the power {@link #deliverPower} has just been given: a surge, a brownout or a
	 * drop-out, or {@link PowerDeliveryEvent#NONE}.  Called straight after it, every time.  Only the end of a line
	 * (the power box) does anything with it; every other machine may ignore it.
	 */
	default void deliverEvent(PowerDeliveryEvent event) {
	}

	/**
	 * What this machine puts onto the wire network this tick, in kW: what it makes less whatever other mods' cables
	 * took from its energy budget.  Zero for a machine that makes nothing, which is all but a turbine and an inverter.
	 *
	 * A {@link Dispatchable} that does not override this is read from what that contract exposes (see
	 * {@link EnergyBudgets#netKw(Dispatchable)}); one that has a figure of its own overrides it.  It is read once per
	 * calculation, after the machine has opened its budget for the tick.
	 */
	default double getGeneratedPower() {
		return this instanceof Dispatchable generator ? EnergyBudgets.netKw(generator) : 0.0;
	}

	/**
	 * What the network actually carried away from this generator this tick, in kW: what {@link #getGeneratedPower}
	 * offered if a load could be reached, zero if none could.  A generator with an energy budget takes it off the
	 * budget ({@link GeneratorBudget#drawForGrid}), so that no other mod's cable can claim the same energy before
	 * the next tick opens a new one.  Called after every calculation, for every generator in it.
	 */
	default void drawnByGrid(double kw) {
	}

	/**
	 * The wind a generator stands in, when it is one that can disturb the network with gusts; null for every other
	 * machine, and for a generator the weather does not reach.  Only turbines have an answer.
	 */
	@Nullable
	default WindSample windSample() {
		return null;
	}

	/**
	 * What is flowing through this machine, in kW: the figure a power wrench reads off it.
	 *
	 * Every host already had this method - it is what {@link #deliverPower} stores - but it was not on the
	 * interface, so the wrench had to name three types and read nothing off the other eight.
	 */
	double getCurrentPower();
}
