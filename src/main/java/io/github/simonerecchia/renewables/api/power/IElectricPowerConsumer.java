package io.github.simonerecchia.renewables.api.power;

/**
 * Something that wants power from the grid.  Expose it as a block entity capability,
 * {@link RenewablesCapabilities#ELECTRIC_CONSUMER}, and a power box in range will feed it.
 *
 * Power is in kW throughout.
 */
public interface IElectricPowerConsumer {
	/** The power it wants to run at full efficiency. */
	double getRequiredPowerKw();

	/** The least it can run on; defaults to what it asks for, i.e. it does not run at all on less. */
	default double getMinimumPowerKw() {
		return getRequiredPowerKw();
	}

	/**
	 * Called once a tick for as long as it is inside an active power field.
	 *
	 * @param suppliedKw       what actually reached it
	 * @param meetsMinimum     whether {@code suppliedKw} is at least {@link #getMinimumPowerKw()}
	 * @param event            any disturbance riding on the supply; {@link PowerDeliveryEvent#NONE} when calm
	 */
	void onPowerSupplied(double suppliedKw, boolean meetsMinimum, PowerDeliveryEvent event);
}
