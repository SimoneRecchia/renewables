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

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.comment("Renewables Client Configuration").push("client");
		PREVIEW_SNAPPING = builder
				.comment("If true, wire previews snap to the exact insulator positions.")
				.define("previewSnapping", false);
		builder.pop();
		SPEC = builder.build();
	}

	private ClientConfig() {
	}

	public static boolean previewSnapping() {
		return PREVIEW_SNAPPING.get();
	}
}
