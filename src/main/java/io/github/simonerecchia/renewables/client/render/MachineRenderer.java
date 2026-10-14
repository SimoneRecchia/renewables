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
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The shared pass that draws every machine whose model is an OBJ (docs/spec/models.md section 6).
 *
 * Machines are not drawn by a block entity renderer nor baked into chunks: once a frame, after the solid blocks, each
 * renderer walks the machines of its type that the client is tracking and draws their model groups from vertex
 * buffers of its own, with the "entity cutout, no cull" shader.  A renderer subclasses this only to inherit these
 * statics: it keeps its own buffer cache and subscribes its own {@code RenderLevelStageEvent}.
 *
 * <ul>
 * <li>One static buffer per (block position, group), built the first time the group is drawn and again when the
 *     machine's light or the group's texture changes.  The buffers of a position that was not drawn this pass are
 *     closed, and all of them are when the level changes or the resources reload.</li>
 * <li>Within a machine the groups are batched by texture: one render state per texture, one draw per group.</li>
 * <li>A group that is given no matrix is not drawn, which is how a renderer hides a part.  Markers
 *     ({@code pivot_*}) are never drawn.</li>
 * <li>Light is sampled once, at the machine's own block.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public abstract class MachineRenderer {
	/** A group's vertex buffer on the GPU, kept per (block position, group name). */
	public static final class GroupBuffer implements AutoCloseable {
		private static final int VERTEX_BYTES = DefaultVertexFormat.NEW_ENTITY.getVertexSize();

		@Nullable
		private VertexBuffer buffer;
		/** The group this was built from: a different one (another model, a reload) makes the buffer stale. */
		private final MeshModel.Group source;
		private final ResourceLocation texture;
		private final int light;

		private GroupBuffer(VertexBuffer buffer, MeshModel.Group source, ResourceLocation texture, int light) {
			this.buffer = buffer;
			this.source = source;
			this.texture = texture;
			this.light = light;
		}

		static GroupBuffer build(MeshModel.Group group, ResourceLocation texture, int light) {
			int count = group.vertexCount();
			// the builder allocates six times what it is told; a third of the bytes is three times what the group needs
			BufferBuilder builder = new BufferBuilder(count * VERTEX_BYTES / 2 + 64);
			// triangles as they are: no need for the quad of four that the render type would otherwise ask for
			builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);

			float[] p = group.positions;
			float[] n = group.normals;
			float[] uv = group.uvs;
			for (int i = 0; i < count; i++) {
				builder.vertex(p[3 * i], p[3 * i + 1], p[3 * i + 2], 1.0f, 1.0f, 1.0f, 1.0f, uv[2 * i], uv[2 * i + 1],
						OverlayTexture.NO_OVERLAY, light, n[3 * i], n[3 * i + 1], n[3 * i + 2]);
			}

			VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
			vertexBuffer.bind();
			vertexBuffer.upload(builder.end());
			VertexBuffer.unbind();
			return new GroupBuffer(vertexBuffer, group, texture, light);
		}

		boolean isStale(MeshModel.Group group, ResourceLocation texture, int light) {
			return buffer == null || source != group || this.light != light || !this.texture.equals(texture);
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

	/** What a renderer does with one machine, given the model and the block's pose already on the stack. */
	@FunctionalInterface
	public interface Draw<T extends BlockEntity> {
		void draw(T machine, RenderContext context, PoseStack poseStack, Matrix4f projection);
	}

	/** What {@code drawAll} returns on a stage it does not draw in: "nothing was decided", unlike an empty set. */
	private static final Set<BlockPos> SKIPPED = Collections.unmodifiableSet(new HashSet<>());
	/** Every renderer's cache that has been drawn into, so that all of them can be released at once. */
	private static final Set<Map<BlockPos, Map<String, GroupBuffer>>> CACHES = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
	private static final Set<String> REPORTED = new HashSet<>();
	@Nullable
	private static Level lastLevel;

	// ---- the pass ----

	/** Draws every tracked machine of a type with just the block's pose.  Returns the positions it drew. */
	protected static <T extends BlockEntity> Set<BlockPos> drawAll(RenderLevelStageEvent event, Class<T> type, double maxDistanceSq,
			@Nullable Function<BlockState, Direction> facing, @Nullable Direction authored, Map<BlockPos, Map<String, GroupBuffer>> cache) {
		return drawAll(event, type, maxDistanceSq, facing, authored, cache, null, null);
	}

	/** As above, but the renderer decides what to draw and how each group is posed. */
	protected static <T extends BlockEntity> Set<BlockPos> drawAll(RenderLevelStageEvent event, Class<T> type, double maxDistanceSq,
			@Nullable Function<BlockState, Direction> facing, @Nullable Direction authored, Map<BlockPos, Map<String, GroupBuffer>> cache,
			@Nullable Draw<T> draw) {
		return drawAll(event, type, maxDistanceSq, facing, authored, cache, draw, null);
	}

	/** As above, for only those machines of the type that pass {@code filter}. */
	protected static <T extends BlockEntity> Set<BlockPos> drawAll(RenderLevelStageEvent event, Class<T> type, double maxDistanceSq,
			@Nullable Function<BlockState, Direction> facing, @Nullable Direction authored, Map<BlockPos, Map<String, GroupBuffer>> cache,
			@Nullable Draw<T> draw, @Nullable Predicate<T> filter) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return SKIPPED;

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) return SKIPPED;

		FrameClock.begin(event.getRenderTick(), event.getPartialTick(), minecraft.isPaused());
		watchLevel(level);
		CACHES.add(cache);

		Vec3 camera = event.getCamera().getPosition();
		PoseStack poseStack = event.getPoseStack();
		Matrix4f projection = event.getProjectionMatrix();
		Set<BlockPos> seen = new HashSet<>();

		for (T machine : ClientBlockEntities.of(type)) {
			if (machine.isRemoved() || machine.getLevel() != level) continue;
			if (filter != null && !filter.test(machine)) continue;

			BlockPos pos = machine.getBlockPos();
			double dx = pos.getX() + 0.5 - camera.x;
			double dy = pos.getY() + 0.5 - camera.y;
			double dz = pos.getZ() + 0.5 - camera.z;
			if (dx * dx + dy * dy + dz * dz > maxDistanceSq) continue;

			BlockState state = machine.getBlockState();
			MeshModel model = ModelBindings.model(state.getBlock());
			if (model == null) continue;

			int light = LevelRenderer.getLightColor(level, pos);
			RenderContext context = new RenderContext(model, ModelBindings.fallbackTexture(state.getBlock()), light);

			poseStack.pushPose();
			try {
				poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
				poseStack.translate(0.5, 0.0, 0.5);
				if (facing != null && authored != null) {
					poseStack.mulPose(Axis.YP.rotationDegrees(ModelFacing.degrees(authored, facing.apply(state))));
				}

				if (machine instanceof ExtraPose pose) applyPose(poseStack, pose);

				if (draw == null) {
					renderGrouped(model, poseStack, projection, context.texture(), light, pos, cache, name -> true);
				} else {
					draw.draw(machine, context, poseStack, projection);
				}

				seen.add(pos);
			} catch (RuntimeException e) {
				// one machine's drawing going wrong must cost that machine, not the frame
				reportOnce(machine.getClass().getName(), "A " + machine.getClass().getSimpleName() + " could not be drawn: " + e);
			} finally {
				poseStack.popPose();
			}
		}

		release(cache, seen);
		return seen;
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

	/** Draws groups, each with the matrix in {@code poses}; a group with no matrix is not drawn. */
	protected static void renderGrouped(MeshModel model, Map<String, Matrix4f> poses, Matrix4f projection, ResourceLocation texture,
			int packedLight, BlockPos pos, Map<BlockPos, Map<String, GroupBuffer>> cache) {
		if (poses.isEmpty()) return;

		Map<ResourceLocation, List<Batch>> batches = new LinkedHashMap<>();
		Map<String, GroupBuffer> buffers = buffersAt(cache, pos);
		for (Map.Entry<String, Matrix4f> entry : poses.entrySet()) {
			collect(batches, buffers, model.groups.get(entry.getKey()), entry.getValue(), texture, packedLight);
		}

		drawBatches(batches, projection);
	}

	/** Draws every group with the one pose on the stack, except those {@code visible} rejects. */
	protected static void renderGrouped(MeshModel model, PoseStack poseStack, Matrix4f projection, ResourceLocation texture,
			int packedLight, BlockPos pos, Map<BlockPos, Map<String, GroupBuffer>> cache, Predicate<String> visible) {
		Matrix4f pose = poseStack.last().pose();
		Map<ResourceLocation, List<Batch>> batches = new LinkedHashMap<>();
		Map<String, GroupBuffer> buffers = buffersAt(cache, pos);
		for (MeshModel.Group group : model.groups.values()) {
			if (visible.test(group.name)) collect(batches, buffers, group, pose, texture, packedLight);
		}

		drawBatches(batches, projection);
	}

	private static Map<String, GroupBuffer> buffersAt(Map<BlockPos, Map<String, GroupBuffer>> cache, BlockPos pos) {
		CACHES.add(cache);
		return cache.computeIfAbsent(pos.immutable(), key -> new HashMap<>());
	}

	private static void collect(Map<ResourceLocation, List<Batch>> batches, Map<String, GroupBuffer> buffers, @Nullable MeshModel.Group group,
			Matrix4f pose, ResourceLocation fallback, int light) {
		if (group == null || group.isMarker() || group.vertexCount() == 0) return;

		ResourceLocation texture = textureOf(group, fallback);
		GroupBuffer buffer = buffers.get(group.name);
		if (buffer == null || buffer.isStale(group, texture, light)) {
			if (buffer != null) buffer.close();
			buffer = GroupBuffer.build(group, texture, light);
			buffers.put(group.name, buffer);
		}

		batches.computeIfAbsent(texture, key -> new ArrayList<>()).add(new Batch(buffer, pose));
	}

	/** One render state per texture, however many groups share it. */
	private static void drawBatches(Map<ResourceLocation, List<Batch>> batches, Matrix4f projection) {
		for (Map.Entry<ResourceLocation, List<Batch>> entry : batches.entrySet()) {
			RenderType type = RenderType.entityCutoutNoCull(entry.getKey());
			type.setupRenderState();
			try {
				ShaderInstance shader = RenderSystem.getShader();
				if (shader == null) continue;

				for (Batch batch : entry.getValue()) batch.buffer().draw(batch.pose(), projection, shader);
			} finally {
				type.clearRenderState();
			}
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

	/** Closes every buffer of every renderer: the resources reloaded, or the world went away. */
	public static void releaseAll() {
		for (Map<BlockPos, Map<String, GroupBuffer>> cache : CACHES) {
			for (Map<String, GroupBuffer> buffers : cache.values()) {
				for (GroupBuffer buffer : buffers.values()) buffer.close();
			}

			cache.clear();
		}

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
