package dev.namestyle.style;

import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public final class Palettes {
    private Palettes() {}

    public record Preset(String name, int[] colors) {}

    public static final String[] ICONS = {
        "", "\u2605", "\u2606", "\u2665", "\u2666", "\u2663", "\u2660", "\u2620",
        "\u26A1", "\u2600", "\u263E", "\u273F", "\u266A", "\u266B", "\u2726", "\u2727",
        "\u272A", "\u272F", "\u2714", "\u2716", "\u2691", "\u2764", "\u262F", "\u2744",
        "\u2622", "\u2623", "\u265B", "\u265A", "\u2655", "\u263A", "\u263B", "\u27A4",
        "\u00BB", "\u2708", "\u2602", "\u2694", "\u270E", "\u260E", "\u2702", "\u2693",
        "\u2756", "\u2601", "\u2698", "\u2740", "\u2731", "\u2604", "\u231B", "\u2618"
    };

    public static final int[] LEGACY = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    public static final Preset[] PRESETS = {
        new Preset("Inferno", new int[]{0xFF3B1F, 0xFFB000, 0xFFD400}),
        new Preset("Ocean", new int[]{0x00C6FF, 0x0072FF}),
        new Preset("Aurora", new int[]{0x00FF87, 0x00D4FF, 0x7B2FFF}),
        new Preset("Candy", new int[]{0xFF6EC4, 0x7873F5}),
        new Preset("Vaporwave", new int[]{0xFF71CE, 0x01CDFE, 0x05FFA1}),
        new Preset("Galaxy", new int[]{0x8E2DE2, 0x4A00E0, 0x00C6FF}),
        new Preset("Gold", new int[]{0xFFD700, 0xFFF4B0, 0xFFB400}),
        new Preset("Frost", new int[]{0xE0F7FF, 0x6EC6FF, 0x3A7BD5}),
        new Preset("Blood", new int[]{0x8B0000, 0xFF1A1A, 0xFF6B6B}),
        new Preset("Toxic", new int[]{0x39FF14, 0xF7FF00}),
        new Preset("Sakura", new int[]{0xFFB7D5, 0xFF6FA5}),
        new Preset("Lava", new int[]{0xFF0844, 0xFFB199}),
        new Preset("Mint", new int[]{0x00F260, 0x0575E6}),
        new Preset("Royal", new int[]{0x7F00FF, 0xE100FF}),
        new Preset("Peach", new int[]{0xFF9A8B, 0xFF6A88, 0xFF99AC}),
        new Preset("Silver", new int[]{0xFFFFFF, 0x7A7A7A}),
    };

    public static List<Integer> list(int[] a) {
        List<Integer> l = new ArrayList<>();
        for (int v : a) l.add(v);
        return l;
    }

    public static int lerp(int a, int b, double f) {
        f = Math.max(0, Math.min(1, f));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    /** Samples a multi-stop gradient. cyclic = the last stop blends back into the first. */
    public static int sample(List<Integer> st, double pos, boolean cyclic) {
        int k = st.size();
        if (k == 0) return 0xFFFFFF;
        if (k == 1) return st.get(0);
        int segs;
        double scaled;
        if (cyclic) {
            pos = pos - Math.floor(pos);
            segs = k;
            scaled = pos * k;
        } else {
            pos = Math.max(0, Math.min(1, pos));
            segs = k - 1;
            scaled = pos * (k - 1);
        }
        int idx = (int) Math.floor(scaled);
        if (idx >= segs) idx = segs - 1;
        double f = scaled - idx;
        return lerp(st.get(idx % k), st.get((idx + 1) % k), f);
    }

    public static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        s = Math.max(0f, Math.min(1f, s));
        v = Math.max(0f, Math.min(1f, v));
        return MathHelper.hsvToRgb(h, s, v) & 0xFFFFFF;
    }

    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min, h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0) h += 1f;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    public static int hueShift(int rgb, float delta) {
        float[] f = rgbToHsv(rgb);
        return hsv(f[0] + delta, f[1], f[2]);
    }
}
