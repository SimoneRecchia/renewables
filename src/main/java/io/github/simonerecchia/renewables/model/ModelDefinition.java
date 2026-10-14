package io.github.simonerecchia.renewables.model;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Which OBJ draws a machine, which way it was modelled, and which of its groups are wire fittings.
 *
 * Common to client and server: nothing here loads a model, and the server needs the fittings' <em>names</em>
 * (never their geometry) to resolve a saved span's fitting index.
 *
 * @param obj        resource of the model, e.g. {@code renewables:models/pv_inverter/pv_inverter.obj}
 * @param authored   the horizontal direction the model's front faces as it was drawn
 * @param insulators the fitting groups in order.  <b>The order is saved data</b>: a span stores an index into
 *                   this list, so only ever append.
 */
public record ModelDefinition(ResourceLocation obj, Direction authored, List<String> insulators) {
	public ModelDefinition {
		insulators = List.copyOf(insulators);
	}
}
