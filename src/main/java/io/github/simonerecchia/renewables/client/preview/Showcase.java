package io.github.simonerecchia.renewables.client.preview;

import io.github.simonerecchia.renewables.api.power.PvArraySpec;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.block.WindTurbineBlockEntity;
import io.github.simonerecchia.renewables.catalog.PvCatalog;
import io.github.simonerecchia.renewables.catalog.TurbineCatalog;
import io.github.simonerecchia.renewables.registry.ModTabs;
import io.github.simonerecchia.renewables.wind.TurbineGeometry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * The pictures the Modrinth page shows, as the game draws them: every array, a C90 on its tallest tower, and every item
 * the mod adds. {@code -Drenewables.preview=showcase}; .github/workflows/screenshots.yml runs it on a virtual screen.
 */
@OnlyIn(Dist.CLIENT)
final class Showcase {
	/** The flat preset's grass is at y -61, so everything here stands on -60. */
	private static final int GROUND = -60;
	private static final BlockPos ARRAYS = new BlockPos(0, GROUND, 0);
	private static final BlockPos TURBINE = new BlockPos(48, GROUND, 0);
	private static final BlockPos CHEST = new BlockPos(-24, GROUND, 0);
	/** tp ... facing turns the feet towards its point, and the camera is this far above them: aim this much lower. */
	private static final double EYE = 1.62;
	/** Half the game's default vertical field of view, 70 degrees. */
	private static final double HALF_FOV = Math.toRadians(35.0);

	private Showcase() {
	}

	static List<Runnable> steps() {
		List<Runnable> steps = new ArrayList<>();
		steps.add(() -> {
			PreviewStage.run("gamerule sendCommandFeedback false");
			PreviewStage.run("gamerule doDaylightCycle false");
			PreviewStage.run("gamerule doWeatherCycle false");
			PreviewStage.run("weather clear");
			PreviewStage.run("gamemode creative");
			PreviewStage.hideGui(true);
		});
		// a new world is still lighting its first chunks
		PreviewStage.waitTicks(steps, 100);
		// a creative player falls from where a teleport leaves it unless it is flying, and the camera with it
		steps.add(() -> {
			Minecraft mc = Minecraft.getInstance();
			mc.player.getAbilities().flying = true;
			mc.player.onUpdateAbilities();
		});

		arrays(steps);
		turbine(steps);
		items(steps);

		steps.add(() -> PreviewStage.hideGui(false));
		return steps;
	}

	/** The six arrays, three of each in a row: the low ones in front, the tracked ones behind. */
	private static void arrays(List<Runnable> steps) {
		List<PvArraySpec> specs = PvCatalog.all();
		steps.add(() -> {
			PreviewStage.run("time set 3000");
			for (int i = 0; i < specs.size(); i++) {
				int x = ARRAYS.getX() + (i % 3) * 5;
				int z = ARRAYS.getZ() + (i < 3 ? 5 : 0);
				for (int k = 0; k < 3; k++) {
					PreviewStage.run(PreviewStage.command("setblock %d %d %d %s[facing=south]", x + k, GROUND, z, specs.get(i).id()));
				}
			}
			// in front of the middle and looking down on the modules: from eye height they were a band under a sky
			PreviewStage.run(PreviewStage.command("tp @s %.2f %.2f %.2f facing %.2f %.2f %.2f",
					ARRAYS.getX() + 6.5, GROUND + 3.5, ARRAYS.getZ() + 10.0, ARRAYS.getX() + 6.5, GROUND + 0.3 - EYE, ARRAYS.getZ() + 2.5));
		});
		PreviewStage.waitTicks(steps, 80);
		shot(steps, "showcase-arrays");
	}

	/** The C90 on the tallest tower it is sold on, seen from in front of its rotor and a little to the side. */
	private static void turbine(List<Runnable> steps) {
		TurbineSpec spec = TurbineCatalog.C90_30;
		int segments = spec.maxTowerSegments();
		steps.add(() -> {
			PreviewStage.run("time set 6000");
			PreviewStage.run(PreviewStage.command("fill %d %d %d %d %d %d renewables:turbine_tower",
					TURBINE.getX(), GROUND, TURBINE.getZ(), TURBINE.getX(), GROUND + segments - 1, TURBINE.getZ()));
			PreviewStage.run(PreviewStage.command("setblock %d %d %d %s", TURBINE.getX(), GROUND + segments, TURBINE.getZ(), spec.id()));
		});
		// the nacelle yaws onto the wind at five degrees a second, and the drawn one follows it
		PreviewStage.waitTicks(steps, 200);
		steps.add(() -> faceRotor(spec, segments));
		PreviewStage.waitTicks(steps, 40);
		steps.add(() -> faceRotor(spec, segments));
		PreviewStage.waitTicks(steps, 10);
		shot(steps, "showcase-c90");
	}

	private static void faceRotor(TurbineSpec spec, int segments) {
		Minecraft mc = Minecraft.getInstance();
		BlockPos head = TURBINE.above(segments);
		if (!(mc.level.getBlockEntity(head) instanceof WindTurbineBlockEntity turbine)) return;

		double radius = TurbineGeometry.rotorRadius(spec);
		double top = segments + TurbineGeometry.hubOffset(spec, 0.0)[1] + radius;
		double distance = (top / 2.0 + 1.5) / Math.tan(HALF_FOV);
		// the yaw is the heading the air travels towards (0 east, 90 south) and the rotor faces the other way
		double towards = Math.toRadians(turbine.yawDegrees(0.0f) + 180.0 + 35.0);
		double x = TURBINE.getX() + 0.5 + Math.cos(towards) * distance;
		double z = TURBINE.getZ() + 0.5 + Math.sin(towards) * distance;
		PreviewStage.run(PreviewStage.command("tp @s %.2f %.2f %.2f facing %.2f %.2f %.2f",
				x, GROUND + top * 0.3, z, TURBINE.getX() + 0.5, GROUND + top * 0.5 - EYE, TURBINE.getZ() + 0.5));
	}

	/** The creative tab, top and bottom, then all of it at once in a double chest and the inventory under it. */
	private static void items(List<Runnable> steps) {
		steps.add(Showcase::openTab);
		PreviewStage.waitTicks(steps, 10);
		steps.add(Showcase::parkCursor);
		PreviewStage.waitTicks(steps, 10);
		shot(steps, "showcase-items-1");
		steps.add(() -> {
			if (Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen screen) screen.mouseScrolled(0.0, 0.0, -1000.0);
		});
		PreviewStage.waitTicks(steps, 10);
		shot(steps, "showcase-items-2");
		steps.add(() -> Minecraft.getInstance().setScreen(null));

		steps.add(Showcase::fillChest);
		PreviewStage.waitTicks(steps, 20);
		steps.add(() -> {
			Minecraft mc = Minecraft.getInstance();
			BlockPos first = CHEST;
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(first), Direction.SOUTH, first, false));
		});
		PreviewStage.waitTicks(steps, 20);
		steps.add(Showcase::parkCursor);
		PreviewStage.waitTicks(steps, 10);
		shot(steps, "showcase-items-chest");
		steps.add(() -> Minecraft.getInstance().player.closeContainer());
	}

	private static void openTab() {
		Minecraft mc = Minecraft.getInstance();
		CreativeModeTab tab = ModTabs.MAIN.get();
		// the screen opens on its static selected tab, and Forge turns to the page that tab is on
		try {
			for (Field field : CreativeModeInventoryScreen.class.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) && field.getType() == CreativeModeTab.class) {
					field.setAccessible(true);
					field.set(null, tab);
				}
			}
		} catch (ReflectiveOperationException e) {
			PreviewStage.say("showcase: could not select the tab: " + e);
		}
		mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), mc.options.operatorItemsTab().get()));
	}

	/** The tab's items, in its order: the first 54 into a double chest, the rest into the player's inventory. */
	private static void fillChest() {
		List<String> ids = new ArrayList<>();
		for (ItemStack stack : ModTabs.MAIN.get().getDisplayItems()) {
			ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		}

		// a chest facing south joins the one west of it when it is the left half; the right half holds the first 27
		BlockPos right = CHEST;
		BlockPos left = CHEST.east();
		PreviewStage.run(PreviewStage.command("setblock %d %d %d minecraft:chest[facing=south,type=right]", right.getX(), right.getY(), right.getZ()));
		PreviewStage.run(PreviewStage.command("setblock %d %d %d minecraft:chest[facing=south,type=left]", left.getX(), left.getY(), left.getZ()));
		for (int i = 0; i < ids.size(); i++) {
			if (i < 27) {
				PreviewStage.run(PreviewStage.command("item replace block %d %d %d container.%d with %s", right.getX(), right.getY(), right.getZ(), i, ids.get(i)));
			} else if (i < 54) {
				PreviewStage.run(PreviewStage.command("item replace block %d %d %d container.%d with %s", left.getX(), left.getY(), left.getZ(), i - 27, ids.get(i)));
			} else if (i < 81) {
				PreviewStage.run(PreviewStage.command("item replace entity @s inventory.%d with %s", i - 54, ids.get(i)));
			}
		}
		PreviewStage.run(PreviewStage.command("tp @s %.2f %.2f %.2f facing %.2f %.2f %.2f",
				CHEST.getX() + 1.0, GROUND + 0.0, CHEST.getZ() + 2.5, CHEST.getX() + 1.0, GROUND + 0.5, CHEST.getZ() + 0.5));
	}

	/**
	 * A screen opens with the cursor in its middle, over a slot, and its tooltip would be in the picture. A window
	 * without focus - Xvfb's, with no window manager - is not moved by GLFW, so the move is handed to the game as
	 * GLFW would hand it, by the dev name of the handler's callback.
	 */
	private static void parkCursor() {
		Minecraft mc = Minecraft.getInstance();
		long window = mc.getWindow().getWindow();
		GLFW.glfwSetCursorPos(window, 1.0, 1.0);
		try {
			Method onMove = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
			onMove.setAccessible(true);
			onMove.invoke(mc.mouseHandler, window, 1.0, 1.0);
		} catch (ReflectiveOperationException e) {
			PreviewStage.say("showcase: could not move the cursor: " + e);
		}
	}

	/** What is cleared in the tick it is shot in is still in the frame being read, so it goes a few ticks before. */
	private static void shot(List<Runnable> steps, String name) {
		steps.add(() -> {
			// with a screen open the game draws the chat even with the GUI hidden
			Minecraft.getInstance().gui.getChat().clearMessages(false);
			Minecraft.getInstance().getToasts().clear();
		});
		PreviewStage.waitTicks(steps, 3);
		steps.add(() -> PreviewStage.shoot(name + ".png"));
	}
}
