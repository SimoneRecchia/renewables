package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.client.model.MeshModel;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * What a machine's draw callback is handed: the model, the texture to fall back on and the packed light, and -
 * kept for {@link MachineRenderer} alone - the frame's projection and the machine's own vertex buffers, so that a
 * renderer passes the context back to {@code renderGrouped} and never names a buffer.
 */
@OnlyIn(Dist.CLIENT)
public final class RenderContext {
	private final MeshModel model;
	private final ResourceLocation texture;
	private final int packedLight;
	final Matrix4f projection;
	final Map<String, MachineRenderer.GroupBuffer> buffers;

	RenderContext(MeshModel model, ResourceLocation texture, int packedLight, Matrix4f projection,
			Map<String, MachineRenderer.GroupBuffer> buffers) {
		this.model = model;
		this.texture = texture;
		this.packedLight = packedLight;
		this.projection = projection;
		this.buffers = buffers;
	}

	public MeshModel model() {
		return model;
	}

	public ResourceLocation texture() {
		return texture;
	}

	public int packedLight() {
		return packedLight;
	}
}
