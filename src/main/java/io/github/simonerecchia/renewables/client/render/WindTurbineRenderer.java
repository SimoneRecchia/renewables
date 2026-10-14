package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.block.MachineBlock;
import io.github.simonerecchia.renewables.block.ModelFacing;
import io.github.simonerecchia.renewables.block.WindTurbineBlock;
import io.github.simonerecchia.renewables.block.WindTurbineBlockEntity;
import io.github.simonerecchia.renewables.client.model.MeshModel;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.model.ExtraPose;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the wind turbines: the tower, the nacelle, the hub and the rotor, from the one shared model scaled by the
 * turbine's own specification (docs/spec/wind.md section 14, models.md section 8.3).
 *
 * The model is the whole structure from the foot of the tower up, so it is drawn {@code towerHeight} blocks below the
 * machine block, which is the block on top of the tower.  Each part gets a matrix of its own, by the group's name
 * prefix:
 * <ul>
 * <li>{@code pole_base*}: the door, flange and nuts at the foot, scaled uniformly and never stretched;</li>
 * <li>{@code pole*}: the tower, stretched to the height of the tower and as wide as the turbine's scale;</li>
 * <li>{@code insulator*}: the wire fitting at the foot, as authored.  Neither of these two follows the wind, so
 *     the fitting stays where the picker and the wire anchor are;</li>
 * <li>{@code rotate_1*}: the blades, about the hub, at the rotor's scale;</li>
 * <li>{@code rotate_2*}: the hub's cone, about the hub, at the nacelle's scale;</li>
 * <li>anything else is the nacelle, brought down to sit on the top of the tower.  These three turn about the axis of
 *     the tower to face the wind.</li>
 * </ul>
 *
 * The block entity keeps its yaw in the wind's own frame (the direction the air goes); the model is turned for the
 * drawing only, so that the rotor faces into the wind.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class WindTurbineRenderer extends MachineRenderer {
	private static final double MAX_RENDER_DISTANCE_SQ = 128 * 128;
	private static final Map<BlockPos, Map<String, GroupBuffer>> BUFFER_CACHE = new HashMap<>();
	/** Where the nacelle has got to in the model's own yaw, per turbine, so it follows the wind smoothly. */
	private static final Map<BlockPos, Float> YAW = new HashMap<>();

	/** Where the hub's cone is if the model has none to measure. */
	private static final Vec3 HUB_FALLBACK = new Vec3(0.0, 13.30, 1.04);

	/** The yaw follower, tuned at 60 frames a second: the fraction of the gap closed a frame, and the most a frame turns. */
	private static final float YAW_FOLLOW = 0.1f;
	private static final float YAW_MAX_STEP = 1.5f;
	/** A gap this small is closed at once, so the follower does not creep for ever. */
	private static final float YAW_SNAP = 0.25f;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		cleanupAngles(YAW, drawAll(event, WindTurbineBlockEntity.class, MAX_RENDER_DISTANCE_SQ,
				state -> state.getValue(MachineBlock.FACING), WindTurbineBlock.AUTHORED, BUFFER_CACHE, WindTurbineRenderer::render));
	}

	private static void render(WindTurbineBlockEntity turbine, RenderContext context, PoseStack poseStack, Matrix4f projection) {
		MeshModel model = context.model();
		TurbineSpec spec = turbine.spec();
		BlockPos pos = turbine.getBlockPos();
		Direction facing = turbine.getBlockState().getValue(MachineBlock.FACING);
		float partialTick = Minecraft.getInstance().getFrameTime();

		int segments = Math.max(0, turbine.towerHeight());
		float nacelleScale = (float) spec.nacelleRenderScale();
		float rotorScale = (float) spec.renderScale();

		Vec3 hubCentre = centerOf(model, "rotate_2", HUB_FALLBACK);
		float nacelleBottom = nacelleBottom(model, (float) hubCentre.y);
		float authoredHeight = authoredTowerHeight(model);
		// the hub rides on the nacelle: the same height above its underside, scaled with it
		Vec3 hub = new Vec3(hubCentre.x * nacelleScale, segments + nacelleScale * (hubCentre.y - nacelleBottom), hubCentre.z * nacelleScale);

		float yaw = smoothYaw(pos, modelYaw(turbine.yawDegrees(partialTick)));
		float relativeYaw = Mth.wrapDegrees(yaw - ModelFacing.degrees(WindTurbineBlock.AUTHORED, facing));
		// the real turbines turn clockwise seen from the front; a positive turn about +z is the other way
		float rotor = -turbine.rotorAngleDegrees(partialTick);

		poseStack.pushPose();
		// the model starts at the foot of the tower; if the block entity does not already say so, lower it
		if (!(turbine instanceof ExtraPose)) poseStack.translate(0.0, -segments, 0.0);

		Map<String, Matrix4f> poses = new HashMap<>();
		for (String groupName : model.groups.keySet()) {
			if (groupName.startsWith("pivot_")) continue;

			poseStack.pushPose();
			if (groupName.startsWith("pole")) {
				// the tower and the fitting at its foot never follow the wind: the tube is the same all the way
				// round, and the fitting is what the picker and the wire anchor stand on
				if (segments <= 0) {
					poseStack.popPose();
					continue;
				}

				if (groupName.startsWith("pole_base")) {
					// the base (door, flange, nuts) is a thing of fixed size: stretching it with the tube would squash the door
					poseStack.scale(nacelleScale, nacelleScale, nacelleScale);
				} else {
					poseStack.scale(nacelleScale, segments / authoredHeight, nacelleScale);
				}
			} else if (groupName.startsWith("insulator")) {
				// stays at the foot, as it was authored
			} else {
				// everything that is up at the machine turns about the axis of the tower to face into the wind
				if (relativeYaw != 0.0f) poseStack.mulPose(Axis.YP.rotationDegrees(relativeYaw));

				if (groupName.startsWith("rotate_1")) {
					spin(poseStack, hub, hubCentre, rotorScale, rotor);
				} else if (groupName.startsWith("rotate_2")) {
					spin(poseStack, hub, hubCentre, nacelleScale, rotor);
				} else {
					poseStack.translate(0.0, segments, 0.0);
					poseStack.scale(nacelleScale, nacelleScale, nacelleScale);
					poseStack.translate(0.0, -nacelleBottom, 0.0);
				}
			}

			poses.put(groupName, new Matrix4f(poseStack.last().pose()));
			poseStack.popPose();
		}

		renderGrouped(model, poses, projection, context.texture(), context.packedLight(), pos, BUFFER_CACHE);
		poseStack.popPose();
	}

	/** Moves the hub of the model to where the hub really is, scales, turns about the rotor's axis, moves back. */
	private static void spin(PoseStack poseStack, Vec3 hub, Vec3 centre, float scale, float degrees) {
		poseStack.translate(hub.x, hub.y, hub.z);
		poseStack.scale(scale, scale, scale);
		poseStack.mulPose(Axis.ZP.rotationDegrees(degrees));
		poseStack.translate(-centre.x, -centre.y, -centre.z);
	}

	/**
	 * The height of the authored tube, read from the model so that a redrawn tower needs no constant changed: the
	 * stretch is {@code segments / this}, and the nacelle has to land on the top of the tube.
	 */
	static float authoredTowerHeight(MeshModel model) {
		var box = model.union("pole");
		return box == null || box.max().y() <= 0.0f ? (float) TurbineSpec.MODEL_TOWER_HEIGHT_BLOCKS : box.max().y();
	}

	/** The underside of the nacelle in the model: what rests on the top of the tower. */
	private static float nacelleBottom(MeshModel model, float fallback) {
		var box = model.union("motor");
		return box == null ? fallback : box.min().y();
	}

	/**
	 * The turn about the vertical that makes a model authored facing +z point into the wind, for a wind that blows
	 * towards {@code windDegrees} (measured from east, towards south).
	 */
	static float modelYaw(float windDegrees) {
		return Mth.wrapDegrees(-90.0f - windDegrees);
	}

	/**
	 * Follows the target yaw with a lag, taking the short way round.  The constants are those of a frame at 60 a
	 * second; the proportional part is applied as a per-second rate so that the lag is the same at any frame rate.
	 */
	private static float smoothYaw(BlockPos pos, float target) {
		float current = YAW.getOrDefault(pos, target);
		if (Minecraft.getInstance().isPaused()) return current;

		float delta = Mth.wrapDegrees(target - current);
		float scale = frameScale();
		float next;
		if (Math.abs(delta) <= YAW_SNAP) {
			next = target;
		} else {
			float follow = 1.0f - (float) Math.pow(1.0 - YAW_FOLLOW, scale);
			float step = Mth.clamp(delta * follow, -YAW_MAX_STEP * scale, YAW_MAX_STEP * scale);
			next = Mth.wrapDegrees(current + step);
		}

		YAW.put(pos, next);
		return next;
	}

	public static void init() {
		ModelBindings.registerAll(ModBlocks.TURBINES);
		TurbineTowerRenderer.init();
	}
}
