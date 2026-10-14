package io.github.simonerecchia.renewables.core;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-only settings, stored in {@code config/Renewables/config.toml}.
 *
 * Registered from the client entry class and never from the main one, so a dedicated server does not even
 * create the file.
 */
public final class ClientConfig {
	public static final ForgeConfigSpec SPEC;

	private static final ForgeConfigSpec.BooleanValue PREVIEW_SNAPPING;
	private static final ForgeConfigSpec.IntValue WIRE_DRAW_DISTANCE;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.comment("Renewables Client Configuration").push("client");
		// on by default: the preview then starts and ends where the finished wire will, not at the middle of the porcelain
		PREVIEW_SNAPPING = builder
				.comment("If true, wire previews snap to the exact insulator positions.")
				.define("previewSnapping", true);
		WIRE_DRAW_DISTANCE = builder
				.comment("How far away, in blocks, a span of wire is still drawn. Lower it if long lines cost frames.")
				.defineInRange("wireDrawDistance", 128, 16, 512);
		builder.pop();
		SPEC = builder.build();
	}

	private ClientConfig() {
	}

	public static boolean previewSnapping() {
		return PREVIEW_SNAPPING.get();
	}

	/** Spans further than this from the camera are not drawn, in blocks. */
	public static int wireDrawDistance() {
		return WIRE_DRAW_DISTANCE.get();
	}
}
