package io.github.simonerecchia.renewables.item;


/**
 * The power wrench: aim at a machine and a screen shows what it is doing.  Its whole job is on the client.
 *
 * STUB: owner B for the item (grid-blocks.md section 9.1) with owner C for the client hook it calls
 * (client-net.md section 4): the right-click on a block and in the air, the reach of
 * {@code PanelGuard.WRENCH_REACH}, and the choice of screen by block entity type.
 */
public class PowerWrenchItem extends TooltipItem {
	public PowerWrenchItem(Properties properties) {
		super(properties, "tooltip.renewables.power_wrench");
	}
}
