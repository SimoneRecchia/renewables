package io.github.simonerecchia.renewables.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.api.power.MpptInputs;
import io.github.simonerecchia.renewables.api.power.PvArraySpec;
import io.github.simonerecchia.renewables.api.power.PvModuleSpec;
import io.github.simonerecchia.renewables.catalog.InverterCatalog;
import io.github.simonerecchia.renewables.catalog.PvCatalog;

import org.junit.jupiter.api.Test;

/** Strings land on one tracker each and fit under that tracker's current, not under the machine's total. */
class MpptInputsTest {
	private static double stringAmps(PvArraySpec array) {
		return array.stringCurrent(PvModuleSpec.STC_IRRADIANCE, PvModuleSpec.STC_TEMPERATURE);
	}

	/** Arrays of one product landed until the inputs refuse one; answers how many strings went in. */
	private static int landUntilFull(MpptInputs inputs, PvArraySpec array, int arrays) {
		int strings = 0;
		for (int i = 0; i < arrays; i++) {
			if (inputs.landStrings(array.strings(), stringAmps(array))) strings += array.strings();
		}

		return strings;
	}

	/** Two 17.3 A strings do not share a 26 A input: nine trackers take nine, where the summed limit let in thirteen. */
	@Test
	void aBigStringHasATrackerToItself() {
		assertEquals(9, landUntilFull(new MpptInputs(InverterCatalog.VX_110), PvCatalog.HX_700, 20));
		assertEquals(16, landUntilFull(new MpptInputs(InverterCatalog.VX_350), PvCatalog.HX_700, 40));
	}

	/** Two 13.13 A strings are 26.26 A, a quarter of an amp over an SG110CX input: one each, four arrays of two. */
	@Test
	void twoStringsThatJustOverrunAnInputAreSplit() {
		assertEquals(8, landUntilFull(new MpptInputs(InverterCatalog.VX_110), PvCatalog.FT_415, 20));
		// and the smaller heterojunction strings do go two to a tracker, until the terminals run out
		assertEquals(16, landUntilFull(new MpptInputs(InverterCatalog.VX_110), PvCatalog.FT_430, 20));
	}

	/** An array that only partly fits is refused whole and leaves the inputs as they were. */
	@Test
	void aRefusedArrayLandsNothing() {
		MpptInputs inputs = new MpptInputs(2, 2, 26.0);
		assertTrue(inputs.landStrings(3, 10.29));
		double free = inputs.freeAmps();
		assertFalse(inputs.landStrings(4, 10.29));
		assertEquals(free, inputs.freeAmps(), 1e-12);
		assertTrue(inputs.landStrings(1, 10.29));
	}

	/** A trunk lands on the busbars, against what the trackers have left between them: two boxes of sixteen rows. */
	@Test
	void aTrunkFitsUnderTheTrackersTogether() {
		MpptInputs inputs = new MpptInputs(InverterCatalog.VX_350);
		double box = 16 * stringAmps(PvCatalog.TR_580);
		assertTrue(inputs.landTrunk(box));
		assertTrue(inputs.landTrunk(box));
		assertEquals(16 * 30.0 - 2 * box, inputs.freeAmps(), 1e-9);
		assertFalse(inputs.landTrunk(box));
	}

	/** Strings already on the trackers leave the trunk only what is left. */
	@Test
	void stringsComeOutOfWhatATrunkCanHave() {
		MpptInputs inputs = new MpptInputs(InverterCatalog.VX_350);
		double string = stringAmps(PvCatalog.TR_580);
		for (int i = 0; i < 10; i++) {
			assertTrue(inputs.landStrings(1, string));
		}

		assertEquals(480.0 - 10 * string, inputs.freeAmps(), 1e-9);
		// tightest fit: two to a tracker where two fit, so five trackers carry them and eleven are empty
		int empty = 0;
		for (int tracker = 0; tracker < 16; tracker++) {
			if (inputs.ampsOn(tracker) == 0.0) empty++;
		}

		assertEquals(11, empty);
	}
}
