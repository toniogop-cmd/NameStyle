package dev.namestyle.mixin;

import dev.namestyle.config.NSConfig;
import dev.namestyle.style.NameRenderer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"), argsOnly = true)
    private Text namestyle$chat(Text message) {
        NSConfig c = NSConfig.I;
        if (!c.enabled || !c.chat) return message;
        try {
            return NameRenderer.restyle(message);
        } catch (Throwable t) {
            return message;
        }
    }
}
