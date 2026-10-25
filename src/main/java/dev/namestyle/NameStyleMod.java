package dev.namestyle;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.namestyle.config.NSConfig;
import dev.namestyle.gui.NameStyleScreen;
import dev.namestyle.style.StyleProfile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameStyleMod implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("NameStyle");
    public static KeyBinding OPEN_KEY;
    private static boolean openRequested;

    @Override
    public void onInitializeClient() {
        NSConfig.load();

        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.namestyle.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.namestyle"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_KEY.wasPressed()) openRequested = true;
            if (openRequested) {
                openRequested = false;
                mc.setScreen(new NameStyleScreen(mc.currentScreen));
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("namestyle")
                .executes(ctx -> {
                    openRequested = true;
                    return 1;
                })
                .then(ClientCommandManager.literal("toggle").executes(ctx -> {
                    NSConfig.I.enabled = !NSConfig.I.enabled;
                    NSConfig.save();
                    ctx.getSource().sendFeedback(Text.literal("NameStyle " + (NSConfig.I.enabled ? "enabled" : "disabled")));
                    return 1;
                }))
                .then(ClientCommandManager.literal("profile")
                    .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                        .suggests((ctx, b) -> CommandSource.suggestMatching(
                            NSConfig.I.profiles.stream().map(p -> p.name).toList(), b))
                        .executes(ctx -> {
                            String want = StringArgumentType.getString(ctx, "name");
                            for (int i = 0; i < NSConfig.I.profiles.size(); i++) {
                                StyleProfile p = NSConfig.I.profiles.get(i);
                                if (p.name.equalsIgnoreCase(want)) {
                                    NSConfig.I.active = i;
                                    NSConfig.save();
                                    ctx.getSource().sendFeedback(Text.literal("Switched to profile " + p.name));
                                    return 1;
                                }
                            }
                            ctx.getSource().sendError(Text.literal("No profile named " + want));
                            return 0;
                        })))));
    }
}
