package dev.namestyle.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.namestyle.NameStyleMod;
import dev.namestyle.style.StyleProfile;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NSConfig {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Gson GSON_COMPACT = new Gson();
    public static NSConfig I = new NSConfig();

    public boolean enabled = true;
    public boolean chat = true;
    public boolean tab = true;
    public boolean nametag = true;
    public boolean ownNametag = true;
    public int active = 0;
    public List<StyleProfile> profiles = new ArrayList<>();

    public StyleProfile profile() {
        if (profiles == null) profiles = new ArrayList<>();
        if (profiles.isEmpty()) profiles.add(new StyleProfile());
        if (active < 0 || active >= profiles.size()) active = 0;
        return profiles.get(active);
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("namestyle.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                NSConfig c = GSON.fromJson(Files.readString(p), NSConfig.class);
                if (c != null) I = c;
            }
        } catch (Exception e) {
            NameStyleMod.LOG.warn("Could not read namestyle.json, using defaults", e);
        }
        I.profile();
        I.profiles.forEach(StyleProfile::fix);
        save();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(I));
        } catch (Exception e) {
            NameStyleMod.LOG.warn("Could not save namestyle.json", e);
        }
    }
}
