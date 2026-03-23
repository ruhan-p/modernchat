package com.infloat.modernchat.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(ChatScreen.class)
public abstract class ChatAutocompleteMixin extends Screen {

    @Shadow
    protected TextFieldWidget chatField;

    @Unique
    private static final int ENTRY_HEIGHT = 12;

    @Unique
    private static final int MAX_VISIBLE = 10;

    @Unique
    private static final List<String> COMMANDS = Arrays.asList("/ban", "/ban-ip", "/banlist", "/blockdata", "/clear", "/clone", "/debug", "/defaultgamemode", "/deop", "/difficulty", "/effect", "/enchant", "/entitydata", "/execute", "/fill", "/gamemode", "/gamerule", "/give", "/help", "/?",
            "/kick", "/kill", "/list", "/me", "/msg", "/op", "/pardon", "/pardon-ip", "/particle", "/playsound", "/publish", "/replaceitem", "/save-all", "/save-off", "/save-on", "/say", "/scoreboard", "/seed", "/setblock", "/setidletimeout", "/setworldspawn", "/spawnpoint", "/spreadplayers",
            "/stats", "/stop", "/summon", "/tell", "/tellraw", "/testfor", "/testforblock", "/testforblocks", "/time", "/title", "/toggledownfall", "/tp", "/teleport", "/trigger", "/w", "/weather", "/whitelist", "/worldborder", "/xp");

    @Unique
    private List<String> suggestions = new ArrayList<>();

    @Unique
    private int selectedIndex = -1;

    @Unique
    private String lastFilterText = "";

    @Unique
    private boolean browsing = false;

    @Inject(method = "render", at = @At("TAIL"))
    private void modernchat$renderAutocomplete(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        String text = this.chatField.getText();

        // While browsing, keep the suggestion box open and skip recalculation
        if (this.browsing) {
            if (this.suggestions.isEmpty()) {
                this.browsing = false;
            }
        } else {
            if (!text.startsWith("/") || text.length() < 2 || text.contains(" ")) {
                this.suggestions.clear();
                this.selectedIndex = -1;
                this.lastFilterText = "";
                return;
            }

            if (!text.equals(this.lastFilterText)) {
                this.lastFilterText = text;
                String prefix = text.toLowerCase();
                this.suggestions = COMMANDS.stream()
                        .filter(cmd -> cmd.startsWith(prefix) && !cmd.equals(prefix))
                        .collect(Collectors.toList());
                this.selectedIndex = this.suggestions.isEmpty() ? -1 : 0;
            }
        }

        if (this.suggestions.isEmpty()) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int visibleCount = Math.min(this.suggestions.size(), MAX_VISIBLE);

        int maxWidth = 0;
        for (int i = 0; i < visibleCount; i++) {
            int w = tr.getStringWidth(this.suggestions.get(i));
            if (w > maxWidth) maxWidth = w;
        }

        int boxWidth = maxWidth + 10;
        int boxHeight = visibleCount * ENTRY_HEIGHT;
        int boxX = 2;
        int boxY = this.height - 14 - boxHeight;

        // Background
        fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xCC000000);

        // Entries
        for (int i = 0; i < visibleCount; i++) {
            int entryY = boxY + i * ENTRY_HEIGHT;

            if (i == this.selectedIndex) {
                fill(boxX, entryY, boxX + boxWidth, entryY + ENTRY_HEIGHT, 0x882A2A40);
            }

            int textColor = (i == this.selectedIndex) ? 0xFFFFFF00 : 0xFFAAAAAA;
            tr.drawWithShadow(this.suggestions.get(i), boxX + 4, entryY + 2, textColor);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void modernchat$onKeyPressed(char chr, int keyCode, CallbackInfo ci) {
        if (this.suggestions.isEmpty()) return;

        // Tab - commit selected suggestion and close
        if (keyCode == 15) {
            int idx = this.selectedIndex >= 0 ? this.selectedIndex : 0;
            if (idx < this.suggestions.size()) {
                this.chatField.setText(this.suggestions.get(idx) + " ");
                this.suggestions.clear();
                this.selectedIndex = -1;
                this.lastFilterText = "";
                this.browsing = false;
            }
            ci.cancel();
            return;
        }

        // Up arrow - move selection up, preview in chat
        if (keyCode == 200) {
            if (this.selectedIndex > 0) {
                this.selectedIndex--;
                this.browsing = true;
                this.chatField.setText(this.suggestions.get(this.selectedIndex));
            }
            ci.cancel();
            return;
        }

        // Down arrow - move selection down, preview in chat
        if (keyCode == 208) {
            int maxIdx = Math.min(this.suggestions.size(), MAX_VISIBLE) - 1;
            if (this.selectedIndex < maxIdx) {
                this.selectedIndex++;
                this.browsing = true;
                this.chatField.setText(this.suggestions.get(this.selectedIndex));
            }
            ci.cancel();
            return;
        }

        // Any other key exits browsing mode
        if (this.browsing) {
            this.browsing = false;
            this.lastFilterText = "";
        }
    }
}
