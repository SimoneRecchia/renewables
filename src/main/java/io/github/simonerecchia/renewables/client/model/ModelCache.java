package io.github.simonerecchia.renewables.client.model;

import io.github.simonerecchia.renewables.Renewables;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Every OBJ the client has loaded, by resource, loaded the first time anyone asks (docs/spec/models.md section 4).
 *
 * A model that cannot be loaded is remembered as such, so a missing file costs one log line and not one per
 * frame, and so nothing retries it until the resources are reloaded.  Nothing here ever throws at a caller: no
 * model is an answer ({@code null}) and the machine simply is not drawn.
 */
@OnlyIn(Dist.CLIENT)
public final class ModelCache {
	private static final Map<ResourceLocation, MeshModel> MODELS = new ConcurrentHashMap<>();
	private static final Set<ResourceLocation> FAILED = ConcurrentHashMap.newKeySet();

	private ModelCache() {
	}

	/** The model for an OBJ resource, or null when it is missing or unreadable. */
	@Nullable
	public static MeshModel get(ResourceLocation obj) {
		MeshModel cached = MODELS.get(obj);
		if (cached != null) return cached;
		if (FAILED.contains(obj)) return null;

		return load(obj);
	}

	private static synchronized MeshModel load(ResourceLocation obj) {
		MeshModel cached = MODELS.get(obj);
		if (cached != null) return cached;
		if (FAILED.contains(obj)) return null;

		ResourceManager manager = Minecraft.getInstance().getResourceManager();
		try {
			MeshModel model = ObjModelLoader.load(obj.getPath(), path -> manager.open(new ResourceLocation(obj.getNamespace(), path)));
			for (String warning : model.warnings()) Renewables.LOGGER.warn("Model {}", warning);

			MODELS.put(obj, model);
			return model;
		} catch (IOException | RuntimeException e) {
			// a missing file is an IOException; anything else is a file that is there and wrong, and neither may crash a frame
			FAILED.add(obj);
			Renewables.LOGGER.error("Model {} could not be loaded, so the machines that use it are not drawn: {}", obj, e.toString());
			return null;
		}
	}

	/** Forgets every model and every failure: the resources have changed, or the world has gone. */
	public static synchronized void reload() {
		MODELS.clear();
		FAILED.clear();
	}
}
