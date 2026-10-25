package dev.namestyle.gui;

import dev.namestyle.NameStyleMod;
import dev.namestyle.config.NSConfig;
import dev.namestyle.style.NameRenderer;
import dev.namestyle.style.Palettes;
import dev.namestyle.style.StyleProfile;
import dev.namestyle.style.StyleProfile.IconPos;
import dev.namestyle.style.StyleProfile.Mode;
import dev.namestyle.style.StyleProfile.Motion;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.DoubleToIntFunction;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/** Immediate-mode editor. Everything is drawn in a fixed 520x320 logical space that scales down on small windows. */
public class NameStyleScreen extends Screen {
    private static final int W = 520, H = 320;
    private static final int BG = 0xFF0D1017, PANEL = 0xFF131826, PANEL2 = 0xFF1B2133, LINE = 0xFF2B3452,
        TXT = 0xFFE8EBF5, DIM = 0xFF8089A6, ACC = 0xFF5B8CFF, ACC_D = 0xFF2B4580, GOOD = 0xFF4ADE80, BAD = 0xFFF87171;
    private static final String[] TABS = {"Colors", "Text & Icon", "Motion", "Settings"};
    private static final String[] BG_NAMES = {"Night", "Sky", "Grass", "Light"};
    private static final StyleProfile TITLE = makeTitle();

    private final Screen parent;
    private final String snapshot;
    private final Box hexBox, iconBox, iconHexBox, nickBox, nameBox;
    private final Box[] boxes;
    private final List<Hit> hits = new ArrayList<>();
    private Hit dragging;
    private double ox, oy, s = 1, mx, my;
    private int tab = 0, previewBg = 0, selStop = 0, lastRgb = -1, profileScroll = 0;
    private float hh, ss = 1, vv = 1;
    private String status = "";
    private int statusCol = TXT;
    private long statusUntil;
    private String hoverPreset = null;

    public NameStyleScreen(Screen parent) {
        super(Text.literal("NameStyle"));
        this.parent = parent;
        this.snapshot = NSConfig.GSON.toJson(NSConfig.I);
        IntPredicate printable = ch -> ch >= 32 && ch != 127 && ch != 0xA7;
        hexBox = new Box(7, ch -> ch == '#' || Character.digit(ch, 16) >= 0,
            () -> "#" + hex(stopColor(NSConfig.I.profile())),
            v -> {
                Integer rgb = parseHex(v);
                if (rgb != null) {
                    setStop(NSConfig.I.profile(), rgb);
                    lastRgb = -1;
                }
            });
        iconHexBox = new Box(7, ch -> ch == '#' || Character.digit(ch, 16) >= 0,
            () -> "#" + hex(NSConfig.I.profile().iconColor),
            v -> {
                Integer rgb = parseHex(v);
                if (rgb != null) NSConfig.I.profile().iconColor = rgb;
            });
        iconBox = new Box(4, printable, () -> NSConfig.I.profile().icon, v -> NSConfig.I.profile().icon = v);
        nickBox = new Box(16, printable, () -> NSConfig.I.profile().nickname, v -> NSConfig.I.profile().nickname = v);
        nameBox = new Box(16, printable, () -> NSConfig.I.profile().name, v -> NSConfig.I.profile().name = v.isBlank() ? "Profile" : v);
        boxes = new Box[]{hexBox, iconBox, iconHexBox, nickBox, nameBox};
    }

    private static StyleProfile makeTitle() {
        StyleProfile p = new StyleProfile();
        p.colors = new ArrayList<>(List.of(0x5B8CFF, 0xB07CFF, 0xFF7AC8));
        p.bold = true;
        p.animated = true;
        p.speed = 1.2f;
        return p;
    }

    // ------------------------------------------------------------------ infrastructure

    private static final class Hit {
        final int x, y, w, h;
        final IntConsumer click;
        final DragFn drag;
        Hit(int x, int y, int w, int h, IntConsumer click, DragFn drag) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.click = click; this.drag = drag;
        }
    }

    private interface DragFn { void on(double lx, double ly); }

    private static final class Box {
        final int max;
        final IntPredicate ok;
        final Supplier<String> src;
        final Consumer<String> change;
        String text = "";
        boolean focus;
        Box(int max, IntPredicate ok, Supplier<String> src, Consumer<String> change) {
            this.max = max; this.ok = ok; this.src = src; this.change = change;
        }
        void sync() { if (!focus) text = src.get(); }
        void type(char ch) {
            if (!ok.test(ch) || text.length() >= max) return;
            text += ch;
            change.accept(text);
        }
        void back(boolean all) {
            if (text.isEmpty()) return;
            text = all ? "" : text.substring(0, text.length() - 1);
            change.accept(text);
        }
        void paste(String clip) {
            if (clip == null) return;
            for (char ch : clip.toCharArray()) {
                if (ch == '\n' || ch == '\r') break;
                type(ch);
            }
        }
    }

    private void addHit(int x, int y, int w, int h, IntConsumer click) { hits.add(new Hit(x, y, w, h, click, null)); }
    private void addDrag(int x, int y, int w, int h, DragFn d) { hits.add(new Hit(x, y, w, h, null, d)); }
    private boolean over(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static Text lit(String s) { return Text.literal(s); }
    private static String hex(int rgb) { return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF); }

    private static Integer parseHex(String v) {
        if (v.startsWith("#")) v = v.substring(1);
        if (v.length() != 6) return null;
        try { return Integer.parseInt(v, 16); } catch (NumberFormatException e) { return null; }
    }

    private Box focused() {
        for (Box b : boxes) if (b.focus) return b;
        return null;
    }

    private void say(String msg, int col) {
        status = msg; statusCol = col; statusUntil = System.currentTimeMillis() + 4000;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Box b : boxes) b.focus = false;
        double lx = (mouseX - ox) / s, ly = (mouseY - oy) / s;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (lx >= h.x && lx < h.x + h.w && ly >= h.y && ly < h.y + h.h) {
                if (h.click != null) h.click.accept(button);
                if (h.drag != null && button == 0) {
                    dragging = h;
                    h.drag.on(lx, ly);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging != null && button == 0) {
            dragging.drag.on((mouseX - ox) / s, (mouseY - oy) / s);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double lx = (mouseX - ox) / s, ly = (mouseY - oy) / s;
        if (lx >= 8 && lx < 104 && ly >= 30 && ly < 292) {
            profileScroll -= (int) Math.signum(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Box f = focused();
        if (f != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                f.focus = false;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                f.back(hasControlDown());
            } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
                f.back(true);
            } else if (keyCode == GLFW.GLFW_KEY_V && hasControlDown()) {
                f.paste(client.keyboard.getClipboard());
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        Box f = focused();
        if (f != null) {
            f.type(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() { client.setScreen(parent); }

    @Override
    public void removed() { NSConfig.save(); }

    private void cancel() {
        NSConfig.I = NSConfig.GSON.fromJson(snapshot, NSConfig.class);
        NSConfig.I.profile();
        NSConfig.I.profiles.forEach(StyleProfile::fix);
        client.setScreen(parent);
    }

    // ------------------------------------------------------------------ primitives

    private void rrect(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x + 1, y, x + w - 1, y + h, col);
        c.fill(x, y + 1, x + 1, y + h - 1, col);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, col);
    }

    private void card(DrawContext c, int x, int y, int w, int h, int fill, int border) {
        rrect(c, x, y, w, h, border);
        rrect(c, x + 1, y + 1, w - 2, h - 2, fill);
    }

    private void text(DrawContext c, String str, int x, int y, int col) {
        c.drawText(textRenderer, str, x, y, col, col != DIM);
    }

    private void hGrad(DrawContext c, int x, int y, int w, int h, DoubleToIntFunction f) {
        for (int i = 0; i < w; i += 2) {
            int x2 = Math.min(i + 2, w);
            c.fill(x + i, y, x + x2, y + h, 0xFF000000 | f.applyAsInt((i + 1) / (double) w));
        }
    }

    private void button(DrawContext c, int x, int y, int w, int h, Text label, boolean active, Runnable r) {
        boolean hov = over(x, y, w, h);
        card(c, x, y, w, h, active ? ACC_D : hov ? 0xFF283250 : PANEL2, active ? ACC : hov ? 0xFF3C4A75 : LINE);
        int tw = textRenderer.getWidth(label);
        c.drawText(textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2 + 1, active ? 0xFFFFFFFF : TXT, true);
        addHit(x, y, w, h, b -> { if (b == 0) r.run(); });
    }

    private void segmented(DrawContext c, int x, int y, int segW, int h, String[] labels, int sel, IntConsumer set) {
        for (int i = 0; i < labels.length; i++) {
            final int ii = i;
            button(c, x + i * (segW + 2), y, segW, h, lit(labels[i]), i == sel, () -> set.accept(ii));
        }
    }

    private void toggle(DrawContext c, int x, int y, String label, boolean v, Consumer<Boolean> set) {
        card(c, x, y, 22, 11, v ? ACC_D : PANEL2, v ? ACC : LINE);
        c.fill(x + (v ? 12 : 2), y + 2, x + (v ? 20 : 10), y + 9, v ? 0xFFFFFFFF : 0xFF7A84A3);
        text(c, label, x + 28, y + 2, v ? TXT : DIM);
        addHit(x, y, 28 + textRenderer.getWidth(label), 11, b -> set.accept(!v));
    }

    private void slider(DrawContext c, int x, int y, int w, String label, float min, float max, float val, Consumer<Float> set) {
        text(c, label, x, y, TXT);
        String v = String.format(Locale.ROOT, "%.2f", val);
        text(c, v, x + w - textRenderer.getWidth(v), y, DIM);
        float f = Math.max(0f, Math.min(1f, (val - min) / (max - min)));
        int ty = y + 13;
        card(c, x, ty, w, 5, 0xFF0B0F18, LINE);
        int fw = (int) ((w - 2) * f);
        if (fw > 0) c.fill(x + 1, ty + 1, x + 1 + fw, ty + 4, ACC);
        card(c, x + 1 + fw - 3, ty - 3, 7, 11, 0xFFFFFFFF, ACC);
        addDrag(x - 4, ty - 4, w + 8, 13, (lx, ly) -> {
            float g = (float) Math.max(0, Math.min(1, (lx - x) / w));
            set.accept(min + (max - min) * g);
        });
    }

    private void box(DrawContext c, Box b, int x, int y, int w, int h, String placeholder) {
        b.sync();
        card(c, x, y, w, h, 0xFF0A0E16, b.focus ? ACC : LINE);
        String shown = b.text;
        while (!shown.isEmpty() && textRenderer.getWidth(shown) > w - 8) shown = shown.substring(1);
        int ty = y + (h - 8) / 2 + 1;
        if (b.text.isEmpty() && !b.focus) text(c, placeholder, x + 4, ty, 0xFF566080);
        else text(c, shown, x + 4, ty, TXT);
        if (b.focus && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = x + 4 + textRenderer.getWidth(shown);
            c.fill(cx, y + 3, cx + 1, y + h - 3, TXT);
        }
        addHit(x, y, w, h, btn -> b.focus = true);
    }

    // ------------------------------------------------------------------ helpers on profile state

    private int stopColor(StyleProfile p) {
        return p.colors.get(Math.max(0, Math.min(selStop, p.colors.size() - 1)));
    }

    private void setStop(StyleProfile p, int rgb) {
        p.colors.set(Math.max(0, Math.min(selStop, p.colors.size() - 1)), rgb & 0xFFFFFF);
    }

    private void syncHsv(StyleProfile p) {
        int cur = stopColor(p);
        if (cur != lastRgb) {
            float[] f = Palettes.rgbToHsv(cur);
            if (f[1] > 0.001f && f[2] > 0.001f) hh = f[0];
            ss = f[1];
            vv = f[2];
            lastRgb = cur;
        }
    }

    private void commitHsv(StyleProfile p) {
        int rgb = MathHelper.hsvToRgb(hh, ss, vv) & 0xFFFFFF;
        setStop(p, rgb);
        lastRgb = rgb;
    }

    private void setMode(StyleProfile p, Mode m) {
        p.mode = m;
        selStop = 0;
        lastRgb = -1;
        if (m == Mode.GRADIENT && p.colors.size() < 2) p.colors.add(Palettes.hueShift(p.colors.get(0), 0.12f));
    }

    private void randomize(StyleProfile p) {
        Random r = new Random();
        float h0 = r.nextFloat();
        int n = 2 + r.nextInt(2);
        p.mode = Mode.GRADIENT;
        p.colors = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float h = (h0 + i * (0.08f + r.nextFloat() * 0.25f)) % 1f;
            p.colors.add(Palettes.hsv(h, 0.55f + 0.45f * r.nextFloat(), 0.9f + 0.1f * r.nextFloat()));
        }
        selStop = 0;
        lastRgb = -1;
    }

    private static int previewColor(StyleProfile p, double f) {
        if (p.mode == Mode.RAINBOW) return Palettes.hsv((float) (f * 0.999), p.rainbowSat, p.rainbowVal);
        if (p.mode == Mode.SOLID) return p.colors.get(0);
        return Palettes.sample(p.colors, f, false);
    }

    private static String shownName(StyleProfile p) {
        if (p.nickname != null && !p.nickname.isBlank()) return p.nickname.trim();
        String real = NameRenderer.realName();
        return real == null || real.isEmpty() ? "Steve" : real;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fill(0, 0, width, height, 0xB0050710);
        s = Math.min(1.0, Math.min((width - 12) / (double) W, (height - 12) / (double) H));
        ox = (width - W * s) / 2.0;
        oy = (height - H * s) / 2.0;
        mx = (mouseX - ox) / s;
        my = (mouseY - oy) / s;
        hits.clear();
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate((float) ox, (float) oy, 0f);
        m.scale((float) s, (float) s, 1f);
        draw(c);
        m.pop();
    }

    private void draw(DrawContext c) {
        NSConfig cfg = NSConfig.I;
        StyleProfile p = cfg.profile();
        double t = NameRenderer.now();

        card(c, 0, 0, W, H, BG, LINE);
        hGrad(c, 3, 1, W - 6, 2, f -> previewColor(p, f));
        c.drawText(textRenderer, NameRenderer.build(TITLE, "NameStyle", Style.EMPTY, t, false), 12, 9, 0xFFFFFFFF, true);
        text(c, "client-side name styler", 84, 10, DIM);
        toggle(c, W - 118, 9, "Enabled", cfg.enabled, v -> cfg.enabled = v);
        button(c, W - 24, 6, 16, 14, lit("X"), false, this::close);

        drawSidebar(c, cfg, t);
        drawPreview(c, p, cfg, t);

        for (int i = 0; i < TABS.length; i++) {
            final int ii = i;
            button(c, 112 + i * 101, 98, 97, 16, lit(TABS[i]), tab == i, () -> tab = ii);
        }
        card(c, 112, 120, 400, 172, PANEL, LINE);
        int x0 = 122, y0 = 129;
        switch (tab) {
            case 0 -> tabColors(c, p, x0, y0);
            case 1 -> tabText(c, p, x0, y0, t);
            case 2 -> tabMotion(c, p, x0, y0);
            default -> tabSettings(c, cfg, p, x0, y0);
        }

        // footer
        text(c, "NameStyle 1.0  -  only you can see this", 12, 303, 0xFF566080);
        if (System.currentTimeMillis() < statusUntil) text(c, status, 112, 303, statusCol);
        button(c, 330, 298, 52, 16, lit("Reset"), false, () -> {
            StyleProfile fresh = new StyleProfile();
            fresh.name = p.name;
            cfg.profiles.set(cfg.active, fresh);
            selStop = 0;
            lastRgb = -1;
        });
        button(c, 386, 298, 52, 16, lit("Cancel"), false, this::cancel);
        button(c, 442, 298, 70, 16, lit("Save & Close"), true, this::close);
    }

    private void drawSidebar(DrawContext c, NSConfig cfg, double t) {
        card(c, 8, 30, 96, 262, PANEL, LINE);
        text(c, "PROFILES", 14, 36, DIM);
        int rows = 12;
        profileScroll = Math.max(0, Math.min(profileScroll, Math.max(0, cfg.profiles.size() - rows)));
        for (int r = 0; r < rows; r++) {
            int idx = profileScroll + r;
            if (idx >= cfg.profiles.size()) break;
            int y = 48 + r * 17;
            boolean sel = idx == cfg.active, hov = over(12, y, 88, 15);
            rrect(c, 12, y, 88, 15, sel ? 0xFF22305A : hov ? 0xFF1F2740 : PANEL2);
            if (sel) c.fill(12, y + 3, 14, y + 12, ACC);
            StyleProfile sp = cfg.profiles.get(idx);
            String nm = sp.name.length() > 11 ? sp.name.substring(0, 11) : sp.name;
            c.drawText(textRenderer, NameRenderer.build(sp, nm, Style.EMPTY, t, false), 18, y + 4, 0xFFFFFFFF, true);
            final int ii = idx;
            addHit(12, y, 88, 15, b -> { cfg.active = ii; selStop = 0; lastRgb = -1; });
        }
        button(c, 12, 272, 28, 16, lit("New"), false, () -> {
            if (cfg.profiles.size() >= 24) { say("Profile limit reached (24)", BAD); return; }
            StyleProfile n = new StyleProfile();
            n.name = "Profile " + (cfg.profiles.size() + 1);
            cfg.profiles.add(n);
            cfg.active = cfg.profiles.size() - 1;
            selStop = 0; lastRgb = -1;
        });
        button(c, 42, 272, 30, 16, lit("Copy"), false, () -> {
            if (cfg.profiles.size() >= 24) { say("Profile limit reached (24)", BAD); return; }
            StyleProfile n = cfg.profile().copy();
            n.name = (n.name.length() > 12 ? n.name.substring(0, 12) : n.name) + " 2";
            cfg.profiles.add(n);
            cfg.active = cfg.profiles.size() - 1;
        });
        button(c, 74, 272, 26, 16, lit("Del"), false, () -> {
            if (cfg.profiles.size() <= 1) { say("Can't delete the last profile", BAD); return; }
            cfg.profiles.remove(cfg.active);
            cfg.active = Math.max(0, cfg.active - 1);
            selStop = 0; lastRgb = -1;
        });
    }

    private void drawPreview(DrawContext c, StyleProfile p, NSConfig cfg, double t) {
        int x = 112, y = 30, w = 400, h = 62;
        card(c, x, y, w, h, PANEL, LINE);
        int ix = x + 2, iy = y + 2, iw = w - 4, ih = h - 4;
        switch (previewBg) {
            case 0 -> c.fillGradient(ix, iy, ix + iw, iy + ih, 0xFF090C13, 0xFF141B2C);
            case 1 -> c.fillGradient(ix, iy, ix + iw, iy + ih, 0xFF5E9BEA, 0xFFBFDDF7);
            case 2 -> c.fillGradient(ix, iy, ix + iw, iy + ih, 0xFF4A9A38, 0xFF2E6B22);
            default -> c.fill(ix, iy, ix + iw, iy + ih, 0xFFD9DCE3);
        }
        String shown = shownName(p);
        MutableText nm = NameRenderer.build(p, shown, Style.EMPTY, t, true);
        int tw = textRenderer.getWidth(nm);
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + 7f, 0f);
        m.scale(2f, 2f, 1f);
        c.fill(-tw / 2 - 3, -2, tw / 2 + 3, 10, 0x66000000);
        c.drawText(textRenderer, nm, -tw / 2, 0, 0xFFFFFFFF, true);
        m.pop();

        int cy = y + 34;
        c.fill(x + 6, cy - 1, x + w - 6, cy + 9, 0x77000000);
        MutableText chat = Text.literal("<").append(NameRenderer.build(p, shown, Style.EMPTY, t, true)).append(Text.literal("> gg, nice base"));
        c.drawText(textRenderer, chat, x + 9, cy, 0xFFFFFFFF, true);
        text(c, "CHAT", x + w - 36, cy, DIM);

        int ty = y + 46;
        c.fill(x + 6, ty - 1, x + w - 6, ty + 9, 0x77000000);
        MutableText tabT = NameRenderer.build(p, shown, Style.EMPTY, t, true);
        c.drawText(textRenderer, tabT, x + w / 2 - textRenderer.getWidth(tabT) / 2, ty, 0xFFFFFFFF, true);
        text(c, "TAB", x + w - 30, ty, DIM);

        button(c, x + w - 62, y + 4, 56, 12, lit("BG: " + BG_NAMES[previewBg]), false, () -> previewBg = (previewBg + 1) % 4);
        if (!cfg.enabled) text(c, "Mod is disabled - preview only", x + 8, y + 5, BAD);
    }

    // ------------------------------------------------------------------ tabs

    private void tabColors(DrawContext c, StyleProfile p, int x0, int y0) {
        segmented(c, x0, y0, 66, 14, new String[]{"Solid", "Gradient", "Rainbow"}, p.mode.ordinal(), i -> setMode(p, Mode.values()[i]));
        if (p.mode == Mode.GRADIENT) button(c, x0 + 268, y0, 54, 14, lit("Reverse"), false, () -> Collections.reverse(p.colors));
        button(c, x0 + 326, y0, 54, 14, lit("Random"), false, () -> randomize(p));

        if (p.mode == Mode.RAINBOW) {
            slider(c, x0, y0 + 26, 260, "Saturation", 0.2f, 1f, p.rainbowSat, v -> p.rainbowSat = v);
            slider(c, x0, y0 + 56, 260, "Brightness", 0.4f, 1f, p.rainbowVal, v -> p.rainbowVal = v);
            card(c, x0, y0 + 92, 262, 16, LINE, LINE);
            hGrad(c, x0 + 1, y0 + 93, 260, 14, f -> Palettes.hsv((float) (f * 0.999), p.rainbowSat, p.rainbowVal));
            text(c, "Tip: turn on Motion > Animated for a flowing rainbow.", x0, y0 + 116, DIM);
            return;
        }

        int n = p.mode == Mode.SOLID ? 1 : p.colors.size();
        selStop = Math.max(0, Math.min(selStop, n - 1));
        for (int i = 0; i < n; i++) {
            final int ii = i;
            int cx = x0 + i * 24;
            card(c, cx, y0 + 18, 20, 14, 0xFF000000 | p.colors.get(i), i == selStop ? 0xFFFFFFFF : LINE);
            addHit(cx, y0 + 18, 20, 14, b -> { selStop = ii; lastRgb = -1; });
        }
        if (p.mode == Mode.GRADIENT) {
            int bx = x0 + n * 24;
            if (n < 5) {
                button(c, bx, y0 + 18, 16, 14, lit("+"), false, () -> {
                    p.colors.add(Palettes.hueShift(p.colors.get(p.colors.size() - 1), 0.1f));
                    selStop = p.colors.size() - 1;
                    lastRgb = -1;
                });
                bx += 20;
            }
            if (n > 2) {
                button(c, bx, y0 + 18, 16, 14, lit("-"), false, () -> {
                    p.colors.remove(selStop);
                    selStop = Math.max(0, selStop - 1);
                    lastRgb = -1;
                });
            }
        }

        // colour picker
        int px = x0, py = y0 + 40, pw = 140, ph = 80;
        syncHsv(p);
        for (int i = 0; i < pw; i += 2) {
            float sat = i / (float) (pw - 1);
            c.fillGradient(px + i, py, px + Math.min(i + 2, pw), py + ph, 0xFF000000 | MathHelper.hsvToRgb(hh, sat, 1f), 0xFF000000);
        }
        card(c, px - 1, py - 1, pw + 2, ph + 2, 0x00000000, LINE);
        int mxk = px + (int) (ss * (pw - 1)), myk = py + (int) ((1 - vv) * (ph - 1));
        c.fill(mxk - 3, myk - 3, mxk + 4, myk + 4, 0xFF000000);
        c.fill(mxk - 2, myk - 2, mxk + 3, myk + 3, 0xFFFFFFFF);
        c.fill(mxk - 1, myk - 1, mxk + 2, myk + 2, 0xFF000000 | MathHelper.hsvToRgb(hh, ss, vv));
        addDrag(px, py, pw, ph, (lx, ly) -> {
            ss = (float) Math.max(0, Math.min(1, (lx - px) / (pw - 1)));
            vv = 1f - (float) Math.max(0, Math.min(1, (ly - py) / (ph - 1)));
            commitHsv(p);
        });

        int hx = px + 148;
        for (int i = 0; i < 6; i++) {
            int ya = py + i * ph / 6, yb = py + (i + 1) * ph / 6;
            c.fillGradient(hx, ya, hx + 10, yb, 0xFF000000 | MathHelper.hsvToRgb(i / 6f, 1f, 1f), 0xFF000000 | MathHelper.hsvToRgb((i + 1) / 6f, 1f, 1f));
        }
        int hy = py + (int) (hh * (ph - 1));
        c.fill(hx - 2, hy - 1, hx + 12, hy + 2, 0xFF000000);
        c.fill(hx - 1, hy, hx + 11, hy + 1, 0xFFFFFFFF);
        addDrag(hx - 2, py, 14, ph, (lx, ly) -> {
            hh = (float) Math.max(0, Math.min(0.999, (ly - py) / (ph - 1)));
            commitHsv(p);
        });

        box(c, hexBox, px + 166, py, 62, 14, "#RRGGBB");
        int cur = stopColor(p);
        card(c, px + 166, py + 20, 62, 22, 0xFF000000 | cur, LINE);
        text(c, "R " + ((cur >> 16) & 255), px + 166, py + 50, DIM);
        text(c, "G " + ((cur >> 8) & 255), px + 166, py + 60, DIM);
        text(c, "B " + (cur & 255), px + 166, py + 70, DIM);

        // right side: presets (gradient) or hint (solid)
        int rx = x0 + 236;
        if (p.mode == Mode.GRADIENT) {
            hoverPreset = null;
            for (int i = 0; i < Palettes.PRESETS.length; i++) {
                Palettes.Preset pr = Palettes.PRESETS[i];
                int gx = rx + (i % 4) * 36, gy = py + (i / 4) * 20;
                boolean hov = over(gx, gy, 34, 16);
                if (hov) hoverPreset = pr.name();
                card(c, gx, gy, 34, 16, LINE, hov ? 0xFFFFFFFF : LINE);
                List<Integer> list = Palettes.list(pr.colors());
                hGrad(c, gx + 1, gy + 1, 32, 14, f -> Palettes.sample(list, f, false));
                addHit(gx, gy, 34, 16, b -> {
                    p.mode = Mode.GRADIENT;
                    p.colors = new ArrayList<>(list);
                    selStop = 0;
                    lastRgb = -1;
                });
            }
            text(c, hoverPreset != null ? hoverPreset : "PRESETS", rx, y0 + 22, hoverPreset != null ? TXT : DIM);
        } else {
            text(c, "One color for the whole name.", rx, py + 4, DIM);
            text(c, "Switch to Gradient to blend", rx, py + 20, DIM);
            text(c, "up to 5 colors, or Rainbow for", rx, py + 30, DIM);
            text(c, "a full hue cycle.", rx, py + 40, DIM);
        }

        text(c, "MINECRAFT COLORS", x0, y0 + 124, DIM);
        for (int i = 0; i < Palettes.LEGACY.length; i++) {
            final int rgb = Palettes.LEGACY[i];
            int cx = x0 + i * 17;
            boolean hov = over(cx, y0 + 134, 14, 14);
            card(c, cx, y0 + 134, 14, 14, 0xFF000000 | rgb, hov ? 0xFFFFFFFF : LINE);
            addHit(cx, y0 + 134, 14, 14, b -> { setStop(p, rgb); lastRgb = -1; });
        }
    }

    private void tabText(DrawContext c, StyleProfile p, int x0, int y0, double t) {
        String[] names = {"Bold", "Italic", "Underline", "Strike", "Obfuscated"};
        boolean[] on = {p.bold, p.italic, p.underline, p.strike, p.obfuscated};
        for (int i = 0; i < 5; i++) {
            final int ii = i;
            Style st = Style.EMPTY.withBold(i == 0).withItalic(i == 1).withUnderline(i == 2).withStrikethrough(i == 3).withObfuscated(i == 4);
            button(c, x0 + i * 76, y0, 72, 15, Text.literal(names[i]).setStyle(st), on[i], () -> {
                switch (ii) {
                    case 0 -> p.bold = !p.bold;
                    case 1 -> p.italic = !p.italic;
                    case 2 -> p.underline = !p.underline;
                    case 3 -> p.strike = !p.strike;
                    default -> p.obfuscated = !p.obfuscated;
                }
            });
        }

        text(c, "ICON", x0, y0 + 26, DIM);
        segmented(c, x0 + 224, y0 + 21, 52, 13, new String[]{"Prefix", "Suffix", "Both"}, p.iconPos.ordinal(), i -> p.iconPos = IconPos.values()[i]);

        for (int i = 0; i < Palettes.ICONS.length; i++) {
            final int ii = i;
            String g = Palettes.ICONS[i];
            int cx = x0 + (i % 16) * 23, cy = y0 + 38 + (i / 16) * 19;
            boolean sel = Objects.equals(p.icon, g), hov = over(cx, cy, 21, 17);
            rrect(c, cx, cy, 21, 17, sel ? ACC_D : hov ? 0xFF283250 : PANEL2);
            if (sel) card(c, cx, cy, 21, 17, ACC_D, ACC);
            String shown = i == 0 ? "-" : g;
            int col = i == 0 ? DIM : 0xFF000000 | p.iconColor;
            c.drawText(textRenderer, shown, cx + (21 - textRenderer.getWidth(shown)) / 2, cy + 5, col, true);
            addHit(cx, cy, 21, 17, b -> p.icon = Palettes.ICONS[ii]);
        }

        text(c, "Custom", x0, y0 + 101, DIM);
        box(c, iconBox, x0 + 42, y0 + 97, 56, 14, "any char");
        text(c, "Color", x0 + 108, y0 + 101, DIM);
        box(c, iconHexBox, x0 + 140, y0 + 97, 60, 14, "#RRGGBB");
        card(c, x0 + 204, y0 + 97, 14, 14, 0xFF000000 | p.iconColor, LINE);
        toggle(c, x0 + 230, y0 + 99, "Match name color", p.iconMatchName, v -> p.iconMatchName = v);

        text(c, "NICKNAME", x0, y0 + 126, DIM);
        box(c, nickBox, x0 + 60, y0 + 122, 130, 14, "(your real name)");
        text(c, "Visual only. Never sent to the server.", x0 + 200, y0 + 126, DIM);
    }

    private static final String[] MOTION_DESC = {
        "Colors slide smoothly along your name (Gradient / Rainbow).",
        "A soft wave of color ripples through the letters (Gradient / Rainbow).",
        "The whole name breathes bright and dim (any mode).",
        "A glint of light sweeps across the name (any mode)."
    };

    private void tabMotion(DrawContext c, StyleProfile p, int x0, int y0) {
        toggle(c, x0, y0 + 1, "Animated", p.animated, v -> p.animated = v);
        segmented(c, x0, y0 + 20, 92, 14, new String[]{"Flow", "Wave", "Pulse", "Shimmer"}, p.motion.ordinal(), i -> p.motion = Motion.values()[i]);
        text(c, MOTION_DESC[p.motion.ordinal()], x0, y0 + 42, DIM);
        if (!p.animated) text(c, "Animation is off - turn it on to see it move.", x0, y0 + 54, BAD);
        slider(c, x0, y0 + 70, 260, "Speed", 0.1f, 5f, p.speed, v -> p.speed = v);
        slider(c, x0, y0 + 98, 260, "Spread (Flow / Wave)", 0.25f, 4f, p.spread, v -> p.spread = v);
        text(c, "Nametag and tab list animate live. Chat lines keep the", x0, y0 + 124, DIM);
        text(c, "look they had when the message arrived.", x0, y0 + 134, DIM);
    }

    private void tabSettings(DrawContext c, NSConfig cfg, StyleProfile p, int x0, int y0) {
        text(c, "WHERE TO APPLY", x0, y0, DIM);
        toggle(c, x0, y0 + 14, "Chat (your name + mentions)", cfg.chat, v -> cfg.chat = v);
        toggle(c, x0, y0 + 32, "Tab list", cfg.tab, v -> cfg.tab = v);
        toggle(c, x0, y0 + 50, "Nametag", cfg.nametag, v -> cfg.nametag = v);
        toggle(c, x0, y0 + 68, "Show my own nametag in 3rd person", cfg.ownNametag, v -> cfg.ownNametag = v);

        text(c, "PROFILE NAME", x0, y0 + 92, DIM);
        box(c, nameBox, x0 + 82, y0 + 88, 130, 14, "Profile");

        text(c, "SHARE", x0, y0 + 112, DIM);
        button(c, x0, y0 + 124, 130, 15, lit("Copy style code"), false, () -> {
            String json = NSConfig.GSON_COMPACT.toJson(p);
            client.keyboard.setClipboard("NS1:" + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
            say("Style code copied to clipboard", GOOD);
        });
        button(c, x0 + 136, y0 + 124, 130, 15, lit("Import from clipboard"), false, this::doImport);

        int rx = x0 + 290;
        text(c, "OPEN EDITOR", rx, y0, DIM);
        String key = NameStyleMod.OPEN_KEY == null ? "?" : NameStyleMod.OPEN_KEY.getBoundKeyLocalizedText().getString();
        text(c, "Key: " + key, rx, y0 + 12, TXT);
        text(c, "Command: /namestyle", rx, y0 + 24, TXT);
        text(c, "/namestyle toggle", rx, y0 + 36, DIM);
        text(c, "/namestyle profile <name>", rx, y0 + 48, DIM);
        text(c, "Rebind in Options >", rx, y0 + 66, DIM);
        text(c, "Controls > NameStyle", rx, y0 + 76, DIM);
    }

    private void doImport() {
        try {
            String cb = client.keyboard.getClipboard().trim();
            if (!cb.startsWith("NS1:")) throw new IllegalArgumentException("not a style code");
            String json = new String(Base64.getDecoder().decode(cb.substring(4)), StandardCharsets.UTF_8);
            StyleProfile n = NSConfig.GSON_COMPACT.fromJson(json, StyleProfile.class).fix();
            if (NSConfig.I.profiles.size() >= 24) { say("Profile limit reached (24)", BAD); return; }
            NSConfig.I.profiles.add(n);
            NSConfig.I.active = NSConfig.I.profiles.size() - 1;
            selStop = 0;
            lastRgb = -1;
            say("Imported \"" + n.name + "\"", GOOD);
        } catch (Exception e) {
            say("Clipboard has no valid style code", BAD);
        }
    }
}
