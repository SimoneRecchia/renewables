package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.client.model.MeshModel;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * What a machine's draw callback is handed: the model, the texture to fall back on and the packed light, and - kept for
 * {@link MachineRenderer} alone - the pass's lights, so that a renderer passes the context back to {@code renderGrouped}
 * and never names a buffer.
 */
@OnlyIn(Dist.CLIENT)
public final class RenderContext {
	private final MeshModel model;
	private final ResourceLocation texture;
	private final int packedLight;
	final PoseLighting lighting;

	RenderContext(MeshModel model, ResourceLocation texture, int packedLight, PoseLighting lighting) {
		this.model = model;
		this.texture = texture;
		this.packedLight = packedLight;
		this.lighting = lighting;
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
