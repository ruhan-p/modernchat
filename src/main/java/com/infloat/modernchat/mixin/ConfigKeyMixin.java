package com.infloat.modernchat.mixin;

import com.infloat.modernchat.ModernChatClient;
import com.infloat.modernchat.ModernChatConfigScreen;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class ConfigKeyMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void modernchat$onTick(CallbackInfo ci) {
        if (ModernChatClient.configKeyBinding != null && ModernChatClient.configKeyBinding.wasPressed()) {
            MinecraftClient client = (MinecraftClient) (Object) this;
            if (client.currentScreen == null) {
                client.setScreen(new ModernChatConfigScreen(null));
            }
        }
    }
}
