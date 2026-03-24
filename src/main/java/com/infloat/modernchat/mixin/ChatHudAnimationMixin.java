package com.infloat.modernchat.mixin;

import com.infloat.modernchat.ModernChatConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Back-ported inspiration from ezzenix's ChatAnimation/Smooth Chat

@Mixin(ChatHud.class)
public class ChatHudAnimationMixin {

    @Shadow private int scrolledLines;

    @Unique private long modernchat$lastMessageTime = 0L;
    @Unique private boolean modernchat$matrixPushed = false;

    @Unique
    private float modernchat$calculateDisplacement() {
        if (this.scrolledLines != 0) {
            return 0;
        }

        float fadeTime = 150f;
        int lineHeight = 9;
        float maxDisplacement = lineHeight * 0.8f;
        long lifetime = System.currentTimeMillis() - modernchat$lastMessageTime;
        float alpha = Math.min(lifetime / fadeTime, 1f);

        return maxDisplacement - (alpha * maxDisplacement);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void modernchat$onRenderHead(int ticks, CallbackInfo ci) {
        if (!ModernChatConfig.INSTANCE.smoothAnimations) return;
        float displacement = modernchat$calculateDisplacement();
        if (displacement > 0.01f) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0f, displacement, 0f);
            modernchat$matrixPushed = true;
        } else {
            modernchat$matrixPushed = false;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void modernchat$onRenderReturn(int ticks, CallbackInfo ci) {
        if (modernchat$matrixPushed) {
            GlStateManager.popMatrix();
            modernchat$matrixPushed = false;
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("TAIL"))
    private void modernchat$onAddMessage(Text message, CallbackInfo ci) {
        modernchat$lastMessageTime = System.currentTimeMillis();
    }
}
