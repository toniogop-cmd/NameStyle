package dev.namestyle.mixin;

import dev.namestyle.config.NSConfig;
import dev.namestyle.style.NameRenderer;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void namestyle$tab(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        NSConfig c = NSConfig.I;
        if (!c.enabled || !c.tab) return;
        try {
            cir.setReturnValue(NameRenderer.restyle(cir.getReturnValue()));
        } catch (Throwable ignored) {
        }
    }
}
