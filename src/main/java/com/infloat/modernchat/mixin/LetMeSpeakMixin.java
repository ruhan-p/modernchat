package com.infloat.modernchat.mixin;

import com.infloat.modernchat.ChatStateManager;
import com.infloat.modernchat.ModernChatConfig;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Source code from bytespacegames' LetMeSpeak

@Mixin(ChatScreen.class)
public class LetMeSpeakMixin {

    @Shadow
    protected TextFieldWidget chatField;

    @Inject(method = "keyPressed", at = @At("RETURN"))
    protected void modernchat$keyPressed(char chr, int keyCode, CallbackInfo ci) {
        if (!ModernChatConfig.INSTANCE.preserveChatInput) return;
        // key codes 1 = Escape, 28 = Enter, 156 = Numpad Enter — all close the screen
        if (keyCode != 1 && keyCode != 28 && keyCode != 156) {
            ChatStateManager.INSTANCE.updateState(this.chatField.getText());
        } else {
            ChatStateManager.INSTANCE.resetState();
        }
    }

    @Inject(method = "init", at = @At("RETURN"))
    public void modernchat$init(CallbackInfo ci) {
        if (!ModernChatConfig.INSTANCE.preserveChatInput) return;
        if (ChatStateManager.INSTANCE.restoreState()) {
            this.chatField.setText(ChatStateManager.INSTANCE.getState());
            this.chatField.setCursorToEnd();
        }
    }
}
