package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.DcCableBlock;
import io.github.simonerecchia.renewables.block.ModelFacing;
import io.github.simonerecchia.renewables.client.ClientBlockEntities;
import io.github.simonerecchia.renewables.client.model.MeshModel;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.model.ExtraPose;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The shared pass that draws every machine whose model is an OBJ (docs/spec/models.md section 6).
 *
 * Machines are not drawn by a block entity renderer nor baked into chunks: once a frame, after the solid blocks, each
 * renderer walks the machines of its type that the client is tracking and draws their model groups from vertex
 * buffers, with the "entity cutout, no cull" shader.  A renderer subclasses this only to inherit these statics and
 * subscribes its own {@code RenderLevelStageEvent}; it says what to draw and with which pose, and owns no buffer.
 *
 * <ul>
 * <li>The buffers are kept here, one cache per block entity type: one buffer per (block position, group), built the
 *     first time the group is drawn and again when the machine's light or the group's texture changes.  The buffers
 *     of a position its type's pass left out (out of range, filtered) are closed, and all of them are when the level
 *     changes or the resources reload; a machine only off screen keeps them.  A cache per type, not one for all,
 *     because a pass only knows which of its own machines it saw; it also keeps a turbine and the bare tower it
 *     replaced, at the same position, from sharing buffers.  Every build goes through one shared builder.</li>
 * <li>A machine whose box is outside the frustum is not drawn.</li>
 * <li>Within a machine the groups are batched by texture: one render state per texture, one draw per group.  The
 *     level's two lights are turned into each group's frame for its draw ({@link PoseLighting}).</li>
 * <li>A group that is given no matrix is not drawn, which is how a renderer hides a part.  Markers
 *     ({@code pivot_*}) are never drawn.</li>
 * <li>Light is sampled once, at the machine's own block and the one above it ({@link #lightAt}).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public abstract class MachineRenderer {
	/** A group's vertex buffer on the GPU, kept per (block position, group name). */
	static final class GroupBuffer implements AutoCloseable {
		private static final int VERTEX_BYTES = DefaultVertexFormat.NEW_ENTITY.getVertexSize();
		/** What the shared builder holds before it first has to grow: a machine's larger groups. */
		private static final int BUILDER_START_VERTICES = 16384;
		@Nullable
		private static BufferBuilder BUILDER;

		@Nullable
		private VertexBuffer buffer;
		/** The group this was built from: a different one (another model, a reload) makes the buffer stale. */
		private final MeshModel.Group source;
		private final ResourceLocation texture;
		private final int light;
		/** The scale along the model's axes baked into the vertices, or null for none. */
		@Nullable
		private final Vector3f baked;

		private GroupBuffer(VertexBuffer buffer, MeshModel.Group source, ResourceLocation texture, int light, @Nullable Vector3f baked) {
			this.buffer = buffer;
			this.source = source;
			this.texture = texture;
			this.light = light;
			this.baked = baked;
		}

		/**
		 * The group's triangles in a buffer of their own.  {@code baked} is a scale along the model's axes that is not the
		 * same on all three: it goes into the positions, and its inverse into the normals, so that what is left of the pose
		 * is a rotation the lights can follow ({@link PoseLighting}).
		 */
		static GroupBuffer build(MeshModel.Group group, ResourceLocation texture, int light, @Nullable Vector3f baked) {
			int count = group.vertexCount();
			BufferBuilder builder = builder();
			// triangles as they are: no need for the quad of four that the render type would otherwise ask for
			builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);

			float[] p = group.positions;
			float[] n = group.normals;
			float[] uv = group.uvs;
			float sx = baked == null ? 1.0f : baked.x();
			float sy = baked == null ? 1.0f : baked.y();
			float sz = baked == null ? 1.0f : baked.z();
			for (int i = 0; i < count; i++) {
				float nx = n[3 * i] / sx;
				float ny = n[3 * i + 1] / sy;
				float nz = n[3 * i + 2] / sz;
				float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (length > 0.0f) {
					nx /= length;
					ny /= length;
					nz /= length;
				}

				builder.vertex(p[3 * i] * sx, p[3 * i + 1] * sy, p[3 * i + 2] * sz, 1.0f, 1.0f, 1.0f, 1.0f, uv[2 * i], uv[2 * i + 1],
						OverlayTexture.NO_OVERLAY, light, nx, ny, nz);
			}

			VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
			vertexBuffer.bind();
			vertexBuffer.upload(builder.end());
			VertexBuffer.unbind();
			return new GroupBuffer(vertexBuffer, group, texture, light, baked == null ? null : new Vector3f(baked));
		}

		/**
		 * The one builder every group is written through.  A {@code BufferBuilder}'s memory is native and 1.20.1 has no way
		 * to free it, so one per build was megabytes lost each time a machine came back into range; this one grows to the
		 * largest group once and is handed back empty by the upload.  Render thread only.
		 */
		static BufferBuilder builder() {
			if (BUILDER == null) BUILDER = new BufferBuilder(BUILDER_START_VERTICES * VERTEX_BYTES / 6);

			return BUILDER;
		}

		boolean isStale(MeshModel.Group group, ResourceLocation texture, int light, @Nullable Vector3f baked) {
			if (buffer == null || source != group || this.light != light || !this.texture.equals(texture)) return true;
			if (this.baked == null || baked == null) return this.baked != baked;

			return !PoseLighting.sameScale(this.baked, baked);
		}

		void draw(Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
			if (buffer == null) return;

			buffer.bind();
			buffer.drawWithShader(modelView, projection, shader);
			VertexBuffer.unbind();
		}

		@Override
		public void close() {
			if (buffer != null) buffer.close();
			buffer = null;
		}
	}

	/**
	 * What a renderer does with one machine, given the model and the block's pose already on the stack: work out the
	 * pose of each group and hand them to one of the {@code renderGrouped}s with the same context.
	 */
	@FunctionalInterface
	public interface Draw<T extends BlockEntity> {
		void draw(T machine, RenderContext context, PoseStack poseStack);
	}

	/** What {@code drawAll} returns on a stage it does not draw in: "nothing was decided", unlike an empty set. */
	private static final Set<BlockPos> SKIPPED = Collections.unmodifiableSet(new HashSet<>());
	/** The buffers of every machine type that has been drawn: per position, per group. */
	private static final Map<Class<?>, Map<BlockPos, Map<String, GroupBuffer>>> CACHES = new HashMap<>();
	private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
	/** The squared range each type's pass draws to, as the pass last said. */
	private static final Map<Class<?>, Double> RANGES = new HashMap<>();
	private static final Set<String> REPORTED = new HashSet<>();
	/** The lights of the pass under way: drawing happens on the render thread only, one pass at a time. */
	private static final PoseLighting LIGHTING = new PoseLighting();
	/** How much past the model's own reach the culling box goes; see {@link #aroundModel}. */
	private static final double CULL_MARGIN = 1.5;
	/** Scratch for {@link PoseLighting#axisScale}; a buffer that keeps it copies it. */
	private static final Vector3f BAKED = new Vector3f();
	@Nullable
	private static Level lastLevel;

	// ---- the pass ----

	/**
	 * Draws every tracked machine of a type with just the block's pose.  Returns the positions in range, drawn or only
	 * off screen: what a renderer keeps its animation state for.
	 *
	 * One renderer draws a type, once a frame: the buffers of the type's positions that a pass did not return are
	 * released at its end.
	 */
	protected static <T extends BlockEntity> Set<BlockPos> drawAll(RenderLevelStageEvent event, Class<T> type, double maxDistanceSq,
			@Nullable Function<BlockState, Direction> facing, @Nullable Direction authored) {
		return drawAll(event, type, maxDistanceSq, facing, authored, null, null);
	}

	/** As above, but the renderer decides what to draw and how each group is posed. */
	protected static <T extends BlockEntity> Set<BlockPos> drawAll(RenderLevelStageEvent event, Class<T> type, double maxDistanceSq,
			@Nullable Function<BlockState, Direction> facing, @Nullable Direction authored, @Nullable Draw<T> draw) {
		return drawAll(event, type, maxDistanceSq, facing, authored, draw, null);
	}

	/** As above, for only those machines of the type that pass {@code filter}. */
	protected static <T extends BlockEntity> Set<BlockPos> drawAll(RenderLevelStageEvent event, Class<T> type, double maxDistanceSq,
			@Nullable Function<BlockState, Direction> facing, @Nullable Direction authored, @Nullable Draw<T> draw,
			@Nullable Predicate<T> filter) {
		return drawAll(event, type, maxDistanceSq, facing, authored, draw, filter, null);
	}

	/**
	 * As above, with the box a machine can be drawn anywhere inside, for a renderer whose model is not drawn about the
	 * block (the turbine, from the foot of its tower); null for the box round the model's {@link MeshModel#radius}.
	 */
	protected static <T extends BlockEntity> Set<BlockPos> drawAll(RenderLevelStageEvent event, Class<T> type, double maxDistanceSq,
			@Nullable Function<BlockState, Direction> facing, @Nullable Direction authored, @Nullable Draw<T> draw,
			@Nullable Predicate<T> filter, @Nullable BiFunction<T, MeshModel, AABB> bounds) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return SKIPPED;

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) return SKIPPED;

		RANGES.put(type, maxDistanceSq);
		FrameClock.begin(event.getRenderTick(), event.getPartialTick(), minecraft.isPaused());
		watchLevel(level);
		Map<BlockPos, Map<String, GroupBuffer>> cache = CACHES.computeIfAbsent(type, key -> new HashMap<>());

		Vec3 camera = event.getCamera().getPosition();
		PoseStack poseStack = event.getPoseStack();
		Matrix4f projection = event.getProjectionMatrix();
		Frustum frustum = event.getFrustum();
		Set<BlockPos> seen = new HashSet<>();
		// nothing is pushed yet, so the stage's pose is the camera's rotation: the frame the level's lights are set in
		LIGHTING.forView(poseStack.last().pose(), level.effects().constantAmbientLight());

		for (T machine : ClientBlockEntities.of(type)) {
			if (machine.isRemoved() || machine.getLevel() != level) continue;

			BlockPos pos = machine.getBlockPos();
			double dx = pos.getX() + 0.5 - camera.x;
			double dy = pos.getY() + 0.5 - camera.y;
			double dz = pos.getZ() + 0.5 - camera.z;
			if (dx * dx + dy * dy + dz * dz > maxDistanceSq) continue;
			// after the range: a filter may walk the world (a bare tower counts its column), and not for every machine loaded
			if (filter != null && !filter.test(machine)) continue;

			BlockState state = machine.getBlockState();
			MeshModel model = ModelBindings.model(state.getBlock());
			if (model == null) continue;

			// in range, so its buffers and its angles are kept whether it is off screen this frame or its drawing throws
			seen.add(pos);
			if (frustum != null && !frustum.isVisible(bounds == null ? aroundModel(machine, model) : bounds.apply(machine, model))) continue;

			int light = lightAt(level, pos);
			Map<String, GroupBuffer> buffers = cache.computeIfAbsent(pos.immutable(), key -> new HashMap<>());
			RenderContext context = new RenderContext(model, ModelBindings.fallbackTexture(state.getBlock()), light, projection, buffers,
					LIGHTING);

			poseStack.pushPose();
			try {
				poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
				poseStack.translate(0.5, 0.0, 0.5);
				if (facing != null && authored != null) {
					poseStack.mulPose(Axis.YP.rotationDegrees(ModelFacing.degrees(authored, facing.apply(state))));
				}

				if (machine instanceof ExtraPose pose) applyPose(poseStack, pose);

				if (draw == null) {
					renderGrouped(context, poseStack, name -> true);
				} else {
					draw.draw(machine, context, poseStack);
				}
			} catch (RuntimeException e) {
				// one machine's drawing going wrong must cost that machine, not the frame
				reportOnce(machine.getClass().getName(), "A " + machine.getClass().getSimpleName() + " could not be drawn: " + e);
			} finally {
				poseStack.popPose();
			}
		}

		LIGHTING.restore();
		release(cache, seen);
		return seen;
	}

	/**
	 * The light a machine is drawn with: the brighter of its own cell and the one above, per channel, which is what a
	 * wire's end takes too ({@code WireRenderer.sampleLight}).  A machine whose own cell is shaded by its own shell or
	 * shape would otherwise be drawn a step darker than the wire hanging from it and the ground it stands on.
	 */
	static int lightAt(Level level, BlockPos pos) {
		int here = LevelRenderer.getLightColor(level, pos);
		int above = LevelRenderer.getLightColor(level, pos.above());
		int block = Math.max((here >> 4) & 15, (above >> 4) & 15);
		int sky = Math.max((here >> 20) & 15, (above >> 20) & 15);
		return (block << 4) | (sky << 20);
	}

	/**
	 * Whether a machine is near enough to the camera for its renderer to draw it, by the renderer's own range; true for
	 * one no renderer has drawn yet.  A wire asks it of both its ends, so it is never drawn hanging from nothing.
	 */
	public static boolean inRange(BlockEntity machine, Vec3 camera) {
		Double range = RANGES.get(machine.getClass());
		if (range == null) return true;

		BlockPos pos = machine.getBlockPos();
		double dx = pos.getX() + 0.5 - camera.x;
		double dy = pos.getY() + 0.5 - camera.y;
		double dz = pos.getZ() + 0.5 - camera.z;
		return dx * dx + dy * dy + dz * dz <= range;
	}

	/**
	 * A box the machine is drawn inside, whatever its facing and pose: a cube round the model's origin, half as large
	 * again as the model's reach, because a part turning about a hinge away from the origin (a tracker's table, a
	 * switch's blade) can swing a little past it.
	 */
	private static AABB aroundModel(BlockEntity machine, MeshModel model) {
		double reach = model.radius() * CULL_MARGIN + 0.5;
		if (machine instanceof ExtraPose pose) reach += pose.poseOffset().length();

		BlockPos pos = machine.getBlockPos();
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		return new AABB(x - reach, pos.getY() - reach, z - reach, x + reach, pos.getY() + reach, z + reach);
	}

	/** Offset, then yaw about the vertical, then pitch about the east-west axis: the order everything else uses. */
	private static void applyPose(PoseStack poseStack, ExtraPose pose) {
		Vec3 offset = pose.poseOffset();
		poseStack.translate(offset.x, offset.y, offset.z);
		if (pose.poseYawDegrees() != 0.0f) poseStack.mulPose(Axis.YP.rotationDegrees(pose.poseYawDegrees()));
		if (pose.posePitchDegrees() != 0.0f) poseStack.mulPose(Axis.XP.rotationDegrees(pose.posePitchDegrees()));
	}

	// ---- drawing groups ----

	/** One group ready to go: its buffer and the matrix to draw it with. */
	private record Batch(GroupBuffer buffer, Matrix4f pose) {
	}

	/** Draws the machine's groups, each with the matrix in {@code poses}; a group with no matrix is not drawn. */
	protected static void renderGrouped(RenderContext context, Map<String, Matrix4f> poses) {
		if (poses.isEmpty()) return;

		Map<ResourceLocation, List<Batch>> batches = new LinkedHashMap<>();
		for (Map.Entry<String, Matrix4f> entry : poses.entrySet()) {
			collect(batches, context, context.model().groups.get(entry.getKey()), entry.getValue());
		}

		drawBatches(batches, context);
	}

	/** Draws every group of the machine with the one pose on the stack, except those {@code visible} rejects. */
	protected static void renderGrouped(RenderContext context, PoseStack poseStack, Predicate<String> visible) {
		Matrix4f pose = poseStack.last().pose();
		Map<ResourceLocation, List<Batch>> batches = new LinkedHashMap<>();
		for (MeshModel.Group group : context.model().groups.values()) {
			if (visible.test(group.name)) collect(batches, context, group, pose);
		}

		drawBatches(batches, context);
	}

	private static void collect(Map<ResourceLocation, List<Batch>> batches, RenderContext context, @Nullable MeshModel.Group group,
			Matrix4f pose) {
		if (group == null || group.isMarker() || group.vertexCount() == 0) return;

		ResourceLocation texture = textureOf(group, context.texture());
		int light = context.packedLight();
		// a scale that differs along the model's axes goes into the vertices, so the pose left is one the lights follow
		Vector3f baked = PoseLighting.axisScale(pose, BAKED) ? BAKED : null;
		GroupBuffer buffer = context.buffers.get(group.name);
		if (buffer == null || buffer.isStale(group, texture, light, baked)) {
			if (buffer != null) buffer.close();
			buffer = GroupBuffer.build(group, texture, light, baked);
			context.buffers.put(group.name, buffer);
		}

		Matrix4f drawn = buffer.baked == null ? pose : new Matrix4f(pose).scale(1.0f / buffer.baked.x(), 1.0f / buffer.baked.y(), 1.0f / buffer.baked.z());
		batches.computeIfAbsent(texture, key -> new ArrayList<>()).add(new Batch(buffer, drawn));
	}

	/**
	 * One render state per texture, however many groups share it; the lights are turned into each group's frame.
	 *
	 * The entity shader fogs by {@code IViewRotMat * Position}, which vanilla's vertices need because they are already in
	 * the camera's frame.  These buffers are in the model's, with the whole transform in the model-view matrix, so the
	 * rotation is identity while they draw: otherwise a machine is fogged at the wrong distance, and differently as the
	 * player turns their head.
	 */
	private static void drawBatches(Map<ResourceLocation, List<Batch>> batches, RenderContext context) {
		Matrix3f viewRotation = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
		RenderSystem.setInverseViewRotationMatrix(new Matrix3f());
		try {
			for (Map.Entry<ResourceLocation, List<Batch>> entry : batches.entrySet()) {
				RenderType type = RenderType.entityCutoutNoCull(entry.getKey());
				type.setupRenderState();
				try {
					ShaderInstance shader = RenderSystem.getShader();
					if (shader == null) continue;

					for (Batch batch : entry.getValue()) {
						context.lighting.applyTo(batch.pose());
						batch.buffer().draw(batch.pose(), context.projection, shader);
					}
				} finally {
					type.clearRenderState();
				}
			}
		} finally {
			RenderSystem.setInverseViewRotationMatrix(viewRotation);
		}
	}

	/** The picture a group is drawn with: its material's texture, or the block's fallback.  Never null. */
	private static ResourceLocation textureOf(MeshModel.Group group, ResourceLocation fallback) {
		String file = group.textureFile;
		if (file == null) return fallback;

		ResourceLocation texture = TEXTURES.computeIfAbsent(file,
				name -> ResourceLocation.tryParse(Renewables.MOD_ID + ":textures/block/" + name));
		if (texture != null) return texture;

		reportOnce("texture:" + file, "Texture file \"" + file + "\" is not a valid resource name; the block's fallback is used");
		return fallback;
	}

	// ---- the cache ----

	/** Closes the buffers of every position that was not drawn this pass. */
	private static void release(Map<BlockPos, Map<String, GroupBuffer>> cache, Set<BlockPos> seen) {
		Iterator<Map.Entry<BlockPos, Map<String, GroupBuffer>>> positions = cache.entrySet().iterator();
		while (positions.hasNext()) {
			Map.Entry<BlockPos, Map<String, GroupBuffer>> entry = positions.next();
			if (seen.contains(entry.getKey())) continue;

			for (GroupBuffer buffer : entry.getValue().values()) buffer.close();
			positions.remove();
		}
	}

	/** Closes every buffer of every machine type: the resources reloaded, or the world went away. */
	public static void releaseAll() {
		for (Map<BlockPos, Map<String, GroupBuffer>> cache : CACHES.values()) {
			for (Map<String, GroupBuffer> buffers : cache.values()) {
				for (GroupBuffer buffer : buffers.values()) buffer.close();
			}
		}

		CACHES.clear();
		TEXTURES.clear();
		REPORTED.clear();
		FrameClock.reset();
		lastLevel = null;
	}

	/** A cache is keyed by position only, so a different level means everything in it is someone else's. */
	private static void watchLevel(Level level) {
		if (lastLevel != null && lastLevel != level) releaseAll();
		lastLevel = level;
	}

	private static void reportOnce(String key, String message) {
		if (REPORTED.add(key)) Renewables.LOGGER.error(message);
	}

	// ---- time ----

	/** A per-frame step as it was tuned (at 60 frames a second), scaled to the frame that is being drawn. */
	protected static float frameScale() {
		return FrameClock.perSixty();
	}

	/** Seconds the frame took; what a rate in units a second is multiplied by. */
	protected static float frameSeconds() {
		return FrameClock.seconds();
	}

	// ---- helpers for the renderers ----

	/** The centre of the groups named {@code pivot_<name>*}, or {@code fallback} when the model has none. */
	protected static Vec3 pivot(MeshModel model, String name, Vec3 fallback) {
		return centerOf(model, "pivot_" + name, fallback);
	}

	/** The centre of the box round every group whose name starts with {@code prefix}, or {@code fallback}. */
	protected static Vec3 centerOf(MeshModel model, String prefix, Vec3 fallback) {
		Vector3f center = model.centerOf(prefix);
		return center == null ? fallback : new Vec3(center.x(), center.y(), center.z());
	}

	/** Forgets the interpolated angles of positions that were not drawn this pass. */
	protected static void cleanupAngles(Map<BlockPos, Float> angles, Set<BlockPos> seen) {
		// a pass that did not draw (another stage of the frame) decided nothing about who is still there
		if (seen == SKIPPED) return;

		angles.keySet().retainAll(seen);
	}

	/**
	 * The names {@code <prefix>_<side>} of the model's cable-entry groups that a DC cable is actually
	 * arriving at, for each of the four horizontal sides (the side is read in the model's frame).
	 */
	protected static Set<String> cableEntries(Level level, BlockPos pos, Direction authored, Direction facing, String prefix) {
		Set<String> live = new HashSet<>();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (cableArrives(level, pos, direction)) live.add(prefix + "_" + ModelFacing.side(direction, authored, facing).getName());
		}

		return live;
	}

	/** Whether a group is shown: a {@code <prefix>_<direction>} group only when its entry is live, any other always. */
	protected static boolean entryVisible(String groupName, String prefix, Set<String> live) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (groupName.startsWith(prefix + "_" + direction.getName())) return live.contains(prefix + "_" + direction.getName());
		}

		return true;
	}

	/** Whether a DC cable block next to, above or below {@code pos} on that side reaches this position. */
	protected static boolean cableArrives(Level level, BlockPos pos, Direction side) {
		BlockPos beside = pos.relative(side);
		// the run may be level with the machine, or climb up to it, or step down to it
		for (BlockPos candidate : new BlockPos[] {beside, beside.above(), beside.below()}) {
			BlockState state = level.getBlockState(candidate);
			if (state.getBlock() instanceof DcCableBlock cable && cable.reaches(state, level, candidate, pos)) return true;
		}

		return false;
	}
}
