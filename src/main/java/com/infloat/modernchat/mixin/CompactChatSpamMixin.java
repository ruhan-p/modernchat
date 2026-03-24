package com.infloat.modernchat.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Back-ported inspiration from caoimhebyrne's compact-chat

@Mixin(ChatHud.class)
public class CompactChatSpamMixin {

    @Shadow @Final private List<ChatHudLine> visibleMessages;
    @Shadow @Final private List<ChatHudLine> messages;

    @Unique private String modernchat$lastMessageContent = "";
    @Unique private int modernchat$repeatCount = 1;
    @Unique private boolean modernchat$addingCompacted = false;

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void modernchat$onAddMessage(Text message, CallbackInfo ci) {
        if (modernchat$addingCompacted) {
            return;
        }

        String incoming = message.asFormattedString();

        if (incoming.equals(modernchat$lastMessageContent) && !incoming.isEmpty()) {
            modernchat$repeatCount++;

            modernchat$removePreviousMessage();

            LiteralText counter = new LiteralText(" (x" + modernchat$repeatCount + ")");
            counter.setStyle(new Style().setFormatting(Formatting.GRAY));

            Text compacted = new LiteralText("")
                    .append(message)
                    .append(counter);

            modernchat$addingCompacted = true;
            ((ChatHud) (Object) this).addMessage(compacted);
            modernchat$addingCompacted = false;

            ci.cancel();
        } else {
            modernchat$lastMessageContent = incoming;
            modernchat$repeatCount = 1;
        }
    }

    @Unique
    private void modernchat$removePreviousMessage() {
        if (!messages.isEmpty()) {
            messages.remove(0);
        }

        // Remove the visible (wrapped) lines that belonged to the previous message.
        // Visible lines from the last message share the same creation tick as the
        // first entry, so remove consecutive lines from the top with that tick.
        if (!visibleMessages.isEmpty()) {
            int tick = visibleMessages.get(0).getCreationTick();
            while (!visibleMessages.isEmpty() && visibleMessages.get(0).getCreationTick() == tick) {
                visibleMessages.remove(0);
            }
        }
    }
}