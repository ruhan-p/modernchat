package com.infloat.modernchat.mixin;

import com.infloat.modernchat.CommandSyntaxDef;
import com.infloat.modernchat.CommandSyntaxLoader;
import com.infloat.modernchat.ModernChatConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

// TODO: organize command syntax targets

@Mixin(ChatScreen.class)
public abstract class AutocompleteMixin extends Screen {

    @Shadow
    protected TextFieldWidget chatField;

    @Unique
    private static final int ENTRY_HEIGHT = 12;

    @Unique
    private static final int MAX_VISIBLE = 10;

    @Unique
    private static final int HINT_COLOR = 0xFF777777;

    @Unique
    private static final int SELECTED_COLOR = 0xFFFFFF00;

    // Parallel lists for suggestion data
    @Unique
    private final List<String> suggestionDisplays = new ArrayList<String>();

    @Unique
    private final List<String> suggestionCompletions = new ArrayList<String>();

    @Unique
    private final List<Integer> suggestionColors = new ArrayList<Integer>();

    @Unique
    private int selectedIndex = -1;

    @Unique
    private int scrollOffset = 0;

    @Unique
    private String lastFilterText = "";

    @Unique
    private boolean browsing = false;

    // Syntax data load

    /** command -> list of tokenised argument arrays */
    @Unique
    private static final Map<String, List<String[]>> COMMAND_SYNTAX = new LinkedHashMap<String, List<String[]>>();

    /** command -> autocomplete text color (ARGB int) from its syntax group */
    @Unique
    private static final Map<String, Integer> COMMAND_COLORS = new HashMap<String, Integer>();

    @Unique
    private static final List<String> COMMAND_NAMES = new ArrayList<String>();

    // Sentinel value — can never be a real server address, so first call always loads.
    @Unique
    private static volatile String modernchat$lastKnownServer = "##UNINITIALIZED##";

    @Unique
    private static String modernchat$getCurrentServerHost() {
        try {
            ServerInfo entry = MinecraftClient.getInstance().getCurrentServerEntry();
            if (entry == null) return null;
            String addr = entry.address;
            if (addr == null || addr.isEmpty()) return null;
            // Strip port, handling IPv6 brackets
            int colon = addr.lastIndexOf(':');
            int bracket = addr.lastIndexOf(']');
            if (colon > bracket) addr = addr.substring(0, colon);
            return addr.toLowerCase(java.util.Locale.ROOT).trim();
        } catch (Exception e) {
            return null;
        }
    }

    @Unique
    private static void modernchat$clearSyntax() {
        COMMAND_SYNTAX.clear();
        COMMAND_COLORS.clear();
        COMMAND_NAMES.clear();
        COORD_COMMANDS            = Collections.emptySet();
        SELECTOR_COMMANDS         = Collections.emptySet();
        LIMITED_SELECTOR_COMMANDS = Collections.emptySet();
        PLAYER_TOKENS             = Collections.emptySet();
        BLOCK_TOKENS              = Collections.emptySet();
        ENCHANTMENT_TOKENS        = Collections.emptySet();
        EFFECT_TOKENS             = Collections.emptySet();
        ENTITY_NAME_TOKENS        = Collections.emptySet();
        ITEM_TOKENS               = Collections.emptySet();
        ENCHANTMENT_NAMES         = Collections.emptyMap();
        EFFECT_NAMES              = Collections.emptyMap();
    }

    @Unique
    private static void modernchat$ensureSyntaxLoaded() {
        String currentServer = modernchat$getCurrentServerHost();
        // Fast path: same server as last load, nothing to do.
        String sentinel = "##UNINITIALIZED##";
        String last = modernchat$lastKnownServer;
        if (!sentinel.equals(last)
                && (currentServer == null ? last == null : currentServer.equals(last))) {
            return;
        }
        synchronized (COMMAND_SYNTAX) {
            // Re-check inside lock in case another thread just finished loading.
            currentServer = modernchat$getCurrentServerHost();
            last = modernchat$lastKnownServer;
            if (!sentinel.equals(last)
                    && (currentServer == null ? last == null : currentServer.equals(last))) {
                return;
            }
            modernchat$clearSyntax();
            List<CommandSyntaxDef> defs = CommandSyntaxLoader.loadForServer(currentServer);
            for (CommandSyntaxDef def : defs) {
                int color = def.getColorInt();
                for (Map.Entry<String, List<String>> entry : def.commands.entrySet()) {
                    String cmd = entry.getKey();
                    List<String> patterns = entry.getValue();
                    modernchat$registerCommand(cmd, color, patterns.toArray(new String[0]));
                }
            }
            COMMAND_NAMES.addAll(COMMAND_SYNTAX.keySet());
            Collections.sort(COMMAND_NAMES);

            CommandSyntaxLoader.AggregatedData agg = CommandSyntaxLoader.aggregate(defs);
            COORD_COMMANDS            = agg.coordCommands;
            SELECTOR_COMMANDS         = agg.selectorCommands;
            LIMITED_SELECTOR_COMMANDS = agg.limitedSelectorCommands;
            PLAYER_TOKENS             = agg.playerTokens;
            BLOCK_TOKENS              = agg.blockTokens;
            ENCHANTMENT_TOKENS        = agg.enchantmentTokens;
            EFFECT_TOKENS             = agg.effectTokens;
            ENTITY_NAME_TOKENS        = agg.entityNameTokens;
            ITEM_TOKENS               = agg.itemTokens;
            ENCHANTMENT_NAMES         = agg.enchantmentNames;
            EFFECT_NAMES              = agg.effectNames;

            modernchat$lastKnownServer = currentServer;
        }
    }

    @Unique
    private static void modernchat$registerCommand(String command, int color, String... entries) {
        List<String[]> tokenized = new ArrayList<String[]>();
        for (String entry : entries) {
            if (entry.isEmpty()) {
                tokenized.add(new String[0]);
            } else {
                tokenized.add(entry.split(" "));
            }
        }
        COMMAND_SYNTAX.put(command, tokenized);
        COMMAND_COLORS.put(command, color);
    }

    // Lookup tables from JSON

    @Unique
    private static Set<String> COORD_COMMANDS = Collections.emptySet();

    @Unique
    private static Set<String> PLAYER_TOKENS = Collections.emptySet();

    @Unique
    private static Set<String> BLOCK_TOKENS = Collections.emptySet();

    @Unique
    private static Set<String> ENCHANTMENT_TOKENS = Collections.emptySet();

    @Unique
    private static Set<String> EFFECT_TOKENS = Collections.emptySet();

    @Unique
    private static Set<String> ENTITY_NAME_TOKENS = Collections.emptySet();

    @Unique
    private static Set<String> ITEM_TOKENS = Collections.emptySet();

    @Unique
    private static Set<String> SELECTOR_COMMANDS = Collections.emptySet();

    @Unique
    private static Set<String> LIMITED_SELECTOR_COMMANDS = Collections.emptySet();

    // Selector display names
    @Unique
    private static final String[][] SELECTORS_ALL = {
            {"@p", "@p (Nearest Player)"},
            {"@a", "@a (All Players)"},
            {"@e", "@e (All Entities)"},
            {"@r", "@r (Random Player)"}
    };

    @Unique
    private static final String[][] SELECTORS_LIMITED = {
            {"@p", "@p (Nearest Player)"},
            {"@r", "@r (Random Player)"}
    };

    // Populated from JSON syntax files at load time
    @Unique
    private static Map<Integer, String> ENCHANTMENT_NAMES = Collections.emptyMap();

    @Unique
    private static Map<Integer, String> EFFECT_NAMES = Collections.emptyMap();

    // --- Helper methods ---

    @Unique
    private static boolean modernchat$isPlaceholder(String token) {
        return (token.startsWith("<") && token.contains(">"))
                || (token.startsWith("[") && token.endsWith("]"));
    }

    @Unique
    private static boolean modernchat$isCoordToken(String token) {
        return token.equals("<x>") || token.equals("<y>") || token.equals("<z>")
                || token.equals("<x1>") || token.equals("<y1>") || token.equals("<z1>")
                || token.equals("<x2>") || token.equals("<y2>") || token.equals("<z2>");
    }

    @Unique
    private static int modernchat$getCoordComponent(String token, int x, int y, int z) {
        char axis = token.charAt(1);
        if (axis == 'x') return x;
        if (axis == 'y') return y;
        return z;
    }

    @Unique
    private List<String> modernchat$getPlayerNames() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientPlayNetworkHandler handler = client.getNetworkHandler();
            if (handler == null) return Collections.emptyList();
            List<String> names = new ArrayList<String>();
            for (Object obj : handler.getPlayerList()) {
                PlayerListEntry entry = (PlayerListEntry) obj;
                if (entry.getProfile() != null) {
                    String name = entry.getProfile().getName();
                    if (name != null && !name.isEmpty()) {
                        names.add(name);
                    }
                }
            }
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            return names;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Unique
    private List<String> modernchat$getBlockNames() {
        try {
            List<String> names = new ArrayList<String>();
            for (Object obj : Block.REGISTRY) {
                Identifier id = Block.REGISTRY.getIdentifier((Block) obj);
                if (id != null) {
                    names.add(id.toString());
                }
            }
            Collections.sort(names);
            return names;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Unique
    private List<String> modernchat$getItemNames() {
        try {
            List<String> names = new ArrayList<String>();
            for (Object obj : Item.REGISTRY) {
                Identifier id = Item.REGISTRY.getIdentifier((Item) obj);
                if (id != null) {
                    names.add(id.toString());
                }
            }
            Collections.sort(names);
            return names;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Unique
    private List<String> modernchat$getEntityNames() {
        try {
            List<String> names = EntityType.getEntityNames();
            if (names == null) return Collections.emptyList();
            List<String> sorted = new ArrayList<String>(names);
            Collections.sort(sorted);
            return sorted;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Unique
    private int[] modernchat$getCrosshairBlockPos() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.result != null && client.result.type == BlockHitResult.Type.BLOCK) {
                BlockPos pos = client.result.getBlockPos();
                return new int[]{pos.getX(), pos.getY(), pos.getZ()};
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @Unique
    private String modernchat$buildCompletion(String[] parts, int argTargetPos, String value) {
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 0; i < argTargetPos; i++) {
            sb.append(' ').append(parts[i + 1]);
        }
        sb.append(' ').append(value);
        return sb.toString();
    }

    @Unique
    private void modernchat$addSuggestion(String display, String completion, Set<String> seen, String key, int color) {
        if (seen.add(key)) {
            this.suggestionDisplays.add(display);
            this.suggestionCompletions.add(completion);
            this.suggestionColors.add(color);
        }
    }

    // --- Core suggestion computation ---

    @Unique
    private void modernchat$computeSuggestions(String text) {
        modernchat$ensureSyntaxLoaded();
        this.suggestionDisplays.clear();
        this.suggestionCompletions.clear();
        this.suggestionColors.clear();
        this.scrollOffset = 0;

        if (!text.startsWith("/") || text.length() < 2) {
            this.selectedIndex = -1;
            return;
        }

        if (!text.contains(" ")) {
            String prefix = text.toLowerCase();
            for (String cmd : COMMAND_NAMES) {
                if (cmd.startsWith(prefix) && !cmd.equals(prefix)) {
                    int color = COMMAND_COLORS.containsKey(cmd) ? COMMAND_COLORS.get(cmd) : 0xFFAAAAAA;
                    this.suggestionDisplays.add(cmd);
                    this.suggestionCompletions.add(cmd);
                    this.suggestionColors.add(color);
                }
            }
        } else {
            modernchat$computeArgSuggestions(text);
        }

        this.selectedIndex = this.suggestionDisplays.isEmpty() ? -1 : 0;
    }

    @Unique
    private void modernchat$computeArgSuggestions(String text) {
        String[] parts = text.split(" ", -1);
        String command = parts[0].toLowerCase();

        List<String[]> syntaxEntries = COMMAND_SYNTAX.get(command);
        if (syntaxEntries == null) return;

        int commandColor = COMMAND_COLORS.containsKey(command) ? COMMAND_COLORS.get(command) : 0xFFAAAAAA;

        boolean trailingSpace = text.endsWith(" ");
        int argTargetPos = parts.length - 2;
        String partial = trailingSpace ? "" : parts[parts.length - 1].toLowerCase();

        Set<String> seen = new LinkedHashSet<String>();
        int[] blockCoords = null;
        boolean coordsResolved = false;

        for (String[] argTokens : syntaxEntries) {
            if (argTokens.length <= argTargetPos) continue;

            boolean matches = true;
            for (int i = 0; i < argTargetPos; i++) {
                String inputArg = parts[i + 1];
                String syntaxToken = argTokens[i];

                if (modernchat$isPlaceholder(syntaxToken)) {
                    continue;
                }
                if (!syntaxToken.equalsIgnoreCase(inputArg)) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;

            String nextToken = argTokens[argTargetPos];

            if (PLAYER_TOKENS.contains(nextToken)) {
                List<String> players = modernchat$getPlayerNames();
                boolean anyMatch = false;
                for (String name : players) {
                    if (!trailingSpace && !name.toLowerCase().startsWith(partial)) continue;
                    modernchat$addSuggestion(name,
                            modernchat$buildCompletion(parts, argTargetPos, name),
                            seen, "p:" + name, commandColor);
                    anyMatch = true;
                }

                String[][] selectors = null;
                if (SELECTOR_COMMANDS.contains(command)) {
                    selectors = SELECTORS_ALL;
                } else if (LIMITED_SELECTOR_COMMANDS.contains(command)) {
                    selectors = SELECTORS_LIMITED;
                }
                if (selectors != null) {
                    for (String[] sel : selectors) {
                        if (!trailingSpace && !sel[0].startsWith(partial)) continue;
                        modernchat$addSuggestion(sel[1],
                                modernchat$buildCompletion(parts, argTargetPos, sel[0]),
                                seen, "s:" + sel[0], commandColor);
                        anyMatch = true;
                    }
                }

                if (!anyMatch) {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken, HINT_COLOR);
                }

            } else if (modernchat$isCoordToken(nextToken) && COORD_COMMANDS.contains(command)) {
                if (!coordsResolved) {
                    blockCoords = modernchat$getCrosshairBlockPos();
                    coordsResolved = true;
                }
                if (blockCoords != null) {
                    String coordVal = String.valueOf(
                            modernchat$getCoordComponent(nextToken, blockCoords[0], blockCoords[1], blockCoords[2]));
                    if (trailingSpace || coordVal.startsWith(partial)) {
                        modernchat$addSuggestion(coordVal,
                                modernchat$buildCompletion(parts, argTargetPos, coordVal),
                                seen, "c:" + nextToken, commandColor);
                    }
                } else {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken, HINT_COLOR);
                }

            } else if (BLOCK_TOKENS.contains(nextToken)) {
                List<String> blocks = modernchat$getBlockNames();
                boolean anyMatch = false;
                for (String name : blocks) {
                    if (!trailingSpace && !name.toLowerCase().startsWith(partial)) continue;
                    modernchat$addSuggestion(name,
                            modernchat$buildCompletion(parts, argTargetPos, name),
                            seen, "b:" + name, commandColor);
                    anyMatch = true;
                }
                if (!anyMatch) {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken, HINT_COLOR);
                }

            } else if (ENCHANTMENT_TOKENS.contains(nextToken)) {
                boolean anyMatch = false;
                for (Map.Entry<Integer, String> entry : ENCHANTMENT_NAMES.entrySet()) {
                    String idStr = String.valueOf(entry.getKey());
                    String display = idStr + " (" + entry.getValue() + ")";
                    if (!trailingSpace && !idStr.startsWith(partial)
                            && !entry.getValue().toLowerCase().startsWith(partial)) continue;
                    modernchat$addSuggestion(display,
                            modernchat$buildCompletion(parts, argTargetPos, idStr),
                            seen, "e:" + idStr, commandColor);
                    anyMatch = true;
                }
                if (!anyMatch) {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken, HINT_COLOR);
                }

            } else if (EFFECT_TOKENS.contains(nextToken)) {
                boolean anyMatch = false;
                for (Map.Entry<Integer, String> entry : EFFECT_NAMES.entrySet()) {
                    String idStr = String.valueOf(entry.getKey());
                    String display = idStr + " (" + entry.getValue() + ")";
                    if (!trailingSpace && !idStr.startsWith(partial)
                            && !entry.getValue().toLowerCase().startsWith(partial)) continue;
                    modernchat$addSuggestion(display,
                            modernchat$buildCompletion(parts, argTargetPos, idStr),
                            seen, "fx:" + idStr, commandColor);
                    anyMatch = true;
                }
                if (!anyMatch) {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken, HINT_COLOR);
                }

            } else if (ENTITY_NAME_TOKENS.contains(nextToken)) {
                List<String> entities = modernchat$getEntityNames();
                boolean anyMatch = false;
                for (String name : entities) {
                    if (!trailingSpace && !name.toLowerCase().startsWith(partial)) continue;
                    modernchat$addSuggestion(name,
                            modernchat$buildCompletion(parts, argTargetPos, name),
                            seen, "en:" + name, commandColor);
                    anyMatch = true;
                }
                if (!anyMatch) {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken, HINT_COLOR);
                }

            } else if (ITEM_TOKENS.contains(nextToken)) {
                List<String> items = modernchat$getItemNames();
                boolean anyMatch = false;
                for (String name : items) {
                    String lowerName = name.toLowerCase();
                    boolean fullMatches = trailingSpace || lowerName.startsWith(partial);
                    String shortName = lowerName.startsWith("minecraft:") ? lowerName.substring(10) : null;
                    boolean shortMatches = shortName != null && (trailingSpace || shortName.startsWith(partial));
                    if (fullMatches) {
                        modernchat$addSuggestion(name,
                                modernchat$buildCompletion(parts, argTargetPos, name),
                                seen, "it:" + name, commandColor);
                        anyMatch = true;
                    }
                    if (shortMatches) {
                        String shortDisplay = name.substring(10);
                        modernchat$addSuggestion(shortDisplay,
                                modernchat$buildCompletion(parts, argTargetPos, shortDisplay),
                                seen, "its:" + shortDisplay, commandColor);
                        anyMatch = true;
                    }
                }
                if (!anyMatch) {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken, HINT_COLOR);
                }

            } else if (modernchat$isPlaceholder(nextToken)) {
                modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken, HINT_COLOR);

            } else {
                // Literal keyword
                if (!trailingSpace && !nextToken.toLowerCase().startsWith(partial)) continue;
                modernchat$addSuggestion(nextToken,
                        modernchat$buildCompletion(parts, argTargetPos, nextToken),
                        seen, "l:" + nextToken, commandColor);
            }
        }
    }

    // --- Render ---

    @Inject(method = "render", at = @At("TAIL"))
    private void modernchat$renderAutocomplete(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!ModernChatConfig.INSTANCE.autocomplete) return;
        String text = this.chatField.getText();

        if (this.browsing) {
            if (this.suggestionDisplays.isEmpty()) {
                this.browsing = false;
            }
        } else {
            if (!text.equals(this.lastFilterText)) {
                this.lastFilterText = text;
                modernchat$computeSuggestions(text);
            }
        }

        if (this.suggestionDisplays.isEmpty()) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int totalCount = this.suggestionDisplays.size();
        int visibleCount = Math.min(totalCount, MAX_VISIBLE);

        // Ensure selected index is visible
        if (this.selectedIndex >= 0) {
            if (this.selectedIndex < this.scrollOffset) {
                this.scrollOffset = this.selectedIndex;
            }
            if (this.selectedIndex >= this.scrollOffset + MAX_VISIBLE) {
                this.scrollOffset = this.selectedIndex - MAX_VISIBLE + 1;
            }
        }

        // Compute box width from visible entries
        int maxWidth = 0;
        for (int i = 0; i < visibleCount; i++) {
            int idx = this.scrollOffset + i;
            if (idx < totalCount) {
                int w = tr.getStringWidth(this.suggestionDisplays.get(idx));
                if (w > maxWidth) maxWidth = w;
            }
        }

        int boxWidth = maxWidth + 10;
        int boxHeight = visibleCount * ENTRY_HEIGHT;

        int lastSpace = text.lastIndexOf(' ');
        String textBeforeCurrentWord = lastSpace >= 0 ? text.substring(0, lastSpace + 1) : "";
        int boxX = 4 + tr.getStringWidth(textBeforeCurrentWord);
        int boxY = this.height - 14 - boxHeight;

        // Background
        fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xCC000000);

        if (this.scrollOffset > 0) {
            tr.drawWithShadow("\u25B2", boxX + boxWidth - 10, boxY + 1, 0xFF888888);
        }
        if (this.scrollOffset + visibleCount < totalCount) {
            tr.drawWithShadow("\u25BC", boxX + boxWidth - 10, boxY + boxHeight - 10, 0xFF888888);
        }

        // Entries
        for (int i = 0; i < visibleCount; i++) {
            int idx = this.scrollOffset + i;
            if (idx >= totalCount) break;

            int entryY = boxY + i * ENTRY_HEIGHT;
            String completion = this.suggestionCompletions.get(idx);
            boolean isHint = completion == null;

            if (idx == this.selectedIndex && !isHint) {
                fill(boxX, entryY, boxX + boxWidth, entryY + ENTRY_HEIGHT, 0x882A2A40);
            }

            int textColor = (idx == this.selectedIndex && !isHint)
                    ? SELECTED_COLOR
                    : this.suggestionColors.get(idx);

            tr.drawWithShadow(this.suggestionDisplays.get(idx), boxX + 4, entryY + 2, textColor);
        }
    }

    // --- Key handling ---

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void modernchat$onKeyPressed(char chr, int keyCode, CallbackInfo ci) {
        if (!ModernChatConfig.INSTANCE.autocomplete) return;
        if (this.suggestionDisplays.isEmpty()) return;

        if (keyCode == 15) { // Tab
            int idx = this.selectedIndex >= 0 ? this.selectedIndex : 0;
            if (idx < this.suggestionCompletions.size()) {
                String completion = this.suggestionCompletions.get(idx);
                if (completion != null) {
                    this.chatField.setText(completion);
                    this.suggestionDisplays.clear();
                    this.suggestionCompletions.clear();
                    this.suggestionColors.clear();
                    this.selectedIndex = -1;
                    this.scrollOffset = 0;
                    this.lastFilterText = "";
                    this.browsing = false;
                }
            }
            ci.cancel();
            return;
        }

        if (keyCode == 200) { // Up
            if (this.selectedIndex > 0) {
                this.selectedIndex--;
                this.browsing = true;
                if (this.selectedIndex < this.scrollOffset) {
                    this.scrollOffset = this.selectedIndex;
                }
                String completion = this.suggestionCompletions.get(this.selectedIndex);
                if (completion != null) {
                    this.chatField.setText(completion);
                }
            }
            ci.cancel();
            return;
        }

        if (keyCode == 208) { // Down
            int maxIdx = this.suggestionDisplays.size() - 1;
            if (this.selectedIndex < maxIdx) {
                this.selectedIndex++;
                this.browsing = true;
                if (this.selectedIndex >= this.scrollOffset + MAX_VISIBLE) {
                    this.scrollOffset = this.selectedIndex - MAX_VISIBLE + 1;
                }
                String completion = this.suggestionCompletions.get(this.selectedIndex);
                if (completion != null) {
                    this.chatField.setText(completion);
                }
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
