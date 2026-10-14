package io.github.simonerecchia.renewables.catalog;

import static io.github.simonerecchia.renewables.catalog.Catalogue.id;
import io.github.simonerecchia.renewables.api.power.TransformerSpec;
import io.github.simonerecchia.renewables.api.power.TransformerSpec.Duty;

import java.util.List;

/**
 * The two transformers, which are the two steps a plant's output really takes.
 *
 * A wind generator makes 690 V and a solar inverter 800 V - both low voltage, which is the thing that
 * surprises people: there is no such thing as a machine that generates at medium voltage. So the chain is
 * the same for both technologies and it is two transformers, not one:
 *
 * <ol>
 * <li>a machine unit at each machine's foot, 0.8 kV to 33 kV, which is what makes a collector network
 *     possible at all - at 800 V the cable to carry two megawatts a kilometre would be absurd;</li>
 * <li>a substation unit at the grid connection, 33 kV to 400 kV, and only from there do the towers
 *     start.</li>
 * </ol>
 *
 * The losses are the most the EU Ecodesign regulation (548/2014 as amended by 2019/1783, Tier 2) allows a
 * unit of that size to have, which is what is sold. A 2500 kVA liquid-filled unit with a 36 kV winding may burn
 * 1.81 kW in its core and 20.35 kW in its windings at full load (Table I.1, plus the 15 % and 10 % Table I.3a
 * allows for 36 kV); a 63 MVA unit has to reach a peak efficiency index of 99.745 %, which 28 kW of core and
 * 230 kW of windings just do. The core loss is burned whatever is drawn, which is why a plant that is switched
 * off still costs something to keep energised; the winding loss goes with the square of the load.
 */
public final class TransformerCatalog {
	private static final Catalogue<TransformerSpec> TRANSFORMERS = new Catalogue<>(TransformerSpec::id);

	public static final TransformerSpec MACHINE = TRANSFORMERS.register(new TransformerSpec(
			id("tx_machine"), "VT-2500 Pad", Duty.MACHINE,
			2500.0, 800.0, 33_000.0, 20.35, 1.811, "tx_machine", 3));

	public static final TransformerSpec SUBSTATION = TRANSFORMERS.register(new TransformerSpec(
			id("tx_substation"), "VT-63000 Grid", Duty.SUBSTATION,
			63_000.0, 33_000.0, 400_000.0, 230.0, 28.0, "tx_substation", 6));

	private TransformerCatalog() {
	}

	public static List<TransformerSpec> all() {
		return TRANSFORMERS.all();
	}

}
