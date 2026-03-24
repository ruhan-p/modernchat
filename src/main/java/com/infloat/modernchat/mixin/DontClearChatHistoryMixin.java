package com.infloat.modernchat.mixin;

import com.infloat.modernchat.ModernChatConfig;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Back-ported inspiration from anar4732's Don't Clear Chat History

@Mixin(ChatHud.class)
public class DontClearChatHistoryMixin {
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void modernchat$clear(CallbackInfo ci) {
        if (ModernChatConfig.INSTANCE.maintainChatHistory) {
            ci.cancel();
        }
    }
}