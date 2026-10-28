package com.trmtgtnh.client.settings;

/**
 * The arithmetic of the path color's picker (0.9.228; spec M10).
 *
 * <p>
 * The picker is the editor (spec M17: no hex field on the row); a hex field inside it is the secondary way in, and it
 * reads {@code #RRGGBB} or {@code RRGGBB} in either case and writes {@code #RRGGBB} upper case. Anything else is
 * refused with its reason ({@link #parse}). Hue, saturation and value are each nought to one, so the picker's square
 * and bar map onto them directly; a grey or black color has no hue of its own, and the picker keeps the one it had
 * ({@link #rgbToHsv(int, float)}), so dragging the value down to black and back does not lose the color.
 *
 * <p>
 * {@link #mix} is the arithmetic the game draws the color with on a map (the block layer's {@code blendToward}), and
 * {@link #highlight} the whole of how a worn square is pulled toward it, so the picker's preview strip is drawn by the
 * game's own sums.
 */
public final class ColorMath {

    private ColorMath() {}

    /** What a typed hex color read as: its color, or why it was refused. */
    public static final class Parsed {

        /** The color, {@code 0xRRGGBB}; meaningless when {@link #refusal} is set. */
        public final int rgb;

        public final NumberWords.Refusal refusal;

        Parsed(int rgb, NumberWords.Refusal refusal) {
            this.rgb = rgb;
            this.refusal = refusal;
        }

        public boolean reads() {
            return refusal == null;
        }
    }

    /**
     * A typed color: six hex digits, a leading {@code #} or not, either case. A character that is not a hex digit is
     * refused by name ({@code color.refuse.char}); the wrong number of digits as {@code color.refuse.digits}.
     */
    public static Parsed parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        String digits = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        for (int at = 0; at < digits.length(); at++) {
            char one = digits.charAt(at);
            if (Character.digit(one, 16) < 0) {
                return new Parsed(0, new NumberWords.Refusal(SettingsKeys.COLOR_REFUSE_CHAR, String.valueOf(one)));
            }
        }
        if (digits.length() != 6) return new Parsed(0, new NumberWords.Refusal(SettingsKeys.COLOR_REFUSE_DIGITS, null));
        return new Parsed(Integer.parseInt(digits, 16), null);
    }

    /** A color as the file writes it: {@code #RRGGBB}, upper case. */
    public static String hex(int rgb) {
        String digits = Integer.toHexString(rgb & 0xFFFFFF)
            .toUpperCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder("#");
        for (int pad = digits.length(); pad < 6; pad++) out.append('0');
        return out.append(digits)
            .toString();
    }

    /** Hue, saturation and value, each nought to one; a color with no hue answers hue nought. */
    public static float[] rgbToHsv(int rgb) {
        return rgbToHsv(rgb, 0f);
    }

    /** Hue, saturation and value, keeping {@code hueIfNone} for a grey or black, which has no hue of its own. */
    public static float[] rgbToHsv(int rgb, float hueIfNone) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float v = max;
        float s = max <= 0f ? 0f : delta / max;
        float h;
        if (delta <= 0f || max <= 0f) {
            h = hueIfNone;
        } else if (max == r) {
            h = ((g - b) / delta) / 6f;
        } else if (max == g) {
            h = ((b - r) / delta + 2f) / 6f;
        } else {
            h = ((r - g) / delta + 4f) / 6f;
        }
        if (h < 0f) h += 1f;
        if (h >= 1f) h -= 1f;
        return new float[] { h, s, v };
    }

    /** A color from hue, saturation and value, each nought to one (a hue of one is red again). */
    public static int hsvToRgb(float h, float s, float v) {
        float hue = h - (float) Math.floor(h);
        float sat = clamp(s);
        float val = clamp(v);
        float sector = hue * 6f;
        int part = (int) Math.floor(sector);
        float f = sector - part;
        float p = val * (1f - sat);
        float q = val * (1f - sat * f);
        float t = val * (1f - sat * (1f - f));
        float r;
        float g;
        float b;
        switch (part % 6) {
            case 0:
                r = val;
                g = t;
                b = p;
                break;
            case 1:
                r = q;
                g = val;
                b = p;
                break;
            case 2:
                r = p;
                g = val;
                b = t;
                break;
            case 3:
                r = p;
                g = q;
                b = val;
                break;
            case 4:
                r = t;
                g = p;
                b = val;
                break;
            default:
                r = val;
                g = p;
                b = q;
                break;
        }
        return (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static float clamp(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }

    private static int channel(float value) {
        return Math.round(clamp(value) * 255f);
    }

    /**
     * One color pulled toward another by {@code strength}, nought to one, channel by channel and rounded: the game's
     * own
     * arithmetic for the path color ({@code GhostLogic.blendToward}).
     */
    public static int mix(int base, int color, float strength) {
        float amount = strength < 0f ? 0f : (strength > 1f ? 1f : strength);
        if (amount <= 0f) return base;
        int result = 0;
        for (int channel = 0; channel < 3; channel++) {
            int shift = 16 - channel * 8;
            int start = (base >>> shift) & 0xFF;
            int end = (color >>> shift) & 0xFF;
            int value = Math.round(start + (end - start) * amount);
            if (value < 0) value = 0;
            if (value > 255) value = 255;
            result |= value << shift;
        }
        return result;
    }

    /**
     * A worn square's map color pulled toward the path color as the game does it ({@code GhostLogic.highlightBy}): by
     * the square root of how worn it is, times the highlight setting, which counts no higher than one.
     */
    public static int highlight(int base, int color, float strength, float worn) {
        if (strength <= 0f) return base;
        float clamped = worn < 0f ? 0f : (worn > 1f ? 1f : worn);
        if (clamped <= 0f) return base;
        return mix(base, color, (float) Math.sqrt(clamped) * Math.min(strength, 1f));
    }
}
