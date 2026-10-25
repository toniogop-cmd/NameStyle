package dev.namestyle.mixin;

import dev.namestyle.config.NSConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla never draws your own nametag. This lets you see it in third person (F5). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "hasLabel", at = @At("RETURN"), cancellable = true)
    private void namestyle$ownLabel(LivingEntity entity, double dist, CallbackInfoReturnable<Boolean> cir) {
        NSConfig c = NSConfig.I;
        if (!c.enabled || !c.nametag || !c.ownNametag || cir.getReturnValue()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity != mc.player || mc.options.getPerspective().isFirstPerson() || !MinecraftClient.isHudEnabled()) return;
        cir.setReturnValue(true);
    }
}
