package io.github.simonerecchia.renewables.api.power;



/**
 * A machine a control system can dispatch: it can be stopped, told how to read redstone, and held to a
 * setpoint, and it publishes a per-tick pool of energy while it runs.
 *
 * The turbine and the inverter both answered to all of it already - identically, method for method - and the
 * Lua peripherals had two copies of the sixteen wrappers because nothing said so.
 */
public interface Dispatchable extends IEnergyBudget {
	/** Whether the machine is turning and allowed to generate, for any reason. */
	boolean isRunning();

	/** Joules produced in the last tick, before anything was claimed. */
	double getGrossJoulesPerTick();

	/**
	 * The unit's nameplate, in kW.
	 *
	 * What a plant controller shares a setpoint out in proportion to, and the ceiling
	 * {@link #setActivePowerLimit} clamps to.
	 */
	double getNameplateKw();

	/**
	 * What the unit is exporting now, in kW.
	 *
	 * Also on {@code InsulatorHost}, and it is the same figure: a machine has one output, whether the
	 * question comes from a wire or from a control system.
	 */
	double getCurrentPower();

	// ---- what a computer may do to it ----

	void setStoppedByComputer(boolean stopped);

	boolean isStoppedByComputer();

	/** Whether the current redstone mode and signal are holding it down. */
	boolean isStoppedByRedstone();

	RedstoneMode getRedstoneMode();

	void setRedstoneMode(RedstoneMode mode);

	/** The curtailment setpoint, in kW. */
	double getActivePowerLimit();

	void setActivePowerLimit(double limitKw);

	// ---- what a computer reads ----

	/**
	 * The energy figures and the running state, all from one tick, for a reader on another thread.
	 *
	 * @param grossJoulesPerTick what the machine produced in the tick, before anything was claimed
	 * @param availableJoules    what was left unclaimed in that tick's budget when it was taken
	 * @param maxJoulesPerTick   the ceiling on what the machine hands out in a tick
	 * @param running            turning and allowed to generate
	 */
	record Reading(double grossJoulesPerTick, double availableJoules, double maxJoulesPerTick, boolean running) {
		public static final Reading NONE = new Reading(0.0, 0.0, 0.0, false);
	}

	/**
	 * The figures a computer reads besides the telemetry.  A machine that publishes one {@link Reading} at the end of its
	 * tick overrides this, so a program never sees the production of one tick against the budget of the next; the default
	 * reads the live state, field by field.
	 */
	default Reading reading() {
		return new Reading(getGrossJoulesPerTick(), getAvailableJoules(), getMaxJoulesPerTick(), isRunning());
	}
}
