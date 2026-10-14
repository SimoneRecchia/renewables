package io.github.simonerecchia.renewables.client.render;

import io.github.simonerecchia.renewables.client.model.MeshModel;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** What a machine's draw callback is handed: the model, the texture to fall back on, and the packed light. */
@OnlyIn(Dist.CLIENT)
public record RenderContext(MeshModel model, ResourceLocation texture, int packedLight) {
}
