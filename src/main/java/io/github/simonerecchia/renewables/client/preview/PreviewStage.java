package io.github.simonerecchia.renewables.client.preview;

import io.github.simonerecchia.renewables.api.Nameplate;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.catalog.TurbineCatalog;
import io.github.simonerecchia.renewables.registry.ModBlocks;

import io.github.simonerecchia.renewables.Renewables;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Renders a machine with the game's own renderer, from the angles worth looking at, straight to PNG. */
@OnlyIn(Dist.CLIENT) @Mod.EventBusSubscriber(modid = Renewables.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PreviewStage {
	/** Where the stage is built, well away from anything a player has made. */
	private static final int STAGE_X = 512;
	private static final int STAGE_Z = 512;
	private static final int STAGE_Y = 96;
	/** Half-width of the floor. */
	private static final int FLOOR = 10;

	/** The views, and why these ones. */
	record View(String name, float yaw, float pitch, double distance, double height) {
	}

	private static final List<View> VIEWS = List.of(
			new View("front", 0.0f, 8.0f, 7.0, 1.6),
			new View("side", 90.0f, 8.0f, 7.0, 1.6),
			new View("iso", 45.0f, 30.0f, 9.0, 5.0),
			new View("plan", 45.0f, 88.0f, 3.0, 13.0),
			new View("low", 20.0f, -4.0f, 5.0, 0.4));

	/** What is left to do */
	private static final Deque<Runnable> STEPS = new ArrayDeque<>();
	private static boolean armed = System.getProperty("renewables.preview") != null;
	/** Whether the unattended run has asked for its world yet. */
	private static boolean worldAsked;
	/** Whether a run has hidden the GUI and not shown it again yet. */
	private static boolean hidGui;

	private PreviewStage() {
	}

	@SubscribeEvent
	public static void onRegisterCommands(RegisterClientCommandsEvent event) {
		// a resource location argument rather than a string one, because brigadier's unquoted strings do
		// not allow a colon: /preview renewables:pv_tilt_530 would not have parsed at all
		LiteralArgumentBuilder<CommandSourceStack> command = LiteralArgumentBuilder.<CommandSourceStack>literal("preview")
				.then(Commands.argument("block", ResourceLocationArgument.id())
						.executes(context -> {
							queue(List.of(ResourceLocationArgument.getId(context, "block").toString()), false);
							return 1;
						})
						.then(Commands.literal("pair")
								.executes(context -> {
									queue(List.of(ResourceLocationArgument.getId(context, "block").toString()), true);
									return 1;
								})));

		event.getDispatcher().register(command);
	}

	/** Fires the unattended run, once */
	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			if (armed && !worldAsked && mc.screen instanceof TitleScreen) {
				worldAsked = true;
				// told rather than run: loading a world ticks the game itself, which is no thing to do inside a tick
				mc.tell(PreviewStage::createWorld);
			}
			return;
		}

		if (armed) {
			armed = false;
			String wanted = System.getProperty("renewables.preview", "");
			if ("showcase".equalsIgnoreCase(wanted)) {
				STEPS.addAll(Showcase.steps());
			} else {
				queue(subjects(wanted), true);
			}
			if (Boolean.getBoolean("renewables.preview.quit")) {
				// Screenshot.grab writes its file on another thread
				waitTicks(STEPS, 40);
				STEPS.add(() -> Minecraft.getInstance().stop());
			}
		}

		if (!STEPS.isEmpty()) STEPS.poll().run();
	}

	/**
	 * Leaving the world drops what is left of a run: the steps are commands for this world - a fill with air, a game
	 * mode, a game rule - and the next world with cheats on would otherwise run them.
	 */
	@SubscribeEvent
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		STEPS.clear();
		if (hidGui) {
			Minecraft.getInstance().options.hideGui = false;
			hidGui = false;
		}
	}

	/** The unattended run's own world: flat, creative and with cheats, which every step here is a command for. */
	private static void createWorld() {
		String name = "renewables-preview-" + System.currentTimeMillis();
		LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
				WorldDataConfiguration.DEFAULT);
		Minecraft.getInstance().createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(0L, false, false),
				registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
						.createWorldDimensions());
	}

	/** Which machines a property value names. */
	private static List<String> subjects(String wanted) {
		if (!"all".equalsIgnoreCase(wanted)) {
			return List.of(wanted.split(","));
		}

		List<String> found = new ArrayList<>();
		for (var entry : ModBlocks.BLOCKS.getEntries()) {
			String path = entry.getId().getPath();
			// the shell is invisible by design, and a tower on its own is a tube
			if (path.equals("machine_shell")) continue;

			found.add("renewables:" + path);
		}

		return found;
	}

	private static void queue(List<String> blocks, boolean pair) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		say(command("preview: %d machine(s), %d views each", blocks.size(), VIEWS.size()));
		STEPS.add(() -> {
			// one light, one sky, every time - a render that changes with the weather is not a comparison
			run("gamerule doDaylightCycle false");
			run("time set noon");
			run("weather clear");
			run("gamemode creative");
			hideGui(true);
		});

		for (String block : blocks) {
			String id = block.trim();
			if (id.isEmpty()) continue;

			STEPS.add(() -> stage(id, pair));
			// two ticks for the chunk to relight and the block entity renderers to pick the block up
			STEPS.add(PreviewStage::nothing);
			STEPS.add(PreviewStage::nothing);
			for (View view : VIEWS) {
				STEPS.add(() -> look(view));
				STEPS.add(PreviewStage::nothing);
				STEPS.add(() -> shoot(id, view));
			}
		}

		STEPS.add(() -> {
			hideGui(false);
			say("preview: done, in run/screenshots");
		});
	}

	/** Clears the stage, lays a floor */
	private static void stage(String block, boolean pair) {
		int x = STAGE_X;
		int y = STAGE_Y;
		int z = STAGE_Z;
		run(command("fill %d %d %d %d %d %d air", x - FLOOR, y, z - FLOOR, x + FLOOR, y + 24, z + FLOOR));
		run(command("fill %d %d %d %d %d %d minecraft:smooth_stone", x - FLOOR, y - 1, z - FLOOR, x + FLOOR, y - 1, z + FLOOR));

		if (needsTower(block)) {
			// a machine that needs a tower gets one
			run(command("fill %d %d %d %d %d %d renewables:turbine_tower", x, y, z, x, y + 5, z));
			run(command("setblock %d %d %d %s", x, y + 6, z, block));
		} else {
			run(command("setblock %d %d %d %s", x, y, z, block));
			if (pair) {
				// the row behind, and a run of cable in front: the two questions that are about the join
				run(command("setblock %d %d %d %s", x, y, z - 1, block));
				run(command("setblock %d %d %d renewables:dc_string_cable", x, y, z + 1));
				run(command("setblock %d %d %d renewables:dc_string_cable", x, y, z + 2));
			}
		}
	}

	/** Whether this block is a machine that will not seat without a tower under it. */
	private static boolean needsTower(String block) {
		for (TurbineSpec spec : TurbineCatalog.all()) {
			if (block.endsWith(":" + spec.id().getPath())) return true;
		}

		// the C130 keeps the original registry name, which is not a catalogue path
		return block.endsWith(":wind_turbine");
	}

	private static void look(View view) {
		run(teleport(view));
	}

	/** The command that puts the camera at a view. */
	static String teleport(View view) {
		double radians = Math.toRadians(view.yaw());
		double x = STAGE_X + 0.5 + Math.sin(radians) * view.distance();
		double z = STAGE_Z + 0.5 - Math.cos(radians) * view.distance();
		return command("tp @s %.2f %.2f %.2f %.1f %.1f", x, STAGE_Y + view.height(), z, view.yaw(), view.pitch());
	}

	/**
	 * A command, at the root locale: formatted at the system's, a decimal comma ("tp @s 512,50 ...") is a command the
	 * server refuses, and every view was shot from wherever the camera already was.
	 */
	static String command(String pattern, Object... values) {
		return Nameplate.fmt(pattern, values);
	}

	private static void shoot(String block, View view) {
		shoot(command("preview_%s_%s.png", block.replace(':', '_'), view.name()));
	}

	static void shoot(String file) {
		Minecraft mc = Minecraft.getInstance();
		// the game directory, not the screenshots directory: grab appends "screenshots" itself
		Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), message -> {
		});
	}

	static void hideGui(boolean hide) {
		Minecraft.getInstance().options.hideGui = hide;
		hidGui = hide;
	}

	/** A step a tick, so this many steps that do nothing. */
	static void waitTicks(Collection<Runnable> steps, int ticks) {
		for (int i = 0; i < ticks; i++) steps.add(PreviewStage::nothing);
	}

	private static void nothing() {
	}

	static void run(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.connection.sendCommand(command);
	}

	static void say(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.displayClientMessage(Component.literal(message), false);
	}
}
