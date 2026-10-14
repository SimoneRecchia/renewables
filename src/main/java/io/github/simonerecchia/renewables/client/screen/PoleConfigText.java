package io.github.simonerecchia.renewables.client.screen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * How the pole configuration screen writes and reads its numbers.
 *
 * Always with a decimal point, whatever the language the game is played in: formatting degrees with the system locale
 * wrote "12,50" on a machine set to Italian, which the field could not read back, so a value rewritten by the player
 * was silently ignored.  A comma the player types is accepted as a point; nothing that is not a plain decimal number
 * is, which keeps "NaN", "Infinity", hex floats and a trailing "f" out.  No Minecraft types.
 */
public final class PoleConfigText {
	private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

	private PoleConfigText() {
	}

	/** An angle in radians, shown in degrees with two decimals. */
	public static String degrees(float radians) {
		return String.format(Locale.ROOT, "%.2f", Math.toDegrees(radians));
	}

	/** An offset in blocks: as many decimals as it has up to four, never an exponent. */
	public static String offset(float blocks) {
		if (!Float.isFinite(blocks)) return "0";

		String text = BigDecimal.valueOf(blocks).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
		return text.equals("-0") ? "0" : text;
	}

	/** The number a field holds, or null if what is typed is not a finite decimal number. */
	@Nullable
	public static Float parse(String text) {
		String trimmed = text.trim().replace(',', '.');
		if (!NUMBER.matcher(trimmed).matches()) return null;

		try {
			float value = Float.parseFloat(trimmed);
			return Float.isFinite(value) ? value : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Whether a character may be typed into a field: digits, a sign, a point or a comma, an exponent. */
	public static boolean allowed(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (!(c >= '0' && c <= '9') && c != '-' && c != '+' && c != '.' && c != ',' && c != 'e' && c != 'E') return false;
		}

		return true;
	}

	public static float clamp(float value, float limit) {
		return Math.max(-limit, Math.min(limit, value));
	}
}
