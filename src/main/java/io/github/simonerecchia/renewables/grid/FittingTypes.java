package io.github.simonerecchia.renewables.grid;

import io.github.simonerecchia.renewables.model.ModelDefinition;

import javax.annotation.Nullable;

/**
 * The names a saved span stores for the kind of machine at each end.
 *
 * <b>Never change one</b>: they are written into every world's wire data and into the span packets, and
 * {@link InsulatorHost#fittingType()} must keep returning exactly these.  Kept in one place so that no other
 * file spells a type out.
 */
public final class FittingTypes {
	public static final String UTILITY_POLE = "utility_pole";
	public static final String ELECTRIC_CABIN = "electric_cabin";
	public static final String POWER_BOX = "power_box";
	public static final String WIND_TURBINE = "wind_turbine";
	public static final String PV_INVERTER = "pv_inverter";
	public static final String LATTICE_TOWER = "lattice_tower";
	public static final String TRANSFORMER = "transformer";
	public static final String GROUND_CONDUCTOR = "ground_conductor";
	public static final String SWITCHGEAR = "switchgear";

	/** The power roles a span end can carry, as stored. */
	public static final String POWER_INPUT = "input";
	public static final String POWER_OUTPUT = "output";
	public static final String POWER_BIDIRECTIONAL = "bidirectional";

	private FittingTypes() {
	}

	/**
	 * The model group that fitting {@code index} of a machine is: the name its {@code .obj} gives the
	 * insulator, which is also what the fittings table is keyed on.
	 *
	 * @return null when the machine has no model entry or the index is outside its fittings
	 */
	@Nullable
	public static String nameOf(@Nullable ModelDefinition definition, int index) {
		if (definition == null || index < 0 || index >= definition.insulators().size()) return null;

		return definition.insulators().get(index);
	}
}
