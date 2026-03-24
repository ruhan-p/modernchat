package com.infloat.modernchat.mixin;

import com.infloat.modernchat.ModernChatConfig;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Back-ported inspiration from JackFred2's MoreChatHistory

@Mixin(ChatHud.class)
public class ChatHistoryMixin {
    @ModifyConstant(
            method = "addMessage(Lnet/minecraft/text/Text;IIZ)V",
            constant = @Constant(intValue = 100)
    )
    private int modernchat$changeMaxMessages(int original) {
        return ModernChatConfig.INSTANCE.chatHistory;
    }
}
