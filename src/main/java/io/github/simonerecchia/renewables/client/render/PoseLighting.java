package io.github.simonerecchia.renewables.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The two lights the entity shader shades with, set for the level and carried into a model's own frame.
 *
 * <ul>
 * <li><b>Which lights.</b> The passes draw at {@code AFTER_SOLID_BLOCKS}, which comes before {@code LevelRenderer} sets
 *     the level's lights for the frame (it does so after the cutout layers): what is in them is whatever the last frame's
 *     GUI left, which turns with the screen and not with the world.  So every pass sets the level's own first.</li>
 * <li><b>Which frame.</b> The shader dots the normal it is handed straight with the lights.  Vanilla turns each normal
 *     into camera space on the CPU every frame; a machine's vertex buffer is built once and holds the model's normals as
 *     authored, so the lights are turned the other way instead, into the frame of the group's pose.  The dot product is
 *     the same, and the shader normalises the light, so a uniform scale costs nothing.</li>
 * <li><b>A scale that is not uniform</b> cannot be carried by the light: {@link #axisScale} finds one along the model's own
 *     axes (the turbine's tower, stretched to its height) so the renderer can bake it into the buffer instead.</li>
 * </ul>
 * Pure maths apart from {@link #applyTo} and {@link #restore}, so it is tested without a game.
 */
@OnlyIn(Dist.CLIENT)
public final class PoseLighting {
	/** {@code Lighting.DIFFUSE_LIGHT_0} and {@code _1}, the level's lights in world space; private in the game. */
	static final Vector3fc LEVEL_0 = new Vector3f(0.2f, 1.0f, -0.7f).normalize();
	static final Vector3fc LEVEL_1 = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
	/** {@code Lighting.NETHER_DIFFUSE_LIGHT_1}: a level with constant ambient light is lit from below as well. */
	static final Vector3fc NETHER_1 = new Vector3f(-0.2f, -1.0f, 0.7f).normalize();

	/** Below this, two axes are taken as square to each other and two scales as equal. */
	private static final float TOLERANCE = 1.0e-4f;

	private final Vector3f view0 = new Vector3f();
	private final Vector3f view1 = new Vector3f();
	private final Vector3f model0 = new Vector3f();
	private final Vector3f model1 = new Vector3f();
	private final Matrix3f inverse = new Matrix3f();

	/**
	 * The level's lights as the camera sees them: {@code view} is the pose of the stage, which at the start of a pass is
	 * the camera's rotation and nothing else.
	 */
	public PoseLighting forView(Matrix4fc view, boolean constantAmbient) {
		view.transformDirection(LEVEL_0, view0).normalize();
		view.transformDirection(constantAmbient ? NETHER_1 : LEVEL_1, view1).normalize();
		return this;
	}

	public Vector3fc view0() {
		return view0;
	}

	public Vector3fc view1() {
		return view1;
	}

	/** The two lights in the frame of {@code pose} (the camera's rotation times the model's), written to the outputs. */
	public void inModelFrame(Matrix4fc pose, Vector3f out0, Vector3f out1) {
		inverse.set(pose);
		if (Math.abs(inverse.determinant()) < 1.0e-12f) {
			out0.set(view0);
			out1.set(view1);
			return;
		}

		inverse.invert();
		inverse.transform(view0, out0).normalize();
		inverse.transform(view1, out1).normalize();
	}

	/** Points the shader's lights at a group drawn with {@code pose}; read by the very next draw. */
	void applyTo(Matrix4fc pose) {
		inModelFrame(pose, model0, model1);
		RenderSystem.setShaderLights(model0, model1);
	}

	/** Sets the level's own lights for a camera whose rotation is {@code view}: for a pass whose normals are in camera space. */
	public static void setLevel(Matrix4fc view, boolean constantAmbient) {
		new PoseLighting().forView(view, constantAmbient).restore();
	}

	/** Leaves the level's lights in place, as copies, so nothing drawn later reads a vector this keeps changing. */
	public void restore() {
		RenderSystem.setShaderLights(new Vector3f(view0), new Vector3f(view1));
	}

	/**
	 * Whether the linear part of {@code pose} is a rotation times a scale along the model's own axes that is not the same
	 * on all three; if so the three scales go into {@code out}.  Its columns are then square to each other and of
	 * different lengths, whatever the camera's turn in front of it.
	 */
	public static boolean axisScale(Matrix4fc pose, Vector3f out) {
		float x = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01() + pose.m02() * pose.m02());
		float y = (float) Math.sqrt(pose.m10() * pose.m10() + pose.m11() * pose.m11() + pose.m12() * pose.m12());
		float z = (float) Math.sqrt(pose.m20() * pose.m20() + pose.m21() * pose.m21() + pose.m22() * pose.m22());
		if (!(x > 0.0f && y > 0.0f && z > 0.0f)) return false;

		float largest = Math.max(x, Math.max(y, z));
		float smallest = Math.min(x, Math.min(y, z));
		if (largest - smallest <= TOLERANCE * largest) return false;

		float xy = (pose.m00() * pose.m10() + pose.m01() * pose.m11() + pose.m02() * pose.m12()) / (x * y);
		float xz = (pose.m00() * pose.m20() + pose.m01() * pose.m21() + pose.m02() * pose.m22()) / (x * z);
		float yz = (pose.m10() * pose.m20() + pose.m11() * pose.m21() + pose.m12() * pose.m22()) / (y * z);
		if (Math.abs(xy) > TOLERANCE || Math.abs(xz) > TOLERANCE || Math.abs(yz) > TOLERANCE) return false;

		out.set(x, y, z);
		return true;
	}

	/** Whether two baked scales are the same for a buffer's purposes. */
	static boolean sameScale(Vector3fc a, Vector3fc b) {
		float largest = Math.max(Math.abs(a.x()), Math.max(Math.abs(a.y()), Math.abs(a.z())));
		return Math.abs(a.x() - b.x()) <= TOLERANCE * largest && Math.abs(a.y() - b.y()) <= TOLERANCE * largest
				&& Math.abs(a.z() - b.z()) <= TOLERANCE * largest;
	}
}
