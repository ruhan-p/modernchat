package com.infloat.modernchat.mixin;

import com.infloat.modernchat.ModernChatConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Back-ported inspiration from Gnembon's Chat Up!

@Mixin(ChatHud.class)
public class ChatUpMixin {
    @Shadow @Final private MinecraftClient client;

    @Unique
    private int modernchat$getOffset() {
        if (!ModernChatConfig.INSTANCE.raiseChat) return 0;
        PlayerEntity player = client.player;
        if (player == null || player.abilities.creativeMode || player.abilities.invulnerable) return 0;
        int offset = player.getArmorProtectionValue() > 0 ? 30 : 20;
        if (player.getAbsorption() > 0) offset += 10;
        return offset;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void modernchat$onRenderHead(int ticks, CallbackInfo ci) {
        int offset = modernchat$getOffset();
        if (offset > 0) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0f, -offset, 0f);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void modernchat$onRenderReturn(int ticks, CallbackInfo ci) {
        if (modernchat$getOffset() > 0) {
            GlStateManager.popMatrix();
        }
    }

    @Inject(method = "getVisibleLineCount", at = @At("RETURN"), cancellable = true)
    private void modernchat$modifyHeight(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValue() + modernchat$getOffset());
    }
}