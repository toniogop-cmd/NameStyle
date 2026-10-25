package dev.namestyle.style;

import dev.namestyle.config.NSConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.namestyle.style.StyleProfile.Mode;
import static dev.namestyle.style.StyleProfile.Motion;
import static dev.namestyle.style.StyleProfile.IconPos;

public final class NameRenderer {
    private NameRenderer() {}

    private static String patFor;
    private static Pattern pat;

    public static String realName() {
        try {
            return MinecraftClient.getInstance().getSession().getUsername();
        } catch (Throwable t) {
            return null;
        }
    }

    public static double now() {
        return (System.currentTimeMillis() % 7_200_000L) / 1000.0;
    }

    private static Pattern pattern(String name) {
        if (!name.equals(patFor)) {
            pat = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])");
            patFor = name;
        }
        return pat;
    }

    /** Colour (RGB) of glyph i out of n at time t (seconds). */
    public static int colorAt(StyleProfile p, int i, int n, double t) {
        double ph = n <= 0 ? 0 : i / (double) n;
        double pos = n <= 1 ? 0 : i / (double) (n - 1);
        double sp = p.speed * 0.25;
        boolean anim = p.animated;
        Motion mo = p.motion;
        int c;
        if (p.mode == Mode.SOLID) {
            c = p.colors.get(0);
        } else if (p.mode == Mode.GRADIENT) {
            if (anim && mo == Motion.FLOW) {
                c = Palettes.sample(p.colors, ph * p.spread - t * sp, true);
            } else if (anim && mo == Motion.WAVE) {
                double w = 0.5 + 0.5 * Math.sin(Math.PI * 2 * (ph * p.spread - t * sp * 1.5));
                c = Palettes.sample(p.colors, w, false);
            } else {
                c = Palettes.sample(p.colors, pos, false);
            }
        } else {
            double h;
            if (anim && mo == Motion.FLOW) {
                h = ph * p.spread - t * sp;
            } else if (anim && mo == Motion.WAVE) {
                h = (0.5 + 0.5 * Math.sin(Math.PI * 2 * (ph * p.spread - t * sp * 1.5))) * 0.8;
            } else {
                h = ph;
            }
            c = Palettes.hsv((float) h, p.rainbowSat, p.rainbowVal);
        }
        if (anim) {
            if (mo == Motion.PULSE) {
                double f = 0.5 + 0.5 * Math.sin(Math.PI * 2 * t * sp * 1.2);
                c = Palettes.lerp(0x000000, c, 0.4 + 0.6 * f);
            } else if (mo == Motion.SHIMMER) {
                double center = ((t * sp * 0.6) % 1.0) * 1.6 - 0.3;
                double k = Math.max(0, 1 - Math.abs(ph - center) / 0.2);
                c = Palettes.lerp(c, 0xFFFFFF, k * 0.9);
            }
        }
        return c & 0xFFFFFF;
    }

    private static Style iconStyle(StyleProfile p, int i, int n, double t) {
        int col = p.iconMatchName ? colorAt(p, i, n, t) : p.iconColor;
        Style s = Style.EMPTY.withColor(TextColor.fromRgb(col));
        if (p.bold) s = s.withBold(true);
        return s;
    }

    /** Builds the fully styled name (optionally with icon). base = style inherited from the surrounding text. */
    public static MutableText build(StyleProfile p, String shown, Style base, double t, boolean icons) {
        List<String> g = new ArrayList<>();
        shown.codePoints().forEach(cp -> g.add(new String(Character.toChars(cp))));
        int n = g.size();
        MutableText out = Text.empty();
        boolean ic = icons && p.icon != null && !p.icon.isEmpty();
        if (ic && p.iconPos != IconPos.SUFFIX) {
            out.append(Text.literal(p.icon).setStyle(iconStyle(p, 0, n, t).withParent(base)));
            out.append(Text.literal(" ").setStyle(base));
        }
        for (int i = 0; i < n; i++) {
            Style s = Style.EMPTY.withColor(TextColor.fromRgb(colorAt(p, i, n, t)));
            if (p.bold) s = s.withBold(true);
            if (p.italic) s = s.withItalic(true);
            if (p.underline) s = s.withUnderline(true);
            if (p.strike) s = s.withStrikethrough(true);
            if (p.obfuscated) s = s.withObfuscated(true);
            out.append(Text.literal(g.get(i)).setStyle(s.withParent(base)));
        }
        if (ic && p.iconPos != IconPos.PREFIX) {
            out.append(Text.literal(" ").setStyle(base));
            out.append(Text.literal(p.icon).setStyle(iconStyle(p, Math.max(0, n - 1), n, t).withParent(base)));
        }
        return out;
    }

    /** Replaces every occurrence of the local player's name inside any Text with the styled version. */
    public static Text restyle(Text original) {
        NSConfig cfg = NSConfig.I;
        if (!cfg.enabled || original == null) return original;
        String real = realName();
        if (real == null || real.isEmpty()) return original;
        if (!original.getString().contains(real)) return original;

        StyleProfile p = cfg.profile();
        String shown = p.nickname == null || p.nickname.isBlank() ? real : p.nickname.trim();
        Pattern pt = pattern(real);
        double t = now();
        MutableText out = Text.empty();
        boolean[] hit = {false};

        original.visit((style, str) -> {
            Matcher m = pt.matcher(str);
            int last = 0;
            while (m.find()) {
                hit[0] = true;
                if (m.start() > last) out.append(Text.literal(str.substring(last, m.start())).setStyle(style));
                out.append(build(p, shown, style, t, true));
                last = m.end();
            }
            if (last < str.length()) out.append(Text.literal(str.substring(last)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);

        return hit[0] ? out : original;
    }
}
