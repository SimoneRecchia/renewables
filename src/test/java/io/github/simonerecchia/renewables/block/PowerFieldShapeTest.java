package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PowerFieldShapeTest {
	/** The counts grid.md section 12.2 gives: they are the whole definition of "a sphere of whole blocks". */
	@Test
	void countsMatchTheSpecification() {
		assertEquals(6, PowerFieldShape.count(1));
		assertEquals(514, PowerFieldShape.count(5));
		assertEquals(17_076, PowerFieldShape.count(16));
	}

	@Test
	void aRadiusBelowOneIsOne() {
		assertEquals(6, PowerFieldShape.count(0));
		assertEquals(6, PowerFieldShape.count(-3));
	}

	@Test
	void everyOffsetIsInsideTheSphereAndTheCentreIsNotOne() {
		int radius = 5;
		int[] offsets = PowerFieldShape.offsets(radius);
		boolean sawAxisPoint = false;

		for (int i = 0; i < offsets.length; i += 3) {
			int dx = offsets[i];
			int dy = offsets[i + 1];
			int dz = offsets[i + 2];
			assertTrue(dx * dx + dy * dy + dz * dz <= radius * radius);
			assertFalse(dx == 0 && dy == 0 && dz == 0);
			if (dx == radius && dy == 0 && dz == 0) sawAxisPoint = true;
		}

		// the edge is in: <= radius squared, not < radius squared
		assertTrue(sawAxisPoint);
	}

	@Test
	void theListIsSharedBetweenCalls() {
		assertTrue(PowerFieldShape.offsets(5) == PowerFieldShape.offsets(5));
	}
}
