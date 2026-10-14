package io.github.simonerecchia.renewables.client.wire;

import io.github.simonerecchia.renewables.api.power.ConductorSpec;

/** How a conductor from the catalogue is drawn: its colour, thickness, sag and bundle, as the catalogue declares them. */
public final class WireStyles {
	private WireStyles() {
	}

	public static WireStyle of(ConductorSpec spec) {
		return new WireStyle(spec.colour() & 0xFFFFFF, spec.radius(), spec.sag(), spec.subConductors(), spec.bundleSpacing());
	}
}
