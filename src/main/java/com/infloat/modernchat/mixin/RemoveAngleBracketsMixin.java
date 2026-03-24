package com.infloat.modernchat.mixin;

import com.infloat.modernchat.ModernChatConfig;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Back-ported inspiration from Flytre7's No Angled Brackets

@Mixin(ChatHud.class)
public class RemoveAngleBracketsMixin {

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true)
    private Text modernchat$removeAngleBrackets(Text message) {
        if (!ModernChatConfig.INSTANCE.replaceAngleBrackets) return message;
        if (!(message instanceof TranslatableText)) {
            return message;
        }

        TranslatableText translatable = (TranslatableText) message;
        if (!"chat.type.text".equals(translatable.getKey())) {
            return message;
        }

        // Replace angle brackets with colon
        String formatted = message.asFormattedString();
        String replaced = formatted.replaceFirst("<([^>]+)> ", "$1: ");

        return new LiteralText(replaced);
    }
}
