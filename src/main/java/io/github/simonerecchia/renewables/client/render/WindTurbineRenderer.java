package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.Renewables;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.block.MachineBlock;
import io.github.simonerecchia.renewables.block.ModelFacing;
import io.github.simonerecchia.renewables.block.WindTurbineBlock;
import io.github.simonerecchia.renewables.block.WindTurbineBlockEntity;
import io.github.simonerecchia.renewables.client.model.GroupPoses;
import io.github.simonerecchia.renewables.client.model.MeshModel;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.model.ExtraPose;
import io.github.simonerecchia.renewables.model.PartBox;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
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
				state -> state.getValue(MachineBlock.FACING), WindTurbineBlock.AUTHORED, WindTurbineRenderer::render, null,
				WindTurbineRenderer::bounds));
	}

	/**
	 * What the turbine's groups are posed with, from its specification and its tower: the two scales, the height of the
	 * tower, and where the model's hub is and where the drawn one is.
	 */
	record Layout(float nacelleScale, float rotorScale, int segments, float authoredHeight, float nacelleBottom, Vec3 hubCentre,
			Vec3 hub) {
		static Layout of(MeshModel model, WindTurbineBlockEntity turbine) {
			TurbineSpec spec = turbine.spec();
			int segments = Math.max(0, turbine.towerHeight());
			float nacelleScale = (float) spec.nacelleRenderScale();
			Vec3 hubCentre = centerOf(model, "rotate_2", HUB_FALLBACK);
			float nacelleBottom = WindTurbineRenderer.nacelleBottom(model, (float) hubCentre.y);
			// the hub rides on the nacelle: the same height above its underside, scaled with it
			Vec3 hub = new Vec3(hubCentre.x * nacelleScale, segments + nacelleScale * (hubCentre.y - nacelleBottom), hubCentre.z * nacelleScale);
			return new Layout(nacelleScale, (float) spec.renderScale(), segments, authoredTowerHeight(model), nacelleBottom, hubCentre, hub);
		}
	}

	private static void render(WindTurbineBlockEntity turbine, RenderContext context, PoseStack poseStack) {
		MeshModel model = context.model();
		Layout layout = Layout.of(model, turbine);
		float partialTick = Minecraft.getInstance().getFrameTime();

		float yaw = smoothYaw(turbine.getBlockPos(), modelYaw(turbine.yawDegrees(partialTick)));
		float relativeYaw = relativeYaw(yaw, turbine);
		// the real turbines turn clockwise seen from the front; a positive turn about +z is the other way
		float rotor = -turbine.rotorAngleDegrees(partialTick);

		poseStack.pushPose();
		// the model starts at the foot of the tower; if the block entity does not already say so, lower it
		if (!(turbine instanceof ExtraPose)) poseStack.translate(0.0, -layout.segments(), 0.0);

		Matrix4f base = poseStack.last().pose();
		Map<String, Matrix4f> poses = new HashMap<>();
		for (String groupName : model.groups.keySet()) {
			Matrix4f pose = new Matrix4f(base);
			if (groupPose(groupName, layout, relativeYaw, rotor, true, pose)) poses.put(groupName, pose);
		}

		renderGrouped(context, poses);
		poseStack.popPose();
	}

	/**
	 * Multiplies onto {@code pose} what one group is drawn with, in the model's frame at the foot of the tower; false when
	 * the group is not drawn.  The drawing and the picker both call it, the picker with {@code withRotor} false: a rotor
	 * turning at speed is not something to aim at.
	 */
	static boolean groupPose(String groupName, Layout layout, float relativeYaw, float rotorDegrees, boolean withRotor, Matrix4f pose) {
		if (groupName.startsWith("pivot_")) return false;

		if (groupName.startsWith("pole")) {
			// the tower and the fitting at its foot never follow the wind: the tube is the same all the way round, and
			// the fitting is what the picker and the wire anchor stand on
			if (layout.segments() <= 0) return false;

			float s = layout.nacelleScale();
			// the base (door, flange, nuts) is a thing of fixed size: stretching it with the tube would squash the door
			if (groupName.startsWith("pole_base")) {
				pose.scale(s);
			} else {
				pose.scale(s, layout.segments() / layout.authoredHeight(), s);
			}
			return true;
		}

		// stays at the foot, as it was authored
		if (groupName.startsWith("insulator")) return true;

		// everything that is up at the machine turns about the axis of the tower to face into the wind
		if (relativeYaw != 0.0f) pose.rotate(Axis.YP.rotationDegrees(relativeYaw));

		if (groupName.startsWith("rotate_1")) {
			if (!withRotor) return false;
			spin(pose, layout.hub(), layout.hubCentre(), layout.rotorScale(), rotorDegrees);
		} else if (groupName.startsWith("rotate_2")) {
			spin(pose, layout.hub(), layout.hubCentre(), layout.nacelleScale(), rotorDegrees);
		} else {
			pose.translate(0.0f, layout.segments(), 0.0f);
			pose.scale(layout.nacelleScale());
			pose.translate(0.0f, -layout.nacelleBottom(), 0.0f);
		}
		return true;
	}

	/** Moves the hub of the model to where the hub really is, scales, turns about the rotor's axis, moves back. */
	private static void spin(Matrix4f pose, Vec3 hub, Vec3 centre, float scale, float degrees) {
		pose.translate((float) hub.x, (float) hub.y, (float) hub.z);
		pose.scale(scale);
		pose.rotate(Axis.ZP.rotationDegrees(degrees));
		pose.translate((float) -centre.x, (float) -centre.y, (float) -centre.z);
	}

	/** The nacelle's yaw relative to the block's facing, which the pass has already turned the model by. */
	private static float relativeYaw(float modelYaw, WindTurbineBlockEntity turbine) {
		Direction facing = turbine.getBlockState().getValue(MachineBlock.FACING);
		return Mth.wrapDegrees(modelYaw - ModelFacing.degrees(WindTurbineBlock.AUTHORED, facing));
	}

	/**
	 * The box the whole turbine is drawn in, from the foot of its tower to the top of the rotor's sweep; the default
	 * round the block would lose a tower taller than the model's own reach.
	 */
	private static AABB bounds(WindTurbineBlockEntity turbine, MeshModel model) {
		Layout layout = Layout.of(model, turbine);
		PartBox blades = model.union("rotate_1");
		double sweep = blades == null ? 6.5 : Math.max(
				Math.max(Math.abs(blades.min().x() - layout.hubCentre().x), Math.abs(blades.max().x() - layout.hubCentre().x)),
				Math.max(Math.abs(blades.min().y() - layout.hubCentre().y), Math.abs(blades.max().y() - layout.hubCentre().y)));
		// the blades' box is a box round three of them; turning, a tip reaches its diagonal
		double reach = sweep * Math.sqrt(2.0) * layout.rotorScale() + Math.abs(layout.hub().z) + 1.0;

		BlockPos pos = turbine.getBlockPos();
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		double foot = pos.getY() - layout.segments();
		return new AABB(x - reach, foot - 0.5, z - reach, x + reach, foot + layout.hub().y + reach, z + reach);
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
		// the picker sees the turbine at its own size, its tower stretched and its nacelle where it last was drawn
		GroupPoses.register(WindTurbineBlockEntity.class, (turbine, model) -> {
			Layout layout = Layout.of(model, turbine);
			float yaw = relativeYaw(YAW.getOrDefault(turbine.getBlockPos(), modelYaw(turbine.yawDegrees(1.0f))), turbine);
			return groupName -> {
				Matrix4f pose = new Matrix4f();
				return groupPose(groupName, layout, yaw, 0.0f, false, pose) ? pose : null;
			};
		});
		TurbineTowerRenderer.init();
	}
}
