package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.block.DcCableBlock;
import io.github.simonerecchia.renewables.block.ModelFacing;
import io.github.simonerecchia.renewables.client.ClientBlockEntities;
import io.github.simonerecchia.renewables.client.model.MeshModel;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.model.ExtraPose;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 * <li>The buffers are kept here and shared: one per (groups, light, baked scale), so a field of a thousand rows in the
 *     same daylight draws from one copy of the row and not a thousand.  The groups of a machine that share a pose and a
 *     texture are one buffer.  A buffer no pass has used for {@link #IDLE_TICKS} is closed, and all of them are when the
 *     level changes or the resources reload.  Every build goes through one shared builder.</li>
 * <li>A machine whose box is outside the frustum is not drawn.</li>
 * <li>A pass queues its machines' draws and makes them at its end, a texture at a time: one render state and one shader
 *     set-up per texture for the whole type, and per draw only the pose and the two lights, turned into the group's
 *     frame ({@link PoseLighting}).  Setting the whole state up for every group of every machine was what made a field
 *     of trackers cost more than the rest of the frame.</li>
 * <li>A group that is given no matrix is not drawn, which is how a renderer hides a part.  Markers
 *     ({@code pivot_*}) are never drawn.</li>
 * <li>Light is sampled once, at the machine's own block and the one above it ({@link #lightAt}).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public abstract class MachineRenderer {
	/** Some groups' vertex buffer on the GPU, shared by every machine drawn with them at the same light and scale. */
	static final class GroupBuffer implements AutoCloseable {
		private static final int VERTEX_BYTES = DefaultVertexFormat.NEW_ENTITY.getVertexSize();
		/** What the shared builder holds before it first has to grow: a machine's larger groups. */
		private static final int BUILDER_START_VERTICES = 16384;
		@Nullable
		private static BufferBuilder BUILDER;

		@Nullable
		private VertexBuffer buffer;
		/** The render tick a pass last drew it in: what {@link #evict} goes by. */
		private int lastUsed;

		private GroupBuffer(VertexBuffer buffer) {
			this.buffer = buffer;
		}

		/**
		 * The groups' triangles, one after another, in a buffer of their own.  {@code baked} is a scale along the model's
		 * axes that is not the same on all three: it goes into the positions, and its inverse into the normals, so that what
		 * is left of the pose is a rotation the lights can follow ({@link PoseLighting}).
		 */
		static GroupBuffer build(List<MeshModel.Group> groups, int light, @Nullable Vector3f baked) {
			BufferBuilder builder = builder();
			// triangles as they are: no need for the quad of four that the render type would otherwise ask for
			builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);

			float sx = baked == null ? 1.0f : baked.x();
			float sy = baked == null ? 1.0f : baked.y();
			float sz = baked == null ? 1.0f : baked.z();
			for (MeshModel.Group group : groups) {
				int count = group.vertexCount();
				float[] p = group.positions;
				float[] n = group.normals;
				float[] uv = group.uvs;
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
			}

			VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
			vertexBuffer.bind();
			vertexBuffer.upload(builder.end());
			VertexBuffer.unbind();
			return new GroupBuffer(vertexBuffer);
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

		/** Binds it for the draws that follow; the shader is already applied. */
		void bind() {
			if (buffer != null) buffer.bind();
		}

		/** Draws it with the shader and the uniforms as they stand: {@link #flush} sets them. */
		void draw() {
			if (buffer != null) buffer.draw();
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

	/** What a buffer holds: the groups, in name order, at one light and one baked scale (null for none). */
	private record BufferKey(List<MeshModel.Group> groups, int light, @Nullable Vector3f baked) {
	}

	/** One draw the pass has queued: the shared buffer, the matrix it is drawn with and the lights in that frame. */
	private record Batch(GroupBuffer buffer, Matrix4f pose, Vector3f light0, Vector3f light1) {
	}

	/** Every buffer any pass draws from, shared between machines; see {@link #evict}. */
	private static final Map<BufferKey, GroupBuffer> BUFFERS = new HashMap<>();
	/** The pass under way's draws, by texture; emptied by {@link #flush}. */
	private static final Map<ResourceLocation, List<Batch>> QUEUE = new LinkedHashMap<>();
	/** A buffer no pass has drawn for this many render ticks (ten seconds) is closed. */
	private static final int IDLE_TICKS = 200;
	private static final Comparator<MeshModel.Group> BY_NAME = Comparator.comparing(group -> group.name);
	private static final Comparator<Batch> BY_BUFFER = Comparator.comparingInt(batch -> System.identityHashCode(batch.buffer()));
	/** A baked scale is kept to this step, so a camera's turn cannot make a new key every frame out of rounding. */
	private static final float BAKED_STEP = 1.0e4f;
	/** The render tick of the pass under way, and of the last sweep for idle buffers. */
	private static int tick;
	private static int lastSweep;
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
		tick = event.getRenderTick();

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
			RenderContext context = new RenderContext(model, ModelBindings.fallbackTexture(state.getBlock()), light, LIGHTING);

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

		flush(projection);
		LIGHTING.restore();
		evict();
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

	/**
	 * Queues the machine's groups, each with the matrix in {@code poses}; a group with no matrix is not drawn.  The groups
	 * that share a matrix and a texture go into one buffer.
	 */
	protected static void renderGrouped(RenderContext context, Map<String, Matrix4f> poses) {
		if (poses.isEmpty()) return;

		Map<Matrix4f, Map<ResourceLocation, List<MeshModel.Group>>> shared = new LinkedHashMap<>();
		for (Map.Entry<String, Matrix4f> entry : poses.entrySet()) {
			MeshModel.Group group = context.model().groups.get(entry.getKey());
			if (!drawable(group)) continue;

			shared.computeIfAbsent(entry.getValue(), key -> new LinkedHashMap<>())
					.computeIfAbsent(textureOf(group, context.texture()), key -> new ArrayList<>()).add(group);
		}

		for (Map.Entry<Matrix4f, Map<ResourceLocation, List<MeshModel.Group>>> pose : shared.entrySet()) {
			for (Map.Entry<ResourceLocation, List<MeshModel.Group>> texture : pose.getValue().entrySet()) {
				queue(context, texture.getKey(), texture.getValue(), pose.getKey());
			}
		}
	}

	/** Queues every group of the machine with the one pose on the stack, except those {@code visible} rejects. */
	protected static void renderGrouped(RenderContext context, PoseStack poseStack, Predicate<String> visible) {
		Matrix4f pose = new Matrix4f(poseStack.last().pose());
		Map<ResourceLocation, List<MeshModel.Group>> byTexture = new LinkedHashMap<>();
		for (MeshModel.Group group : context.model().groups.values()) {
			if (drawable(group) && visible.test(group.name)) {
				byTexture.computeIfAbsent(textureOf(group, context.texture()), key -> new ArrayList<>()).add(group);
			}
		}

		for (Map.Entry<ResourceLocation, List<MeshModel.Group>> texture : byTexture.entrySet()) {
			queue(context, texture.getKey(), texture.getValue(), pose);
		}
	}

	private static boolean drawable(@Nullable MeshModel.Group group) {
		return group != null && !group.isMarker() && group.vertexCount() > 0;
	}

	/** One draw for the pass to make: the shared buffer of these groups at this light, built the first time it is wanted. */
	private static void queue(RenderContext context, ResourceLocation texture, List<MeshModel.Group> groups, Matrix4f pose) {
		groups.sort(BY_NAME);
		// a scale that differs along the model's axes goes into the vertices, so the pose left is one the lights follow
		Vector3f baked = PoseLighting.axisScale(pose, BAKED) ? quantised(BAKED) : null;
		BufferKey key = new BufferKey(List.copyOf(groups), context.packedLight(), baked);
		GroupBuffer buffer = BUFFERS.get(key);
		if (buffer == null) {
			buffer = GroupBuffer.build(key.groups(), key.light(), baked);
			BUFFERS.put(key, buffer);
		}
		buffer.lastUsed = tick;

		Matrix4f drawn = baked == null ? new Matrix4f(pose) : new Matrix4f(pose).scale(1.0f / baked.x(), 1.0f / baked.y(), 1.0f / baked.z());
		Vector3f light0 = new Vector3f();
		Vector3f light1 = new Vector3f();
		context.lighting.inModelFrame(drawn, light0, light1);
		QUEUE.computeIfAbsent(texture, key2 -> new ArrayList<>()).add(new Batch(buffer, drawn, light0, light1));
	}

	private static Vector3f quantised(Vector3f scale) {
		return new Vector3f(Math.round(scale.x() * BAKED_STEP) / BAKED_STEP, Math.round(scale.y() * BAKED_STEP) / BAKED_STEP,
				Math.round(scale.z() * BAKED_STEP) / BAKED_STEP);
	}

	/**
	 * Makes the pass's queued draws: per texture, the render state and the shader once, then per draw the pose and the two
	 * lights - the uniforms {@code VertexBuffer.drawWithShader} would otherwise set up and tear down around every draw.
	 * Draws from one buffer follow one another, so it is bound once.
	 *
	 * The entity shader fogs by {@code IViewRotMat * Position}, which vanilla's vertices need because they are already in
	 * the camera's frame.  These buffers are in the model's, with the whole transform in the model-view matrix, so the
	 * rotation is identity while they draw: otherwise a machine is fogged at the wrong distance, and differently as the
	 * player turns their head.
	 */
	private static void flush(Matrix4f projection) {
		if (QUEUE.isEmpty()) return;

		Matrix3f viewRotation = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
		RenderSystem.setInverseViewRotationMatrix(new Matrix3f());
		try {
			for (Map.Entry<ResourceLocation, List<Batch>> entry : QUEUE.entrySet()) {
				List<Batch> batches = entry.getValue();
				if (batches.isEmpty()) continue;

				batches.sort(BY_BUFFER);
				RenderType type = RenderType.entityCutoutNoCull(entry.getKey());
				type.setupRenderState();
				try {
					ShaderInstance shader = RenderSystem.getShader();
					if (shader == null) continue;

					prepare(shader, projection);
					GroupBuffer bound = null;
					for (int i = 0; i < batches.size(); i++) {
						Batch batch = batches.get(i);
						setPose(shader, batch);
						// the first draw's uniforms go up with the rest of the shader's; after that only these three change
						if (i == 0) {
							shader.apply();
						} else {
							uploadPose(shader);
						}
						if (batch.buffer() != bound) {
							batch.buffer().bind();
							bound = batch.buffer();
						}
						batch.buffer().draw();
					}
					shader.clear();
				} finally {
					VertexBuffer.unbind();
					type.clearRenderState();
				}
			}
		} finally {
			RenderSystem.setInverseViewRotationMatrix(viewRotation);
			QUEUE.clear();
		}
	}

	/** What {@code VertexBuffer.drawWithShader} sets before it applies the shader, apart from the pose and the lights. */
	private static void prepare(ShaderInstance shader, Matrix4f projection) {
		for (int i = 0; i < 12; i++) shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
		if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(projection);
		if (shader.INVERSE_VIEW_ROTATION_MATRIX != null) shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
		if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
		if (shader.GLINT_ALPHA != null) shader.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
		if (shader.FOG_START != null) shader.FOG_START.set(RenderSystem.getShaderFogStart());
		if (shader.FOG_END != null) shader.FOG_END.set(RenderSystem.getShaderFogEnd());
		if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
		if (shader.FOG_SHAPE != null) shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
		if (shader.TEXTURE_MATRIX != null) shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
		if (shader.GAME_TIME != null) shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
		if (shader.SCREEN_SIZE != null) {
			Window window = Minecraft.getInstance().getWindow();
			shader.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
		}
	}

	private static void setPose(ShaderInstance shader, Batch batch) {
		if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(batch.pose());
		if (shader.LIGHT0_DIRECTION != null) shader.LIGHT0_DIRECTION.set(batch.light0());
		if (shader.LIGHT1_DIRECTION != null) shader.LIGHT1_DIRECTION.set(batch.light1());
	}

	private static void uploadPose(ShaderInstance shader) {
		if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.upload();
		if (shader.LIGHT0_DIRECTION != null) shader.LIGHT0_DIRECTION.upload();
		if (shader.LIGHT1_DIRECTION != null) shader.LIGHT1_DIRECTION.upload();
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

	/** Once a second, closes the buffers no pass has drawn from for {@link #IDLE_TICKS}: what left the range, or the light. */
	private static void evict() {
		if (Math.abs(tick - lastSweep) < 20) return;

		lastSweep = tick;
		Iterator<GroupBuffer> buffers = BUFFERS.values().iterator();
		while (buffers.hasNext()) {
			GroupBuffer buffer = buffers.next();
			if (Math.abs(tick - buffer.lastUsed) <= IDLE_TICKS) continue;

			buffer.close();
			buffers.remove();
		}
	}

	/** Closes every buffer: the resources reloaded, or the world went away. */
	public static void releaseAll() {
		for (GroupBuffer buffer : BUFFERS.values()) buffer.close();

		BUFFERS.clear();
		QUEUE.clear();
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
		forget(angles, seen);
	}

	/** Forgets what a renderer keeps per position for the positions that were not drawn this pass. */
	protected static void forget(Map<BlockPos, ?> kept, Set<BlockPos> seen) {
		// a pass that did not draw (another stage of the frame) decided nothing about who is still there
		if (seen == SKIPPED) return;

		kept.keySet().retainAll(seen);
	}

	/** The render tick of the pass under way: a clock for what a renderer reads off the world less often than every frame. */
	protected static int renderTick() {
		return tick;
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
