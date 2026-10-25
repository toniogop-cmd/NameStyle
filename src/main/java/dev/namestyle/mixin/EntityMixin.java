package dev.namestyle.mixin;

import dev.namestyle.config.NSConfig;
import dev.namestyle.style.NameRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void namestyle$display(CallbackInfoReturnable<Text> cir) {
        if (!(((Object) this) instanceof ClientPlayerEntity)) return;
        NSConfig c = NSConfig.I;
        if (!c.enabled || !c.nametag) return;
        try {
            cir.setReturnValue(NameRenderer.restyle(cir.getReturnValue()));
        } catch (Throwable ignored) {
        }
    }
}
