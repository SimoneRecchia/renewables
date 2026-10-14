package io.github.simonerecchia.renewables.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.simonerecchia.renewables.block.ModelFacing;
import io.github.simonerecchia.renewables.model.ExtraPose;
import io.github.simonerecchia.renewables.model.PartBox;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Picking on the real pole, door and fittings: the frame, the nearest-wins rule and the pose. */
class PartSearchTest {
	private static final BlockPos POS = new BlockPos(100, 64, -40);
	private static final List<String> POLE_FITTINGS = List.of("insulator_1_porcelain", "insulator_2_porcelain", "insulator_3_porcelain",
			"insulator_4_porcelain", "insulator_5_porcelain", "insulator_6_porcelain", "insulator_7_porcelain", "insulator_8_porcelain");

	private static MeshModel pole;
	private static MeshModel kiosk;
	private static MeshModel cabin;

	@BeforeAll
	static void load() throws IOException {
		pole = load("models/utility_pole/utility_pole.obj");
		kiosk = load("models/power_box/power_box.obj");
		cabin = load("models/electric_cab/cab.obj");
	}

	private static MeshModel load(String path) throws IOException {
		return ObjModelLoader.load(path, p -> {
			InputStream stream = PartSearchTest.class.getClassLoader().getResourceAsStream("assets/renewables/" + p);
			if (stream == null) throw new FileNotFoundException(p);
			return stream;
		});
	}

	private record Pose(Vec3 offset, float yaw, float pitch) implements ExtraPose {
		@Override
		public Vec3 poseOffset() {
			return offset;
		}

		@Override
		public float poseYawDegrees() {
			return yaw;
		}

		@Override
		public float posePitchDegrees() {
			return pitch;
		}
	}

	private static Vec3 centre(MeshModel model, String part) {
		var c = model.box(part).center();
		return new Vec3(c.x(), c.y(), c.z());
	}

	private static PartFrame frame(Direction facing) {
		return PartFrame.of(POS, Direction.NORTH, facing, null);
	}

	/** The pole, kiosk or cabin of a block at {@link #POS} with the given pose, ready to be asked. */
	private static PartSearch search(MeshModel model, List<String> fittings, Direction authored, Direction facing, ExtraPose pose) {
		return new PartSearch(model, PartFrame.of(POS, authored, facing, pose), fittings);
	}

	// ---- the frame is the drawing's frame ----

	@Test
	void theFrameAgreesWithToWorldForEveryFacingAndAPose() {
		Pose pose = new Pose(new Vec3(0.4, -0.25, 0.7), 37.0f, -12.0f);
		Vec3[] points = {Vec3.ZERO, new Vec3(1, 2, 3), new Vec3(-2.6, 7.6, 0.05), new Vec3(0.3, 9.9, -4)};

		for (Direction facing : Direction.Plane.HORIZONTAL) {
			for (ExtraPose p : new ExtraPose[] {null, pose}) {
				PartFrame frame = PartFrame.of(POS, Direction.NORTH, facing, p);
				for (Vec3 point : points) {
					Vec3 expected = ExtraPose.toWorld(point, POS, Direction.NORTH, facing, p);
					Vec3 actual = frame.toWorld(point);
					assertEquals(0.0, expected.distanceTo(actual), 1e-4, facing + " " + point);
					// and back again
					assertEquals(0.0, point.distanceTo(frame.toLocalPoint(expected)), 1e-4);
				}
			}
		}
	}

	@Test
	void aQuarterTurnUsesTheModelFacingArithmetic() {
		// authored north placed facing east is 270 degrees about the vertical: the model's -z front points east
		Vec3 front = PartFrame.of(POS, Direction.NORTH, Direction.EAST, null).toWorld(new Vec3(0, 0, -1));
		Vec3 viaFacing = Vec3.atLowerCornerOf(POS).add(0.5, 0, 0.5).add(ModelFacing.turned(new Vec3(0, 0, -1), Direction.NORTH, Direction.EAST));
		assertEquals(0.0, front.distanceTo(viaFacing), 1e-9);
	}

	// ---- picking on the pole ----

	@Test
	void aRayAtTheCentreOfEachFittingHitsThatFittingForEveryFacing() {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			PartSearch search = search(pole, POLE_FITTINGS, Direction.NORTH, facing, null);
			for (String fitting : POLE_FITTINGS) {
				Vec3 target = search.center(fitting);
				assertNotNull(target);
				// from in front of the model, along its front-to-back line: the arms run across it, so no other insulator
				// of the pole is in the way of this one
				Vec3 front = frame(facing).toWorld(new Vec3(0, 0, -1)).subtract(frame(facing).toWorld(Vec3.ZERO));
				Vec3 origin = target.subtract(front.scale(6));
				PartSearch.Hit hit = search.fitting(origin, front);
				assertEquals(fitting, hit.name(), facing + " " + fitting);
				assertTrue(search.isPointed(fitting, origin, front));
			}
		}
	}

	@Test
	void aRayBesideAFittingMissesIt() {
		PartSearch search = search(pole, POLE_FITTINGS, Direction.NORTH, Direction.NORTH, null);
		Vec3 target = search.center("insulator_1_porcelain");
		Vec3 aside = target.add(0.5, 0, 6);
		assertFalse(search.isPointed("insulator_1_porcelain", aside, new Vec3(0, 0, -1)));
	}

	@Test
	void whereBoxesOverlapAlongTheRayTheNearerFittingWins() {
		// two post insulators one behind the other on the same line of sight
		PartBox front = new PartBox(new org.joml.Vector3f(-0.1f, 0, -2.1f), new org.joml.Vector3f(0.1f, 1, -1.9f));
		PartBox back = new PartBox(new org.joml.Vector3f(-0.1f, 0, 1.9f), new org.joml.Vector3f(0.1f, 1, 2.1f));
		java.util.Map<String, MeshModel.Group> groups = new java.util.LinkedHashMap<>();
		// listed far one first: the order of the list must not decide it
		groups.put("far", group("far", back));
		groups.put("near", group("near", front));
		MeshModel model = new MeshModel(groups, List.of());

		PartSearch search = new PartSearch(model, PartFrame.of(POS, Direction.NORTH, Direction.NORTH, null), List.of("far", "near"));
		Vec3 from = Vec3.atLowerCornerOf(POS).add(0.5, 0.5, -6);
		assertEquals("near", search.fitting(from, new Vec3(0, 0, 1)).name());

		// from the other side the other is nearer
		Vec3 other = Vec3.atLowerCornerOf(POS).add(0.5, 0.5, 6);
		assertEquals("far", search.fitting(other, new Vec3(0, 0, -1)).name());
	}

	@Test
	void aTieGoesToTheLowerFittingIndex() {
		PartBox box = new PartBox(new org.joml.Vector3f(-0.1f, 0, -0.1f), new org.joml.Vector3f(0.1f, 1, 0.1f));
		java.util.Map<String, MeshModel.Group> groups = new java.util.LinkedHashMap<>();
		groups.put("b", group("b", box));
		groups.put("a", group("a", box));
		MeshModel model = new MeshModel(groups, List.of());
		PartSearch search = new PartSearch(model, PartFrame.of(POS, Direction.NORTH, Direction.NORTH, null), List.of("a", "b"));

		Vec3 from = Vec3.atLowerCornerOf(POS).add(0.5, 0.5, -6);
		assertEquals("a", search.fitting(from, new Vec3(0, 0, 1)).name());
	}

	@Test
	void aFittingWinsATieWithTheBodyItSitsOnButTheNearerBodyWinsOtherwise() {
		PartSearch search = search(kiosk, List.of("insulator_porcelain"), Direction.NORTH, Direction.NORTH, null);
		Vec3 top = search.center("insulator_porcelain");
		assertNotNull(top);

		// straight down onto the roof insulator: it is the nearest thing
		assertEquals("insulator_porcelain", search.any(top.add(0, 4, 0), new Vec3(0, -1, 0)).name());
		assertEquals("insulator_porcelain", search.fitting(top.add(0, 4, 0), new Vec3(0, -1, 0)).name());

		// from the side at the height of the body, a body group is entered, and no fitting is
		Vec3 sideOrigin = Vec3.atLowerCornerOf(POS).add(0.5, 0.3, 5);
		PartSearch.Hit body = search.any(sideOrigin, new Vec3(0, 0, -1));
		assertTrue(body.found());
		assertFalse(body.name().startsWith("pivot_"));
		assertNull(search.fitting(sideOrigin, new Vec3(0, 0, -1)).name());
	}

	@Test
	void theEntryPointIsOnTheNearFaceOfTheBox() {
		PartSearch search = search(kiosk, List.of("insulator_porcelain"), Direction.NORTH, Direction.NORTH, null);
		Vec3 origin = Vec3.atLowerCornerOf(POS).add(0.5, 0.3, 5);
		Vec3 hit = search.entryPoint(origin, new Vec3(0, 0, -2));
		assertNotNull(hit);
		assertEquals(origin.x, hit.x, 1e-9);
		assertEquals(origin.y, hit.y, 1e-9);
		assertTrue(hit.z < origin.z && hit.z > POS.getZ() - 1, "inside the block, at the face the ray meets first");

		assertNull(search.entryPoint(origin.add(0, 10, 0), new Vec3(0, 0, -1)), "over the top of everything");
	}

	@Test
	void theMachineBehindTheOriginIsNotPointedAt() {
		PartSearch search = search(cabin, List.of("insulator_input_porcelain"), Direction.NORTH, Direction.NORTH, null);
		Vec3 target = search.center("insulator_input_porcelain");
		assertFalse(search.isPointed("insulator_input_porcelain", target.add(0, 0, 6), new Vec3(0, 0, 1)));
		assertTrue(search.isPointed("insulator_input_porcelain", target.add(0, 0, 6), new Vec3(0, 0, -1)));
	}

	// ---- the pole's extra pose ----

	@Test
	void withAnOffsetYawAndPitchTheRayHitsWhereTheModelIsDrawn() {
		Pose pose = new Pose(new Vec3(0.6, -0.4, 0.5), 33.0f, 20.0f);
		for (Direction facing : List.of(Direction.NORTH, Direction.WEST)) {
			PartSearch posed = search(pole, POLE_FITTINGS, Direction.NORTH, facing, pose);

			for (String fitting : POLE_FITTINGS) {
				// where the renderer's chain puts the middle of this insulator
				var local = pole.box(fitting).center();
				Vec3 drawn = ExtraPose.toWorld(new Vec3(local.x(), local.y(), local.z()), POS, Direction.NORTH, facing, pose);

				// seen from three different sides, along a line that goes through that point
				for (Vec3 away : List.of(new Vec3(0, 0, 8), new Vec3(8, 0, 0), new Vec3(5, 5, 5))) {
					Vec3 origin = drawn.add(away);
					Vec3 direction = away.scale(-1);
					assertTrue(posed.isPointed(fitting, origin, direction), facing + " " + fitting + " from " + away);
				}

				// and a point one block to the side of the drawn place is not it
				assertFalse(posed.isPointed(fitting, drawn.add(0, 0, 8).add(0, 1.5, 0), new Vec3(0, 0, -1)), fitting + " is not 1.5 blocks higher");
			}

			// the same line through the *unposed* position of the first fitting no longer finds it where it was
			PartSearch plain = search(pole, POLE_FITTINGS, Direction.NORTH, facing, null);
			Vec3 undisplaced = plain.center("insulator_1_porcelain");
			assertFalse(posed.isPointed("insulator_1_porcelain", undisplaced.add(0, 0, 8), new Vec3(0, 0, -1)));
		}
	}

	@Test
	void aTowerDrawnLowerIsPickedLower() {
		// the wind turbine's frame: the model starts at the foot of the tower, S blocks under the machine block
		Pose lowered = new Pose(new Vec3(0, -6, 0), 0, 0);
		PartSearch search = search(kiosk, List.of("insulator_porcelain"), Direction.NORTH, Direction.NORTH, lowered);
		Vec3 normal = search(kiosk, List.of("insulator_porcelain"), Direction.NORTH, Direction.NORTH, null).center("insulator_porcelain");
		Vec3 low = search.center("insulator_porcelain");
		assertEquals(normal.y - 6, low.y, 1e-5);
		assertEquals(normal.x, low.x, 1e-5);
		assertEquals(normal.z, low.z, 1e-5);
	}

	private static MeshModel.Group group(String name, PartBox box) {
		float[] p = {box.min().x(), box.min().y(), box.min().z(), box.max().x(), box.max().y(), box.max().z(), box.max().x(), box.min().y(), box.min().z()};
		return new MeshModel.Group(name, name, "m", "t.png", p, new float[9], new float[6]);
	}
}
