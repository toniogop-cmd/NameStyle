package dev.namestyle.style;

import dev.namestyle.config.NSConfig;

import java.util.ArrayList;
import java.util.List;

public class StyleProfile {
    public enum Mode { SOLID, GRADIENT, RAINBOW }
    public enum Motion { FLOW, WAVE, PULSE, SHIMMER }
    public enum IconPos { PREFIX, SUFFIX, BOTH }

    public String name = "Inferno";
    public Mode mode = Mode.GRADIENT;
    public List<Integer> colors = new ArrayList<>(List.of(0xFF3B1F, 0xFFB000));
    public boolean bold, italic, underline, strike, obfuscated;
    public boolean animated = false;
    public Motion motion = Motion.FLOW;
    public float speed = 1f;
    public float spread = 1f;
    public float rainbowSat = 0.85f;
    public float rainbowVal = 1f;
    public String icon = "";
    public IconPos iconPos = IconPos.PREFIX;
    public int iconColor = 0xFF55FF;
    public boolean iconMatchName = false;
    public String nickname = "";

    public StyleProfile copy() {
        return NSConfig.GSON_COMPACT.fromJson(NSConfig.GSON_COMPACT.toJson(this), StyleProfile.class);
    }

    /** Sanitises anything loaded from disk or imported from the clipboard. */
    public StyleProfile fix() {
        if (name == null || name.isBlank()) name = "Profile";
        if (name.length() > 16) name = name.substring(0, 16);
        if (mode == null) mode = Mode.GRADIENT;
        if (motion == null) motion = Motion.FLOW;
        if (iconPos == null) iconPos = IconPos.PREFIX;
        colors = colors == null ? new ArrayList<>() : new ArrayList<>(colors);
        colors.replaceAll(c -> c == null ? 0xFFFFFF : (c & 0xFFFFFF));
        if (colors.isEmpty()) colors.add(0xFFFFFF);
        if (colors.size() > 5) colors = new ArrayList<>(colors.subList(0, 5));
        if (mode == Mode.GRADIENT && colors.size() < 2) colors.add(colors.get(0));
        speed = Math.max(0.1f, Math.min(5f, speed));
        spread = Math.max(0.25f, Math.min(4f, spread));
        rainbowSat = Math.max(0.2f, Math.min(1f, rainbowSat));
        rainbowVal = Math.max(0.4f, Math.min(1f, rainbowVal));
        if (icon == null) icon = "";
        if (icon.length() > 4) icon = icon.substring(0, 4);
        if (nickname == null) nickname = "";
        nickname = nickname.replace("\u00A7", "");
        if (nickname.length() > 16) nickname = nickname.substring(0, 16);
        iconColor &= 0xFFFFFF;
        return this;
    }
}
