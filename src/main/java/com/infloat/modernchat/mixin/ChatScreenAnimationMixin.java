package com.infloat.modernchat.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Back-ported inspiration from ezzenix's ChatAnimation/Smooth Chat

@Mixin(ChatScreen.class)
public class ChatScreenAnimationMixin {

    @Unique private boolean modernchat$wasOpenedLastFrame = false;
    @Unique private long modernchat$lastOpenTime = 0L;
    @Unique private float modernchat$displacement = 0f;
    @Unique private boolean modernchat$matrixPushed = false;

    @Unique
    private float modernchat$calculateDisplacement() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && !modernchat$wasOpenedLastFrame && !client.player.isSleeping()) {
            modernchat$wasOpenedLastFrame = true;
            modernchat$lastOpenTime = System.currentTimeMillis();
        }

        float fadeTime = 200f;
        float fadeOffset = 8f;
        float screenFactor = (float) client.height / 1080f;
        float timeSinceOpen = Math.min((float) (System.currentTimeMillis() - modernchat$lastOpenTime), fadeTime);
        float alpha = 1f - (timeSinceOpen / fadeTime);

        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float modifiedAlpha = c3 * alpha * alpha * alpha - c1 * alpha * alpha;

        return modifiedAlpha * fadeOffset * screenFactor;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void modernchat$onRenderHead(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        modernchat$displacement = modernchat$calculateDisplacement();
        if (modernchat$displacement > 0.01f) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0f, modernchat$displacement, 0f);
            modernchat$matrixPushed = true;
        } else {
            modernchat$matrixPushed = false;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void modernchat$onRenderReturn(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (modernchat$matrixPushed) {
            GlStateManager.popMatrix();
            modernchat$matrixPushed = false;
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void modernchat$onRemoved(CallbackInfo ci) {
        modernchat$wasOpenedLastFrame = false;
    }
}
