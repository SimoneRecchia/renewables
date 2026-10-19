package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.client.model.GroupPoses;
import io.github.simonerecchia.renewables.client.model.MeshModel;
import io.github.simonerecchia.renewables.client.model.ModelBindings;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import io.github.simonerecchia.renewables.Renewables;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import io.github.simonerecchia.renewables.block.SwitchgearBlock;
import io.github.simonerecchia.renewables.block.SwitchgearBlockEntity;

/**
 * Draws the two switches, and swings a disconnector's blades when it is open.
 *
 * The blade turns about the hinge its own model marks with {@code pivot_blade}, which runs along x - so one
 * marker serves all three poles, because a rotation about x does not care where along x a part is.  A
 * breaker has nothing that moves: its contacts are inside a sealed bottle, and the two flag plates are what
 * it can show instead.
 */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Renewables.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SwitchgearRenderer extends MachineRenderer {
	private static final double MAX_RENDER_DISTANCE_SQ = 96 * 96;

	/** How far a blade swings, in degrees: far enough that the gap reads as a gap from anywhere. */
	private static final float OPEN_DEGREES = 84.0f;

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		drawAll(event, SwitchgearBlockEntity.class, MAX_RENDER_DISTANCE_SQ, state -> state.getValue(SwitchgearBlock.FACING),
				SwitchgearBlock.AUTHORED, SwitchgearRenderer::render);
	}

	private static void render(SwitchgearBlockEntity gear, RenderContext context, PoseStack poseStack) {
		MeshModel model = context.model();
		boolean open = SwitchgearBlock.open(gear.getBlockState());
		Vec3 hinge = hinge(model);
		Matrix4f base = poseStack.last().pose();
		Map<String, Matrix4f> poses = new HashMap<>();

		for (String groupName : model.groups.keySet()) {
			Matrix4f pose = new Matrix4f(base);
			if (groupPose(groupName, hinge, open, pose)) poses.put(groupName, pose);
		}

		renderGrouped(context, poses);
	}

	private static Vec3 hinge(MeshModel model) {
		return pivot(model, "blade", new Vec3(0.0, 0.336, 0.155));
	}

	/** Multiplies onto {@code pose} what one group is drawn with; false when it is not drawn.  The picker calls it too. */
	static boolean groupPose(String groupName, Vec3 hinge, boolean open, Matrix4f pose) {
		if (groupName.startsWith("pivot_") || !drawn(groupName, open)) return false;

		if (open && groupName.startsWith("rotate_")) {
			pose.translate(0.0f, (float) hinge.y, (float) hinge.z);
			pose.rotate(Axis.XP.rotationDegrees(OPEN_DEGREES));
			pose.translate(0.0f, (float) -hinge.y, (float) -hinge.z);
		}
		return true;
	}

	/** The two flag plates are one plate: whichever the position calls for. */
	private static boolean drawn(String groupName, boolean open) {
		if (groupName.startsWith("flag_open")) return open;
		if (groupName.startsWith("flag_shut")) return !open;

		return true;
	}

	public static void init() {
		ModelBindings.registerAll(ModBlocks.SWITCHGEARS);
		// an open blade is pointed at where it swung to, and the flag that is not shown is not there to point at
		GroupPoses.register(SwitchgearBlockEntity.class, (gear, model) -> {
			boolean open = SwitchgearBlock.open(gear.getBlockState());
			Vec3 hinge = hinge(model);
			return groupName -> {
				Matrix4f pose = new Matrix4f();
				return groupPose(groupName, hinge, open, pose) ? pose : null;
			};
		});
	}
}
