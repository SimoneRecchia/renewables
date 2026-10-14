package io.github.simonerecchia.renewables.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.simonerecchia.renewables.model.PartBox;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The reader against the project's own models, the real files in {@code assets/renewables/models}: they all load, every
 * group is where the renderers and the definitions expect it, no part is lost to a repeated name, and every box is the
 * one {@code tools/gen_fittings.py} wrote into {@code fittings.json} reading the same files in Python.
 */
class ModelFilesTest {
	private static final String ASSETS = "assets/renewables/";

	/** Models Forge bakes into chunks (cables, conductor runs): not the machine reader's, but they must still load. */
	private static final List<String> CHUNK_FOLDERS = List.of("abc_conductor_run", "mv_conductor_run", "hv_conductor_run",
			"dc_string_cable", "dc_trunk_cable");

	private static Path models;
	private static final Map<String, MeshModel> LOADED = new LinkedHashMap<>();
	private static List<String> machineModels;

	@BeforeAll
	static void loadEverything() throws IOException, URISyntaxException {
		var url = ModelFilesTest.class.getClassLoader().getResource(ASSETS + "models");
		assertNotNull(url, "the models are on the test classpath");
		models = Paths.get(url.toURI());

		List<String> all;
		try (Stream<Path> walk = Files.walk(models)) {
			all = walk.filter(p -> p.toString().endsWith(".obj")).map(p -> models.relativize(p).toString().replace('\\', '/')).sorted()
					.collect(Collectors.toList());
		}

		machineModels = all.stream().filter(p -> CHUNK_FOLDERS.stream().noneMatch(f -> p.startsWith(f + "/"))).collect(Collectors.toList());
		for (String relative : all) {
			LOADED.put(relative, ObjModelLoader.load("models/" + relative, ModelFilesTest::open));
		}
	}

	private static InputStream open(String path) throws IOException {
		InputStream stream = ModelFilesTest.class.getClassLoader().getResourceAsStream(ASSETS + path);
		if (stream == null) throw new java.io.FileNotFoundException(path);

		return stream;
	}

	private static MeshModel machine(String name) {
		MeshModel model = LOADED.get(name);
		assertNotNull(model, name + " is a model of the project");
		return model;
	}

	@Test
	void everyModelLoadsAndHasGroups() {
		assertTrue(LOADED.size() >= 60, "found " + LOADED.size() + " models");
		for (Map.Entry<String, MeshModel> entry : LOADED.entrySet()) {
			assertFalse(entry.getValue().groups.isEmpty(), entry.getKey() + " has no group");
		}
	}

	@Test
	void everyMachineModelHasItsTexturesAndTheyExist() {
		for (String name : machineModels) {
			MeshModel model = machine(name);
			assertTrue(model.warnings().isEmpty(), name + " loads with warnings: " + model.warnings());

			for (MeshModel.Group group : model.groups.values()) {
				if (group.isMarker()) continue;

				assertNotNull(group.textureFile, name + ": group " + group.name + " has no texture");
				assertNotNull(ModelFilesTest.class.getClassLoader().getResource(ASSETS + "textures/block/" + group.textureFile),
						name + ": " + group.textureFile + " (group " + group.name + ") is not in textures/block");
			}
		}
	}

	@Test
	void everyObjectNameIsDeclaredOnce() throws IOException {
		// what check_obj_loading.py enforces, from the same files: a repeated "o" is the one Forge would drop
		for (String name : LOADED.keySet()) {
			List<String> objects = lines(name).stream().filter(l -> l.startsWith("o ")).map(l -> l.substring(2).trim().split("\\s+")[0])
					.collect(Collectors.toList());
			assertEquals(new LinkedHashSet<>(objects).size(), objects.size(), name + " repeats an object name");
		}
	}

	@Test
	void noGroupIsMergedByAccidentAndNoFaceIsLost() throws IOException {
		for (Map.Entry<String, MeshModel> entry : LOADED.entrySet()) {
			// the groups the file declares, worked out here without the reader, with the rule of models.md 2.2
			List<String> lines = lines(entry.getKey());
			boolean named = lines.stream().anyMatch(l -> l.startsWith("o ") && !l.substring(2).trim().split("\\s+")[0].equals("default"));
			Set<String> keys = new LinkedHashSet<>();
			Map<String, String> pair = new LinkedHashMap<>();
			String object = "default";
			String material = "default";
			int expectedVertices = 0;

			for (String line : lines) {
				if (line.startsWith("o ")) {
					object = line.substring(2).trim().split("\\s+")[0];
				} else if (line.startsWith("usemtl ")) {
					material = line.substring(7).trim();
				} else if (line.startsWith("f ")) {
					int corners = line.substring(2).trim().split("\\s+").length;
					expectedVertices += (corners - 2) * 3;
					String key = named ? object + "_" + material : material;
					keys.add(key);
					String previous = pair.put(key, object + "|" + material);
					assertTrue(previous == null || previous.equals(object + "|" + material),
							entry.getKey() + ": " + key + " is two different (object, material) pairs");
				}
			}

			MeshModel model = entry.getValue();
			assertEquals(keys, new LinkedHashSet<>(model.groups.keySet()), entry.getKey() + ": the groups");
			assertEquals(expectedVertices, model.groups.values().stream().mapToInt(MeshModel.Group::vertexCount).sum(),
					entry.getKey() + ": the vertices");
		}
	}

	@Test
	void everyNormalIsFiniteAndEveryBoxIsOrdered() {
		for (Map.Entry<String, MeshModel> entry : LOADED.entrySet()) {
			for (MeshModel.Group group : entry.getValue().groups.values()) {
				for (float n : group.normals) assertTrue(Float.isFinite(n), entry.getKey() + " " + group.name);
				for (float u : group.uvs) assertTrue(Float.isFinite(u), entry.getKey() + " " + group.name);

				PartBox box = group.box;
				assertNotNull(box, entry.getKey() + " " + group.name);
				assertTrue(box.min().x() <= box.max().x() && box.min().y() <= box.max().y() && box.min().z() <= box.max().z());
			}
		}
	}

	// ---- the names the definitions and the renderers rely on ----

	@Test
	void theWireFittingsOfEveryDefinitionAreGroups() {
		assertHas("utility_pole/utility_pole.obj", numbered("insulator_", "_porcelain", 8));
		assertHas("power_box/power_box.obj", List.of("insulator_porcelain"));
		assertHas("electric_cab/cab.obj", List.of("insulator_input_porcelain", "insulator_output_porcelain"));
		assertHas("pv_inverter/pv_inverter.obj", List.of("insulator_porcelain"));
		assertHas("tx_machine/tx_machine.obj", numbered("bushing_", "_porcelain", 3));
		assertHas("tx_substation/tx_substation.obj", numbered("bushing_", "_porcelain", 6));
		for (String tower : List.of("lattice_suspension", "lattice_tension", "lattice_terminal")) {
			assertHas(tower + "/" + tower + ".obj", numbered("insulator_", "_porcelain", 6));
		}
		for (String gear : List.of("mv_disconnector", "mv_breaker")) {
			assertHas(gear + "/" + gear + ".obj", numbered("insulator_", "_porcelain", 6));
		}
	}

	@Test
	void everyFittingBoxIsSmallAndHasAnAnchorAtItsTop() {
		MeshModel pole = machine("utility_pole/utility_pole.obj");
		for (String name : numbered("insulator_", "_porcelain", 8)) {
			PartBox box = pole.box(name);
			assertNotNull(box);
			assertTrue(box.max().x() - box.min().x() < 0.3, name + " is a post insulator");
			assertEquals(box.max().y(), box.top().y());
		}
	}

	@Test
	void theMarkersTheRenderersMeasureFromExistAndAreSinglePoints() {
		assertMarkers("pv_track/pv_track.obj", "pivot_tube");
		assertMarkers("pv_dual/pv_dual.obj", "pivot_azimuth", "pivot_elevation");
		assertMarkers("mv_disconnector/mv_disconnector.obj", "pivot_blade");
		assertMarkers("pv_combiner/pv_combiner.obj", "pivot_handle");
		assertMarkers("met_mast/met_mast.obj", "pivot_cups", "pivot_vane");

		// a fan is turned about the marker of its own side
		MeshModel inverter = machine("pv_inverter/pv_inverter.obj");
		List<String> fans = inverter.groups.keySet().stream().filter(n -> n.startsWith("rotate_fan_")).collect(Collectors.toList());
		assertFalse(fans.isEmpty());
		for (String fan : fans) {
			String side = fan.substring("rotate_fan_".length()).split("_")[0];
			assertNotNull(inverter.union("pivot_fan_" + side), "no pivot for " + fan);
		}
	}

	@Test
	void theTurbineModelHasTheGroupsItsRendererPoses() {
		MeshModel turbine = machine("wind_turbine/wind_turbine.obj");
		assertHas("wind_turbine/wind_turbine.obj", List.of("insulator_insulator_porcelain"));
		for (String prefix : List.of("pole_tower", "pole_base", "motor_nacelle", "rotate_1", "rotate_2")) {
			assertNotNull(turbine.union(prefix), "no group " + prefix);
		}

		// the tube is 13 blocks, the nacelle rests on it, the hub is the middle of rotate_2
		assertEquals(13.0f, turbine.union("pole").max().y(), 1e-4);
		assertEquals(13.0f, turbine.union("motor").min().y(), 1e-4);
		var hub = turbine.centerOf("rotate_2");
		assertEquals(0.0f, hub.x(), 1e-4);
		assertEquals(13.30f, hub.y(), 1e-3);
		assertEquals(1.04f, hub.z(), 1e-3);
		// the fitting is at the foot, beside the tower, and in reach
		var fitting = turbine.box("insulator_insulator_porcelain").center();
		assertEquals(0.375f, fitting.x(), 1e-3);
		assertEquals(0.320f, fitting.y(), 1e-3);
		assertEquals(0.375f, fitting.z(), 1e-3);
		// no marker is ever drawn
		assertTrue(turbine.group("pivot_rotor_flange").isMarker());
	}

	@Test
	void theCableEntriesAndTheMovingGroupsUseTheNamesTheRenderersTest() {
		for (String name : List.of("pv_inverter/pv_inverter.obj", "pv_combiner/pv_combiner.obj")) {
			Set<String> entries = machine(name).groups.keySet().stream().filter(n -> n.startsWith("entry_")).collect(Collectors.toSet());
			for (String side : List.of("north", "east", "south", "west")) {
				assertTrue(entries.stream().anyMatch(e -> e.startsWith("entry_" + side)), name + " has no entry_" + side);
			}
		}

		assertTrue(machine("mv_breaker/mv_breaker.obj").groups.keySet().stream().anyMatch(n -> n.startsWith("flag_open")));
		assertTrue(machine("mv_breaker/mv_breaker.obj").groups.keySet().stream().anyMatch(n -> n.startsWith("flag_shut")));
		assertTrue(machine("mv_disconnector/mv_disconnector.obj").groups.keySet().stream().anyMatch(n -> n.startsWith("rotate_")));
		assertTrue(machine("power_box/power_box.obj").groups.keySet().stream().anyMatch(n -> n.startsWith("plinth")));
		assertTrue(machine("power_box/power_box.obj").groups.keySet().stream().anyMatch(n -> n.startsWith("conduit")));
		assertTrue(machine("met_mast/met_mast.obj").groups.keySet().stream().anyMatch(n -> n.startsWith("rotate_cups")));
		assertTrue(machine("met_mast/met_mast.obj").groups.keySet().stream().anyMatch(n -> n.startsWith("rotate_vane")));
	}

	@Test
	void anObjectInTwoMaterialsIsTwoGroups() {
		// the disconnector's insulator: one object, porcelain and steel and a blade, written as one "o" with several usemtl
		MeshModel tower = machine("mv_disconnector/mv_disconnector.obj");
		assertNotNull(tower.group("insulator_1_porcelain"));
		assertTrue(tower.groups.keySet().stream().anyMatch(n -> n.startsWith("insulator_1_") && !n.equals("insulator_1_porcelain")),
				"the other material of insulator_1 is its own group");
	}

	// ---- the boxes against what the Python tools computed ----

	@Test
	void everyBoxIsTheOneTheGeneratorWroteIntoFittingsJson() throws IOException {
		JsonObject root;
		try (InputStream stream = ModelFilesTest.class.getClassLoader().getResourceAsStream("data/renewables/fittings.json")) {
			assertNotNull(stream, "fittings.json is a resource: run tools/gen_fittings.py");
			root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		}

		assertEquals(1, root.get("format").getAsInt());
		JsonObject table = root.getAsJsonObject("models");
		Set<String> inTable = new HashSet<>();

		for (Map.Entry<String, JsonElement> model : table.entrySet()) {
			String key = model.getKey();
			assertTrue(key.startsWith("renewables:models/"), key);
			String relative = key.substring("renewables:models/".length());
			inTable.add(relative);

			MeshModel loaded = machine(relative);
			JsonObject groups = model.getValue().getAsJsonObject();
			assertEquals(new ArrayList<>(loaded.groups.keySet()), new ArrayList<>(groups.keySet()), key + ": group names and order");

			for (Map.Entry<String, JsonElement> group : groups.entrySet()) {
				PartBox box = loaded.box(group.getKey());
				JsonObject entry = group.getValue().getAsJsonObject();
				for (int axis = 0; axis < 3; axis++) {
					assertEquals(entry.getAsJsonArray("min").get(axis).getAsFloat(), component(box.min(), axis), 1e-5f,
							key + " " + group.getKey() + " min " + axis);
					assertEquals(entry.getAsJsonArray("max").get(axis).getAsFloat(), component(box.max(), axis), 1e-5f,
							key + " " + group.getKey() + " max " + axis);
				}
			}
		}

		// every machine model that is on disk is in the table, so no definition can name one the server does not know
		for (String name : machineModels) {
			assertTrue(inTable.contains(name), name + " is not in fittings.json: run tools/gen_fittings.py");
		}
	}

	// ---- helpers ----

	private static float component(org.joml.Vector3f v, int axis) {
		return axis == 0 ? v.x() : axis == 1 ? v.y() : v.z();
	}

	private static List<String> numbered(String prefix, String suffix, int count) {
		List<String> names = new ArrayList<>();
		for (int i = 1; i <= count; i++) names.add(prefix + i + suffix);

		return names;
	}

	private static void assertHas(String model, List<String> names) {
		MeshModel loaded = machine(model);
		for (String name : names) {
			assertNotNull(loaded.group(name), model + " has no group " + name);
			assertNotNull(loaded.box(name), model + ": " + name + " has no box");
		}
	}

	private static void assertMarkers(String model, String... prefixes) {
		MeshModel loaded = machine(model);
		for (String prefix : prefixes) {
			PartBox box = loaded.union(prefix);
			assertNotNull(box, model + " has no " + prefix);
			assertEquals(box.min().x(), box.max().x(), 1e-6, prefix + " is a point");
			assertEquals(box.min().y(), box.max().y(), 1e-6, prefix + " is a point");
			assertEquals(box.min().z(), box.max().z(), 1e-6, prefix + " is a point");
		}
	}

	private static List<String> lines(String relative) throws IOException {
		return Files.readAllLines(models.resolve(relative), StandardCharsets.UTF_8).stream().map(String::trim)
				.collect(Collectors.toList());
	}
}
