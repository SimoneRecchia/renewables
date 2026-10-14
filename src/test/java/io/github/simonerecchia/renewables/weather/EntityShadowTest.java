package io.github.simonerecchia.renewables.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** A body's shadow is its box projected down the sun onto the module plane, and only the part that lands on the row counts. */
class EntityShadowTest {
	/** The row at the origin, its module plane 0.47 above the floor of the block. */
	private static final AABB SQUARE = new AABB(0.0, 0.47, 0.0, 1.0, 0.47, 1.0);
	private static final Vec3 OVERHEAD = new Vec3(0.0, 1.0, 0.0);

	/** A player standing on the block beside the row at noon: the old corridor, inflated by a block, took a third. */
	@Test
	void aPlayerBesideTheRowAtNoonCastsNothingOnIt() {
		AABB player = new AABB(1.2, 0.0, 0.2, 1.8, 1.8, 0.8);
		assertEquals(0.0, Shading.shadowOnSquare(player, SQUARE, OVERHEAD), 0.0);
	}

	/** Standing on the row at noon, a player's footprint is what is shaded. */
	@Test
	void aPlayerOnTheRowAtNoonShadesTheirFootprint() {
		AABB player = new AABB(0.2, 0.47, 0.2, 0.8, 2.27, 0.8);
		assertEquals(0.36, Shading.shadowOnSquare(player, SQUARE, OVERHEAD), 1e-9);
	}

	/** In a low morning sun the same player, a block east of the row, throws a long shadow west across it. */
	@Test
	void aLowSunThrowsAShadowAcrossTheRow() {
		Vec3 morning = Shading.towardsSun(new SunPosition(20.0, SunPosition.EAST));
		AABB player = new AABB(1.2, 0.0, 0.2, 1.8, 1.8, 0.8);
		double shaded = Shading.shadowOnSquare(player, SQUARE, morning);
		// the shadow runs from x 1.8 - 1.33/tan20 back to past the row's west edge: the full width, 0.6 deep
		assertEquals(0.6, shaded, 1e-9);
		// and an evening sun puts it on the other side, away from the row
		Vec3 evening = Shading.towardsSun(new SunPosition(20.0, SunPosition.WEST));
		assertEquals(0.0, Shading.shadowOnSquare(player, SQUARE, evening), 0.0);
	}

	/** Below the module plane a body casts nothing on it: a sheep under a tracker's tube does not shade the modules. */
	@Test
	void nothingBelowThePlaneShadesIt() {
		AABB low = new AABB(0.1, 0.0, 0.1, 0.9, 0.4, 0.9);
		assertEquals(0.0, Shading.shadowOnSquare(low, SQUARE, OVERHEAD), 0.0);
	}

	/** A full block across the beam covers all of it; a fence post a quarter; a slab half of a level beam. */
	@Test
	void aShapeCoversWhatItsOutlineCoversAcrossTheBeam() {
		Vec3 level = new Vec3(1.0, 0.0, 0.0);
		assertEquals(1.0, Shading.crossSection(new AABB(0, 0, 0, 1, 1, 1), level), 1e-12);
		assertEquals(0.25, Shading.crossSection(new AABB(0.375, 0, 0.375, 0.625, 1, 0.625), level), 1e-12);
		assertEquals(0.5, Shading.crossSection(new AABB(0, 0, 0, 1, 0.5, 1), level), 1e-12);
		assertTrue(Shading.crossSection(new AABB(0, 0, 0, 1, 1, 1), new Vec3(1, 1, 0).normalize()) > 0.999);
	}
}
