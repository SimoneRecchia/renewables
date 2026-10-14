package io.github.simonerecchia.renewables.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.simonerecchia.renewables.Renewables;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3f;

/**
 * Where each wire fitting of each machine is, as data every side can read.
 *
 * Wire anchors are computed on the server, which never loads a model, so the geometry they depend on is
 * generated ahead of time into {@code data/renewables/fittings.json} (see docs/ARCHITECTURE.md section 2)
 * and read from the jar by client and server alike.  A missing or malformed file is not fatal: every lookup
 * answers "absent" and one warning says why, and callers fall back to the middle of the block.
 */
public final class PartBoxes {
	/** Classpath location of the generated table. */
	private static final String RESOURCE = "/data/" + Renewables.MOD_ID + "/fittings.json";

	private static volatile Map<ResourceLocation, Map<String, PartBox>> table;

	private PartBoxes() {
	}

	/** The box of a named group of the model that draws {@code block}, or null when the table has none. */
	@Nullable
	public static PartBox box(Block block, String groupName) {
		ModelDefinition definition = ModelDefinitions.of(block);
		if (definition == null) return null;

		Map<String, PartBox> boxes = load().get(definition.obj());
		return boxes == null ? null : boxes.get(groupName);
	}

	/** Forgets what was read, so the next lookup reads the file again. */
	public static void reload() {
		table = null;
	}

	private static Map<ResourceLocation, Map<String, PartBox>> load() {
		Map<ResourceLocation, Map<String, PartBox>> loaded = table;
		if (loaded != null) return loaded;

		synchronized (PartBoxes.class) {
			if (table == null) table = read();
			return table;
		}
	}

	private static Map<ResourceLocation, Map<String, PartBox>> read() {
		Map<ResourceLocation, Map<String, PartBox>> result = new HashMap<>();

		try (InputStream stream = PartBoxes.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				Renewables.LOGGER.warn("{} is missing: wire anchors fall back to the centre of the block", RESOURCE);
				return result;
			}

			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject models = root.getAsJsonObject("models");
			if (models == null) return result;

			for (Map.Entry<String, JsonElement> model : models.entrySet()) {
				ResourceLocation obj = ResourceLocation.tryParse(model.getKey());
				if (obj == null) continue;

				Map<String, PartBox> boxes = new HashMap<>();
				for (Map.Entry<String, JsonElement> group : model.getValue().getAsJsonObject().entrySet()) {
					JsonObject entry = group.getValue().getAsJsonObject();
					boxes.put(group.getKey(), new PartBox(vector(entry.getAsJsonArray("min")), vector(entry.getAsJsonArray("max"))));
				}
				result.put(obj, boxes);
			}
		} catch (IOException | RuntimeException e) {
			Renewables.LOGGER.warn("{} could not be read ({}): wire anchors fall back to the centre of the block", RESOURCE, e.toString());
			result.clear();
		}

		return result;
	}

	private static Vector3f vector(com.google.gson.JsonArray array) {
		return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
	}
}
