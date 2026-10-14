package io.github.simonerecchia.renewables.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.math.Axis;
import java.util.Random;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * A machine is lit by the world's lights, whichever way the camera looks and however the model is turned, tilted or
 * scaled: the lights carried into a group's frame shade its authored normals exactly as vanilla shades a normal it has
 * turned into camera space.
 */
class PoseLightingTest {
	private static final float EPS = 1.0e-4f;

	/** The shader's dot product: the light normalised, the normal as given. */
	private static float shade(Vector3f light, Vector3f normal) {
		return new Vector3f(light).normalize().dot(normal);
	}

	/** A camera: pitch about x, then yaw about y, as GameRenderer turns the stage's pose. */
	private static Matrix4f camera(float pitch, float yaw) {
		return new Matrix4f().rotate(Axis.XP.rotationDegrees(pitch)).rotate(Axis.YP.rotationDegrees(yaw + 180.0f));
	}

	/** A model turned by the block's facing and a pole's yaw and pitch, and scaled, then set somewhere in front of the camera. */
	private static Matrix4f model(float facing, float yaw, float pitch, float scale) {
		return new Matrix4f().translate(3.0f, -2.0f, 7.0f).rotate(Axis.YP.rotationDegrees(facing)).rotate(Axis.YP.rotationDegrees(yaw))
				.rotate(Axis.XP.rotationDegrees(pitch)).scale(scale);
	}

	@Test
	void aFaceIsLitTheSameFromEveryCamera() {
		Random random = new Random(7);
		Matrix4f model = model(90.0f, 25.0f, 8.0f, 1.0f);
		Vector3f normal = new Vector3f(0.3f, 0.2f, -0.93f).normalize();
		// in the world the face points this way, and the world's lights are fixed
		Vector3f worldNormal = new Matrix3f(model).transform(new Vector3f(normal));
		float expected0 = PoseLighting.LEVEL_0.dot(worldNormal);
		float expected1 = PoseLighting.LEVEL_1.dot(worldNormal);

		for (int i = 0; i < 50; i++) {
			Matrix4f view = camera(random.nextFloat() * 180.0f - 90.0f, random.nextFloat() * 360.0f);
			PoseLighting lighting = new PoseLighting().forView(view, false);
			Vector3f l0 = new Vector3f();
			Vector3f l1 = new Vector3f();
			lighting.inModelFrame(new Matrix4f(view).mul(model), l0, l1);

			assertEquals(expected0, shade(l0, normal), EPS, "camera " + i);
			assertEquals(expected1, shade(l1, normal), EPS, "camera " + i);
		}
	}

	@Test
	void aUniformScaleChangesNothing() {
		Matrix4f view = camera(30.0f, 70.0f);
		PoseLighting lighting = new PoseLighting().forView(view, false);
		Vector3f normal = new Vector3f(0.0f, 0.6f, 0.8f);
		Vector3f a = new Vector3f();
		Vector3f b = new Vector3f();
		Vector3f scratch = new Vector3f();

		lighting.inModelFrame(new Matrix4f(view).mul(model(180.0f, 0.0f, 0.0f, 1.0f)), a, scratch);
		lighting.inModelFrame(new Matrix4f(view).mul(model(180.0f, 0.0f, 0.0f, 0.62f)), b, scratch);
		assertEquals(shade(a, normal), shade(b, normal), EPS);
	}

	@Test
	void theTopOfAMachineIsLitAndItsUndersideIsNot() {
		// both level lights come from above: whatever the camera, a roof faces them and a floor faces away
		for (float yaw = 0.0f; yaw < 360.0f; yaw += 45.0f) {
			Matrix4f view = camera(25.0f, yaw);
			PoseLighting lighting = new PoseLighting().forView(view, false);
			Vector3f l0 = new Vector3f();
			Vector3f l1 = new Vector3f();
			lighting.inModelFrame(new Matrix4f(view).mul(model(270.0f, 0.0f, 0.0f, 1.0f)), l0, l1);

			assertTrue(shade(l0, new Vector3f(0, 1, 0)) > 0.7f);
			assertTrue(shade(l0, new Vector3f(0, -1, 0)) < 0.0f);
		}
	}

	@Test
	void aScaleAlongTheModelsAxesIsFoundBehindAnyCameraAndTurn() {
		Matrix4f pose = camera(40.0f, 123.0f).mul(model(90.0f, 0.0f, 0.0f, 1.0f)).scale(0.25f, 0.69f, 0.25f);
		Vector3f scale = new Vector3f();

		assertTrue(PoseLighting.axisScale(pose, scale));
		assertEquals(0.25f, scale.x(), EPS);
		assertEquals(0.69f, scale.y(), EPS);
		assertEquals(0.25f, scale.z(), EPS);
	}

	@Test
	void aUniformScaleOrAShearIsNotBaked() {
		Vector3f scale = new Vector3f();
		assertFalse(PoseLighting.axisScale(camera(10.0f, 20.0f).mul(model(0.0f, 30.0f, 10.0f, 0.62f)), scale));

		// scaled first and turned after: the axes are no longer square to each other
		Matrix4f sheared = new Matrix4f().rotate(Axis.ZP.rotationDegrees(30.0f)).scale(1.0f, 3.0f, 1.0f).rotate(Axis.ZP.rotationDegrees(30.0f));
		assertFalse(PoseLighting.axisScale(sheared, scale));
	}

	@Test
	void aBakedTowerIsLitLikeTheStretchedOne() {
		// the stretched tube's exact shading: the normal through the inverse transpose, normalised
		Matrix4f view = camera(15.0f, 200.0f);
		Matrix4f pose = new Matrix4f(view).mul(model(0.0f, 0.0f, 0.0f, 1.0f)).scale(0.25f, 1.0f, 0.25f);
		Vector3f normal = new Vector3f(0.95f, 0.3f, 0.0f).normalize();
		Vector3f exact = new Matrix3f(pose).invert().transpose().transform(new Vector3f(normal)).normalize();
		PoseLighting lighting = new PoseLighting().forView(view, false);
		float expected = new Vector3f(lighting.view0()).dot(exact);

		// what the renderer does: the scale into the buffer's normals, the rest of the pose left to the lights
		Vector3f scale = new Vector3f();
		assertTrue(PoseLighting.axisScale(pose, scale));
		Vector3f baked = new Vector3f(normal.x() / scale.x(), normal.y() / scale.y(), normal.z() / scale.z()).normalize();
		Vector3f l0 = new Vector3f();
		lighting.inModelFrame(new Matrix4f(pose).scale(1.0f / scale.x(), 1.0f / scale.y(), 1.0f / scale.z()), l0, new Vector3f());

		assertEquals(expected, shade(l0, baked), EPS);
	}

	@Test
	void theNetherIsLitFromBelowAsWell() {
		Matrix4f view = camera(0.0f, 0.0f);
		PoseLighting lighting = new PoseLighting().forView(view, true);
		Vector3f l0 = new Vector3f();
		Vector3f l1 = new Vector3f();
		lighting.inModelFrame(new Matrix4f(view), l0, l1);

		assertTrue(shade(l1, new Vector3f(0, -1, 0)) > 0.7f);
	}
}
