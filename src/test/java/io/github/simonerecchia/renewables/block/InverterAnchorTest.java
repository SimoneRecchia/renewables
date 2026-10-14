package io.github.simonerecchia.renewables.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.simonerecchia.renewables.api.power.InverterSpec;
import io.github.simonerecchia.renewables.catalog.InverterCatalog;
import io.github.simonerecchia.renewables.model.ExtraPose;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The inverter's wire ends on the porcelain the renderer draws, at every size of cabinet and every facing.
 *
 * The block entity scales the fitting's centre and hands it to {@link ExtraPose#toWorld}; the renderer turns the
 * block's pose onto the facing and then scales the whole model.  Both read {@link InverterSpec#renderScale}, and
 * this holds the two transforms to each other with the real box out of the fittings table.
 */
class InverterAnchorTest {
	private static final double EPS = 1e-5;
	/** The facing the shared cabinet model was drawn at (PvInverterBlock.AUTHORED, not named here so no block class loads). */
	private static final Direction AUTHORED = Direction.NORTH;
	private static final BlockPos POS = new BlockPos(-37, 71, 105);

	@Test
	void theCabinetIsDrawnAtThreeSizes() {
		assertEquals(0.62, InverterCatalog.VX_10.renderScale(), EPS);
		assertEquals(0.92, InverterCatalog.VX_110.renderScale(), EPS);
		assertEquals(0.92, InverterCatalog.VX_350.renderScale(), EPS);
		assertEquals(1.0, InverterCatalog.VC_2500.renderScale(), EPS);
	}

	@Test
	void theAnchorIsWhereTheRendererDrawsThePorcelain() throws Exception {
		Vector3f centre = porcelainCentre();

		for (InverterSpec spec : InverterCatalog.all()) {
			double scale = spec.renderScale();
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				Vec3 anchor = ExtraPose.toWorld(new Vec3(centre.x(), centre.y(), centre.z()).scale(scale), POS, AUTHORED, facing, null);

				// the renderer's pose: onto the block, about the middle of its floor, turned, then the model scaled
				Vector3f drawn = new Matrix4f()
						.translate(POS.getX() + 0.5f, POS.getY(), POS.getZ() + 0.5f)
						.rotateY((float) Math.toRadians(ModelFacing.degrees(AUTHORED, facing)))
						.scale((float) scale)
						.transformPosition(new Vector3f(centre));

				String where = spec.displayName() + " facing " + facing;
				assertEquals(drawn.x(), anchor.x, EPS, "x of " + where);
				assertEquals(drawn.y(), anchor.y, EPS, "y of " + where);
				assertEquals(drawn.z(), anchor.z, EPS, "z of " + where);
			}
		}
	}

	/** The middle of the inverter's insulator box, read the way PartBoxes reads it. */
	private static Vector3f porcelainCentre() throws Exception {
		try (InputStream stream = InverterAnchorTest.class.getResourceAsStream("/data/renewables/fittings.json")) {
			assertNotNull(stream, "fittings.json is on the classpath");

			JsonObject models = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("models");
			JsonObject box = models.getAsJsonObject("renewables:models/pv_inverter/pv_inverter.obj")
					.getAsJsonObject("insulator_porcelain");
			return vector(box.getAsJsonArray("min")).add(vector(box.getAsJsonArray("max"))).mul(0.5f);
		}
	}

	private static Vector3f vector(JsonArray array) {
		return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
	}
}
