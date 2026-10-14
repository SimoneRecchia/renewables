package io.github.simonerecchia.renewables.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The reader and the group builder on small hand-written files, one rule of docs/spec/models.md at a time. */
class ObjReaderTest {
	/** Loads an OBJ and its MTL from strings; any other path is missing. */
	private static MeshModel load(String obj, String mtl) throws IOException {
		return ObjModelLoader.load("models/t/t.obj", path -> {
			if (path.equals("models/t/t.obj")) return new ByteArrayInputStream(obj.getBytes(StandardCharsets.UTF_8));
			if (path.equals("models/t/t.mtl") && mtl != null) return new ByteArrayInputStream(mtl.getBytes(StandardCharsets.UTF_8));
			throw new FileNotFoundException(path);
		});
	}

	private static MeshModel load(String obj) throws IOException {
		return load(obj, null);
	}

	private static final String QUAD = """
			v 0 0 0
			v 1 0 0
			v 1 1 0
			v 0 1 0
			""";

	// ---- 1: what is read ----

	@Test
	void aQuadBecomesTwoTrianglesAsAFan() throws IOException {
		MeshModel model = load(QUAD + "o q\nusemtl m\nf 1 2 3 4\n");
		MeshModel.Group group = model.group("q_m");
		assertNotNull(group);
		assertEquals(6, group.vertexCount());
		// (0,1,2) then (0,2,3): vertex 0 leads both
		assertEquals(0f, group.positions[0]);
		assertEquals(1f, group.positions[3]);
		assertEquals(1f, group.positions[6]);
		assertEquals(1f, group.positions[7]);
		// the second triangle is (0,2,3): back to the first vertex, then (1,1,0), then (0,1,0)
		assertEquals(0f, group.positions[9]);
		assertEquals(1f, group.positions[12]);
		assertEquals(1f, group.positions[13]);
		assertEquals(0f, group.positions[15]);
		assertEquals(1f, group.positions[16]);
	}

	@Test
	void anNgonIsNMinusTwoTriangles() throws IOException {
		String pentagon = "v 0 0 0\nv 1 0 0\nv 2 1 0\nv 1 2 0\nv 0 1 0\no p\nusemtl m\nf 1 2 3 4 5\n";
		assertEquals(9, load(pentagon).group("p_m").vertexCount());
	}

	@Test
	void tabsAndTrailingCommentsAreTolerated() throws IOException {
		MeshModel model = load("v\t0\t0\t0\nv 1 0 0 # a comment\nv 0 1 0\t# another\n# a whole line\n\no t\nusemtl m\nf 1 2 3 # done\n");
		MeshModel.Group group = model.group("t_m");
		assertNotNull(group);
		assertEquals(3, group.vertexCount());
		assertEquals(1f, group.positions[3]);
	}

	@Test
	void windowsLineEndingsAreTheSame() throws IOException {
		MeshModel model = load("v 0 0 0\r\nv 1 0 0\r\nv 0 1 0\r\no t\r\nusemtl m\r\nf 1 2 3\r\n");
		assertEquals(3, model.group("t_m").vertexCount());
	}

	@Test
	void missingOrBadComponentsAreZeroAndExtrasAreIgnored() throws IOException {
		MeshModel model = load("v 1 2\nv x 5 6 7 0.5 0.5 0.5\nv 3 4 5\no t\nusemtl m\nf 1 2 3\n");
		float[] p = model.group("t_m").positions;
		assertEquals(1f, p[0]);
		assertEquals(2f, p[1]);
		assertEquals(0f, p[2]);
		assertEquals(0f, p[3]);
		assertEquals(5f, p[4]);
		assertEquals(6f, p[5]);
	}

	@Test
	void negativeIndicesCountBackFromTheListAsItStands() throws IOException {
		// the face comes after only three vertices were defined, so -1 is vertex 3 even though a fourth follows
		MeshModel model = load("v 0 0 0\nv 1 0 0\nv 0 1 0\no t\nusemtl m\nf -3 -2 -1\nv 9 9 9\n");
		float[] p = model.group("t_m").positions;
		assertEquals(0f, p[0]);
		assertEquals(1f, p[3]);
		assertEquals(1f, p[7]);
	}

	@Test
	void aFaceWithOneBadPositionIsDroppedWhole() throws IOException {
		MeshModel model = load(QUAD + "o t\nusemtl m\nf 1 2 9\nf 1 2 x\nf 1 2\nf 1 2 3\n");
		assertEquals(3, model.group("t_m").vertexCount(), "only the last face is valid");
	}

	@Test
	void aBadUvOrNormalOnlyCostsTheFaceItsUvOrNormal() throws IOException {
		MeshModel model = load(QUAD + "vt 0.25 0.75\nvn 0 0 1\no t\nusemtl m\nf 1/9/1 2/1/1 3/1/1\nf 1//9 2//1 3//1\n");
		MeshModel.Group group = model.group("t_m");
		assertEquals(6, group.vertexCount());
		// first face: one vertex has a UV out of range, so the whole face has no UV
		for (int i = 0; i < 6; i++) assertEquals(0f, group.uvs[i]);
		// second face: no UVs at all, and one normal out of range, so the face has its flat normal
		assertEquals(0f, group.normals[9]);
		assertEquals(0f, group.normals[10]);
		assertEquals(1f, group.normals[11]);
	}

	@Test
	void vIsFlippedAndUIsNot() throws IOException {
		MeshModel model = load(QUAD + "vt 0.25 0.75\nvt 0.5 0.0\nvt 1 1\no t\nusemtl m\nf 1/1 2/2 3/3\n");
		float[] uv = model.group("t_m").uvs;
		assertEquals(0.25f, uv[0]);
		assertEquals(0.25f, uv[1], 1e-6);
		assertEquals(0.5f, uv[2]);
		assertEquals(1.0f, uv[3], 1e-6);
		assertEquals(1f, uv[4]);
		assertEquals(0f, uv[5], 1e-6);
	}

	@Test
	void declaredNormalsAreUsedAsTheyAreAndNotNormalised() throws IOException {
		MeshModel model = load(QUAD + "vn 0 0 2\nvn 0 3 0\nvn 4 0 0\no t\nusemtl m\nf 1//1 2//2 3//3\n");
		float[] n = model.group("t_m").normals;
		assertEquals(2f, n[2]);
		assertEquals(3f, n[4]);
		assertEquals(4f, n[6]);
	}

	@Test
	void flatNormalIsTheCrossProductAndAZeroAreaTriangleFallsBackToUp() throws IOException {
		MeshModel model = load(QUAD + "o t\nusemtl m\nf 1 2 3\n"
				+ "o z\nusemtl m\nf 1 1 1\n");
		float[] n = model.group("t_m").normals;
		assertEquals(0f, n[0]);
		assertEquals(0f, n[1]);
		assertEquals(1f, n[2], 1e-6);

		MeshModel.Group degenerate = model.group("z_m");
		assertNotNull(degenerate, "a degenerate face is kept: a pivot marker is exactly that");
		assertEquals(3, degenerate.vertexCount());
		for (int i = 0; i < 9; i++) {
			assertFalse(Float.isNaN(degenerate.normals[i]));
		}
		assertEquals(1f, degenerate.normals[1]);
		assertNotNull(degenerate.box);
		assertEquals(degenerate.box.min().x(), degenerate.box.max().x());
	}

	@Test
	void unknownStatementsAreIgnored() throws IOException {
		MeshModel model = load(QUAD + "s 1\nl 1 2\np 1\nvp 0.5\ncstype bspline\nshadow_obj x\no t\nusemtl m\nf 1 2 3\n");
		assertEquals(3, model.group("t_m").vertexCount());
	}

	// ---- 2: groups ----

	@Test
	void aFileWithNoObjectsIsSplitByMaterialOnly() throws IOException {
		MeshModel model = load(QUAD + "usemtl a\nf 1 2 3\nusemtl b\nf 1 3 4\n");
		assertEquals(List.of("a", "b"), new ArrayList<>(model.groups.keySet()));
		assertEquals("default", model.group("a").object);
	}

	@Test
	void groupsAreObjectUnderscoreMaterialAndFacesWithNoMaterialAreDefault() throws IOException {
		MeshModel model = load(QUAD + "o box\nf 1 2 3\nusemtl steel\nf 1 3 4\n");
		assertEquals(List.of("box_default", "box_steel"), new ArrayList<>(model.groups.keySet()));
	}

	@Test
	void aMaterialSurvivesAChangeOfObject() throws IOException {
		MeshModel model = load(QUAD + "o one\nusemtl m\nf 1 2 3\no two\nf 1 3 4\n");
		assertNotNull(model.group("two_m"));
	}

	@Test
	void facesBeforeTheFirstObjectAreInDefault() throws IOException {
		MeshModel model = load(QUAD + "usemtl m\nf 1 2 3\no named\nf 1 3 4\n");
		assertNotNull(model.group("default_m"));
		assertNotNull(model.group("named_m"));
	}

	@Test
	void gIsTheSameAsO() throws IOException {
		MeshModel model = load(QUAD + "g part\nusemtl m\nf 1 2 3\n");
		assertNotNull(model.group("part_m"));
	}

	@Test
	void anObjectNameIsItsFirstTokenAndAnUnnamedOneIsDefault() throws IOException {
		MeshModel model = load(QUAD + "o first second\nusemtl m\nf 1 2 3\no\nf 1 3 4\n");
		assertNotNull(model.group("first_m"));
		assertNotNull(model.group("default_m"));
	}

	@Test
	void twoObjectsWithTheSameNameMergeAndKeepEveryFaceInFileOrder() throws IOException {
		MeshModel model = load(QUAD + "o a\nusemtl m\nf 1 2 3\no b\nusemtl m\nf 1 2 4\no a\nusemtl m\nf 1 3 4\n");
		MeshModel.Group a = model.group("a_m");
		assertEquals(6, a.vertexCount(), "the second block of a joins the first, it does not replace it");
		// the first triangle is (0,0,0) (1,0,0) (1,1,0); the second block's is (0,0,0) (1,1,0) (0,1,0)
		assertEquals(1f, a.positions[3]);
		assertEquals(1f, a.positions[12]);
		assertEquals(List.of("a_m", "b_m"), new ArrayList<>(model.groups.keySet()));
	}

	@Test
	void anObjectInTwoMaterialsIsTwoGroups() throws IOException {
		MeshModel model = load(QUAD + "o insulator_1\nusemtl porcelain\nf 1 2 3\nusemtl blade\nf 1 3 4\n");
		assertEquals(List.of("insulator_1_porcelain", "insulator_1_blade"), new ArrayList<>(model.groups.keySet()));
	}

	@Test
	void collidingKeysAreMergedAndReportedOnce() throws IOException {
		MeshModel model = load(QUAD + "o a_b\nusemtl c\nf 1 2 3\no a\nusemtl b_c\nf 1 3 4\nf 1 2 4\n");
		assertEquals(9, model.group("a_b_c").vertexCount(), "no face is lost");
		long warnings = model.warnings().stream().filter(w -> w.contains("a_b_c")).count();
		assertEquals(1, warnings);
	}

	@Test
	void groupsWithNoFacesAreNotCreated() throws IOException {
		MeshModel model = load(QUAD + "o empty\nusemtl m\no full\nf 1 2 3\nf 1 2\n");
		assertEquals(List.of("full_m"), new ArrayList<>(model.groups.keySet()));
	}

	@Test
	void aModelWithNoFacesIsValidAndEmpty() throws IOException {
		MeshModel model = load(QUAD);
		assertTrue(model.groups.isEmpty());
		assertTrue(model.boxes().isEmpty());
	}

	// ---- 2.7: boxes ----

	@Test
	void theBoxIsTheMinAndMaxOfEveryVertexOfTheGroup() throws IOException {
		MeshModel model = load("v -1 2 3\nv 4 -5 6\nv 0 0 -7\nv 9 9 9\no t\nusemtl m\nf 1 2 3\no u\nusemtl m\nf 1 2 4\n");
		var box = model.box("t_m");
		assertEquals(-1f, box.min().x());
		assertEquals(-5f, box.min().y());
		assertEquals(-7f, box.min().z());
		assertEquals(4f, box.max().x());
		assertEquals(2f, box.max().y());
		assertEquals(6f, box.max().z());
		assertEquals(1.5f, box.center().x());
		assertNull(model.box("nothing"));
		assertEquals(2, model.boxes().size());
	}

	@Test
	void theUnionBoxCoversEveryGroupWithThePrefix() throws IOException {
		MeshModel model = load("v 0 0 0\nv 1 1 1\nv 5 5 5\nv 6 6 6\nv 9 9 9\no pivot_hub_a\nusemtl m\nf 1 1 1\no pivot_hub_b\nusemtl m\nf 2 2 2\no other\nusemtl m\nf 5 5 5\n");
		var union = model.union("pivot_hub");
		assertEquals(0f, union.min().x());
		assertEquals(1f, union.max().x());
		assertNull(model.union("pivot_nothing"));
		assertEquals(0.5f, model.centerOf("pivot_hub").y());
	}

	// ---- 1.4, 3: materials and textures ----

	@Test
	void aMaterialLibraryGivesTheTextureOfEachMaterial() throws IOException {
		String obj = "mtllib t.mtl\n" + QUAD + "o a\nusemtl first\nf 1 2 3\nusemtl second\nf 1 3 4\nusemtl bare\nf 1 2 4\n";
		String mtl = "# generated\nnewmtl first\nNs 250\nmap_Kd one.png\nnewmtl second\nKd 1 1 1\nmap_Kd -s 1 1 1 two.png\nnewmtl bare\nd 1.0\n";
		MeshModel model = load(obj, mtl);
		assertEquals("one.png", model.group("a_first").textureFile);
		assertEquals("two.png", model.group("a_second").textureFile, "options before the file are skipped");
		assertNull(model.group("a_bare").textureFile);
		assertTrue(model.warnings().stream().anyMatch(w -> w.contains("\"bare\"")), "a material with no texture is reported");
	}

	@Test
	void aChunkModelTextureVariableIsNoTexture() {
		Map<String, String> library = MtlReader.newLibrary();
		assertThrowsNothing(() -> MtlReader.read(new java.io.StringReader("newmtl m\nmap_Kd #porcelain\n"), library));
		assertTrue(library.containsKey("m"));
		assertNull(library.get("m"));
	}

	@Test
	void aMapKdBeforeAnyNewmtlIsIgnoredAndALaterMaterialWins() throws IOException {
		Map<String, String> library = MtlReader.newLibrary();
		MtlReader.read(new java.io.StringReader("map_Kd stray.png\nnewmtl m\nmap_Kd a.png\nnewmtl m\nmap_Kd b.png\n"), library);
		assertEquals(Map.of("m", "b.png"), library);
	}

	@Test
	void aMissingMaterialLibraryIsAWarningNotAnError() throws IOException {
		MeshModel model = load("mtllib gone.mtl\n" + QUAD + "o a\nusemtl m\nf 1 2 3\n");
		assertEquals(3, model.group("a_m").vertexCount());
		assertTrue(model.warnings().stream().anyMatch(w -> w.contains("gone.mtl")));
	}

	@Test
	void aMissingObjIsAnIOExceptionTheCacheCanCatch() {
		assertThrows(IOException.class, () -> ObjModelLoader.load("models/none/none.obj", path -> {
			throw new FileNotFoundException(path);
		}));
		assertThrows(IOException.class, () -> ObjModelLoader.load("models/none/none.obj", path -> null));
	}

	private static void assertThrowsNothing(ThrowingRunnable runnable) {
		try {
			runnable.run();
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
