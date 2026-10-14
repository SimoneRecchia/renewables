package io.github.simonerecchia.renewables.client.wire;

import io.github.simonerecchia.renewables.client.hooks.WirePreview;
import io.github.simonerecchia.renewables.client.render.MachineRenderer;
import io.github.simonerecchia.renewables.client.render.PoseLighting;
import io.github.simonerecchia.renewables.core.ClientConfig;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws every span, and the preview of the one being joined, once per frame (docs/spec/client-net.md section 6).
 *
 * A cable is a tube of 12 sides at most, running through the points of its {@link WireProfile}.  The tube is built
 * ring by ring and neighbouring segments share their ring, with the ring's axis halfway between the two segments:
 * the joints are smooth instead of showing a notch, and a ring is computed once for two segments.
 *
 * <b>Nothing is allocated per frame</b> in the loop over spans: the geometry of a span is cached in its
 * {@link ClientSpan} until a machine at one of its ends moves, the rings are written into static buffers, vertices go
 * straight into the render buffer as numbers, and the frustum test reuses the span's own box.
 *
 * What is skipped: a span whose box is beyond the configured distance (128 blocks by default), one outside the
 * frustum, one whose ends cannot be found yet, and one whose machine at either end is beyond its own renderer's range.
 * What is simplified with distance: see {@link WireLod}.
 */
@OnlyIn(Dist.CLIENT)
public final class WireRenderer {
	/** A plain white texture, so the colour a conductor declares is the colour that is seen (wool would make it black). */
	private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");

	/** Light is looked up this many game ticks apart: it changes with the day, not with the frame. */
	private static final int LIGHT_TICKS = 10;
	/** The preview of a span longer than the conductor allows is this colour, so it is seen before the click. */
	public static final int TOO_LONG_COLOUR = 0xB33A2E;

	private static final int MAX_SIDES = 12;
	private static final float[] COS_12 = table(12, true);
	private static final float[] SIN_12 = table(12, false);
	private static final float[] COS_6 = table(6, true);
	private static final float[] SIN_6 = table(6, false);
	private static final float[] COS_4 = table(4, true);
	private static final float[] SIN_4 = table(4, false);

	// scratch, reused by every cable: positions and normals of two rings, in camera space
	private static float[] ringAPos = new float[3 * MAX_SIDES];
	private static float[] ringANormal = new float[3 * MAX_SIDES];
	private static float[] ringBPos = new float[3 * MAX_SIDES];
	private static float[] ringBNormal = new float[3 * MAX_SIDES];
	private static final Vector3f SCRATCH = new Vector3f();
	private static final double[] ACROSS = new double[2];
	private static final double[] FRAME = new double[6];
	private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

	// the centre and the axis of the ring in A, kept so it can be rebuilt with another number of sides
	private static double aX, aY, aZ, aTx, aTy, aTz;
	private static int aSides;

	private static long frame;
	private static RenderType renderType;

	private WireRenderer() {
	}

	private static RenderType type() {
		if (renderType == null) renderType = RenderType.entitySolid(WHITE);
		return renderType;
	}

	/** The pass: called from the level renderer after the solid blocks. */
	public static void render(RenderLevelStageEvent event) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) return;

		List<ClientSpan> spans = ClientWires.spans();
		PendingLink pending = ClientWires.pending();
		if (spans.isEmpty() && pending == null) return;

		Vec3 camera = event.getCamera().getPosition();
		Frustum frustum = event.getFrustum();
		PoseStack.Pose pose = event.getPoseStack().last();
		MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
		RenderType type = type();
		VertexConsumer consumer = null;

		double maxDistance = ClientConfig.wireDrawDistance();
		double maxDistanceSq = maxDistance * maxDistance;
		long lightStamp = level.getGameTime() / LIGHT_TICKS;
		frame++;

		for (int i = 0; i < spans.size(); i++) {
			ClientSpan span = spans.get(i);
			if (!span.prepare(level, frame)) continue;

			double distanceSq = span.distanceSqTo(camera.x, camera.y, camera.z);
			if (distanceSq > maxDistanceSq) continue;
			if (!frustum.isVisible(span.bounds)) continue;
			if (!endDrawn(level, span.wire.startBlockPos(), camera) || !endDrawn(level, span.wire.endBlockPos(), camera)) continue;

			if (span.lightStamp != lightStamp) refreshLight(level, span, lightStamp);
			if (consumer == null) consumer = buffers.getBuffer(type);

			drawCable(consumer, pose, span.profile, span.style, span.sx, span.sy, span.sz, camera.x, camera.y, camera.z,
					span.lightStart, span.lightMiddle, span.lightEnd, span.style.colour(), Math.sqrt(distanceSq));
		}

		if (pending != null) {
			if (consumer == null) consumer = buffers.getBuffer(type);
			WirePreview.draw(consumer, pose, level, player, pending, camera, event.getPartialTick());
		}

		if (consumer == null) return;

		// the normals are in camera space; the level's lights are not set yet at this stage of the frame
		PoseLighting.setLevel(event.getPoseStack().last().pose(), level.effects().constantAmbientLight());
		buffers.endBatch(type);
	}

	/** Whether the machine at one end of a span is drawn from here: a cable is never drawn hanging from nothing. */
	private static boolean endDrawn(ClientLevel level, BlockPos pos, Vec3 camera) {
		BlockEntity machine = level.getBlockEntity(pos);
		return machine == null || MachineRenderer.inRange(machine, camera);
	}

	// ---- light ----

	private static void refreshLight(ClientLevel level, ClientSpan span, long stamp) {
		span.lightStamp = stamp;
		WireProfile profile = span.profile;
		int middle = profile.segments() / 2;

		span.lightStart = sampleLight(level, span.sx, span.sy, span.sz);
		span.lightMiddle = sampleLight(level, span.sx + profile.x(middle), span.sy + profile.y(middle), span.sz + profile.z(middle));
		span.lightEnd = sampleLight(level, span.ex, span.ey, span.ez);
	}

	/**
	 * The light at a point on a cable, which is in the air above whatever holds it: the brighter of the cell it is in
	 * and the one above, because an end that sits inside a machine's cell would otherwise be black.
	 */
	public static int sampleLight(ClientLevel level, double x, double y, double z) {
		int here = LevelRenderer.getLightColor(level, CURSOR.set(Math.floor(x), Math.floor(y), Math.floor(z)));
		int above = LevelRenderer.getLightColor(level, CURSOR.set(Math.floor(x), Math.floor(y) + 1, Math.floor(z)));

		int block = Math.max((here >> 4) & 15, (above >> 4) & 15);
		int sky = Math.max((here >> 20) & 15, (above >> 20) & 15);
		return (block << 4) | (sky << 20);
	}

	// ---- one cable: a conductor, or a bundle of them ----

	/**
	 * Draws one span's profile as one wire or as a bundle.
	 *
	 * @param nearest distance from the camera to the span: decides the bundle's merging
	 */
	public static void drawCable(VertexConsumer consumer, PoseStack.Pose pose, WireProfile profile, WireStyle style,
			double startX, double startY, double startZ, double cameraX, double cameraY, double cameraZ,
			int lightStart, int lightMiddle, int lightEnd, int colour, double nearest) {
		int last = profile.segments();
		if (last < 1) return;

		boolean merged = WireLod.mergeBundle(nearest, style.count());
		int wires = merged ? 1 : BundleLayout.wires(style.count());
		double radius = merged ? WireLod.mergedRadius(style.radius(), style.spacing(), style.count()) : style.radius();
		BundleLayout.across(profile.x(last), profile.z(last), ACROSS);

		for (int wire = 0; wire < wires; wire++) {
			double across = merged ? 0.0 : BundleLayout.x(style.count(), wire, style.spacing());
			double up = merged ? 0.0 : BundleLayout.y(style.count(), wire, style.spacing());

			drawWire(consumer, pose, profile,
					startX + ACROSS[0] * across - cameraX, startY + up - cameraY, startZ + ACROSS[1] * across - cameraZ,
					radius, colour, lightStart, lightMiddle, lightEnd);
		}
	}

	/** One tube along the profile, whose first point is at {@code (bx, by, bz)} relative to the camera. */
	private static void drawWire(VertexConsumer consumer, PoseStack.Pose pose, WireProfile profile,
			double bx, double by, double bz, double radius, int colour, int lightStart, int lightMiddle, int lightEnd) {
		int last = profile.segments();
		int red = (colour >> 16) & 255;
		int green = (colour >> 8) & 255;
		int blue = colour & 255;
		Matrix4f matrix = pose.pose();
		Matrix3f normalMatrix = pose.normal();

		int from = 0;
		double distance = Math.sqrt(sq(bx + profile.x(0)) + sq(by + profile.y(0)) + sq(bz + profile.z(0)));
		int to = WireLod.next(from, last, WireLod.stride(distance));

		// the first ring faces along the first segment
		double dx = profile.x(to) - profile.x(from);
		double dy = profile.y(to) - profile.y(from);
		double dz = profile.z(to) - profile.z(from);
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.0e-9) return;

		double tx = dx / length, ty = dy / length, tz = dz / length;

		aX = bx + profile.x(from);
		aY = by + profile.y(from);
		aZ = bz + profile.z(from);
		aTx = tx;
		aTy = ty;
		aTz = tz;
		aSides = 0;

		while (from < last) {
			int sides = WireLod.sides(distance);
			if (aSides != sides) buildRing(ringAPos, ringANormal, matrix, normalMatrix, aX, aY, aZ, aTx, aTy, aTz, radius, sides);
			aSides = sides;

			// this segment's direction, and the next one's, so the ring between them is turned to the half-way
			dx = profile.x(to) - profile.x(from);
			dy = profile.y(to) - profile.y(from);
			dz = profile.z(to) - profile.z(from);
			length = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (length < 1.0e-9) return;

			double sx = dx / length, sy = dy / length, sz = dz / length;

			double mx = sx, my = sy, mz = sz;
			double bX = bx + profile.x(to), bY = by + profile.y(to), bZ = bz + profile.z(to);
			double farDistance = Math.sqrt(sq(bX) + sq(bY) + sq(bZ));
			int after = to;
			if (to < last) {
				after = WireLod.next(to, last, WireLod.stride(farDistance));
				double nx = profile.x(after) - profile.x(to);
				double ny = profile.y(after) - profile.y(to);
				double nz = profile.z(after) - profile.z(to);
				double nlength = Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (nlength > 1.0e-9) {
					mx = sx + nx / nlength;
					my = sy + ny / nlength;
					mz = sz + nz / nlength;
					double mlength = Math.sqrt(mx * mx + my * my + mz * mz);
					if (mlength > 1.0e-6) {
						mx /= mlength;
						my /= mlength;
						mz /= mlength;
					} else {
						mx = sx;
						my = sy;
						mz = sz;
					}
				}
			}

			buildRing(ringBPos, ringBNormal, matrix, normalMatrix, bX, bY, bZ, mx, my, mz, radius, sides);
			int lightA = lightAt((float) from / last, lightStart, lightMiddle, lightEnd);
			int lightB = lightAt((float) to / last, lightStart, lightMiddle, lightEnd);
			emit(consumer, ringAPos, ringANormal, ringBPos, ringBNormal, sides, red, green, blue, lightA, lightB);

			// B becomes A
			float[] swap = ringAPos;
			ringAPos = ringBPos;
			ringBPos = swap;
			swap = ringANormal;
			ringANormal = ringBNormal;
			ringBNormal = swap;
			aX = bX;
			aY = bY;
			aZ = bZ;
			aTx = mx;
			aTy = my;
			aTz = mz;

			from = to;
			to = after;
			distance = farDistance;
		}
	}

	private static int lightAt(float t, int start, int middle, int end) {
		return t < 0.5f ? WireLod.mixLight(start, middle, t * 2.0f) : WireLod.mixLight(middle, end, (t - 0.5f) * 2.0f);
	}

	// ---- the tube ----

	/**
	 * A ring of {@code sides} vertices round a point, in the plane across {@code (tx, ty, tz)}, already carried through
	 * the pose matrices into camera space.  Normals point out from the axis.
	 */
	private static void buildRing(float[] positions, float[] normals, Matrix4f matrix, Matrix3f normalMatrix,
			double cx, double cy, double cz, double tx, double ty, double tz, double radius, int sides) {
		TubeBasis.frame(tx, ty, tz, FRAME);
		double ux = FRAME[0], uy = FRAME[1], uz = FRAME[2];
		double vx = FRAME[3], vy = FRAME[4], vz = FRAME[5];

		float[] cos = cosines(sides);
		float[] sin = sines(sides);
		for (int k = 0; k < sides; k++) {
			double nx = cos[k] * ux + sin[k] * vx;
			double ny = cos[k] * uy + sin[k] * vy;
			double nz = cos[k] * uz + sin[k] * vz;

			matrix.transformPosition((float) (cx + radius * nx), (float) (cy + radius * ny), (float) (cz + radius * nz), SCRATCH);
			positions[3 * k] = SCRATCH.x;
			positions[3 * k + 1] = SCRATCH.y;
			positions[3 * k + 2] = SCRATCH.z;

			normalMatrix.transform((float) nx, (float) ny, (float) nz, SCRATCH);
			normals[3 * k] = SCRATCH.x;
			normals[3 * k + 1] = SCRATCH.y;
			normals[3 * k + 2] = SCRATCH.z;
		}
	}

	/**
	 * The quads between two rings.  Each side takes the whole width of the texture round the perimeter and the whole
	 * height along the segment; the order is counter-clockwise seen from outside, so the inside of the tube is culled.
	 */
	private static void emit(VertexConsumer consumer, float[] aPos, float[] aNormal, float[] bPos, float[] bNormal, int sides,
			int red, int green, int blue, int lightA, int lightB) {
		for (int k = 0; k < sides; k++) {
			int next = k + 1 == sides ? 0 : k + 1;

			for (int corner = 0; corner < 4; corner++) {
				boolean far = TubeBasis.CORNERS[corner][0] == 1;
				int at = TubeBasis.CORNERS[corner][1] == 0 ? k : next;
				vertex(consumer, far ? bPos : aPos, far ? bNormal : aNormal, at, TubeBasis.CORNER_U[corner],
						TubeBasis.CORNER_V[corner], red, green, blue, far ? lightB : lightA);
			}
		}
	}

	private static void vertex(VertexConsumer consumer, float[] positions, float[] normals, int k, float u, float v,
			int red, int green, int blue, int light) {
		consumer.vertex(positions[3 * k], positions[3 * k + 1], positions[3 * k + 2])
				.color(red, green, blue, 255)
				.uv(u, v)
				.overlayCoords(OverlayTexture.NO_OVERLAY)
				.uv2(light)
				.normal(normals[3 * k], normals[3 * k + 1], normals[3 * k + 2])
				.endVertex();
	}

	// ---- tables ----

	private static float[] table(int sides, boolean cosine) {
		float[] values = new float[sides];
		for (int k = 0; k < sides; k++) {
			double angle = 2.0 * Math.PI * k / sides;
			values[k] = (float) (cosine ? Math.cos(angle) : Math.sin(angle));
		}

		return values;
	}

	private static float[] cosines(int sides) {
		return sides >= 12 ? COS_12 : sides >= 6 ? COS_6 : COS_4;
	}

	private static float[] sines(int sides) {
		return sides >= 12 ? SIN_12 : sides >= 6 ? SIN_6 : SIN_4;
	}

	private static double sq(double value) {
		return value * value;
	}
}
