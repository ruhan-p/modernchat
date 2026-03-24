package com.infloat.modernchat;

import java.util.List;
import java.util.Map;

/**
 * Represents one command-syntax group loaded from a JSON file in
 * config/modernchat/syntaxes/.
 *
 * JSON structure:
 * <pre>
 * {
 *   "name":  "vanilla",
 *   "color": "0xFFAAAAAA",   // ARGB hex – color of autocomplete suggestion text
 *   "commands": {
 *     "/ban": ["<player>", "<player> [reason]"],
 *     ...
 *   }
 * }
 * </pre>
 *
 * The color field accepts:
 *   "0xAARRGGBB"  (with or without leading 0x / 0X)
 *   "#AARRGGBB"   or  "#RRGGBB"  (alpha defaults to FF when omitted)
 */
public class CommandSyntaxDef {

    public String name = "unnamed";

    /**
     * Server IP suffix for server-specific defs (e.g. "hypixel.net").
     * Null or absent means this def is always active (no server restriction).
     */
    public String ip = null;

    /**
     * When true, always-on defs (those without an ip field, e.g. vanilla) are
     * excluded when this server-specific def is matched. Allows a server's
     * command set to fully replace vanilla suggestions.
     */
    public boolean disableVanilla = false;

    /** ARGB hex string, e.g. "0xFFAAAAAA". Parsed by {@link #getColorInt()}. */
    public String color = "0xFFAAAAAA";

    /** Maps command (e.g. "/ban") to its list of argument-pattern strings. */
    public Map<String, List<String>> commands;

    // -------------------------------------------------------------------------
    // Optional fields — null means "this file contributes nothing for this category"
    // -------------------------------------------------------------------------

    /** Commands eligible for coordinate autocomplete from crosshair. */
    public List<String> coordCommands;

    /** Commands that accept all four selectors (@p, @a, @e, @r). */
    public List<String> selectorCommands;

    /** Commands that accept only @p and @r. */
    public List<String> limitedSelectorCommands;

    /** Token strings that trigger player-name suggestions. */
    public List<String> playerTokens;

    /** Token strings that trigger block-registry suggestions. */
    public List<String> blockTokens;

    /** Token strings that trigger enchantment suggestions. */
    public List<String> enchantmentTokens;

    /** Token strings that trigger potion-effect suggestions. */
    public List<String> effectTokens;

    /** Token strings that trigger entity-name suggestions. */
    public List<String> entityNameTokens;

    /** Token strings that trigger item-registry suggestions. */
    public List<String> itemTokens;

    /**
     * Maps command (e.g. "/fly") to its rank-indicator color (ARGB hex string).
     * Commands present here will show a colored {@code *} suffix in autocomplete.
     */
    public Map<String, String> rankColors;

    /**
     * Enchantment ID-to-name map. Keys are string integers (e.g. "0" → "Protection").
     * JSON object keys must be strings; the loader parses them to int at aggregate time.
     */
    public Map<String, String> enchantmentNames;

    /**
     * Potion effect ID-to-name map. Keys are string integers (e.g. "1" → "Speed").
     * JSON object keys must be strings; the loader parses them to int at aggregate time.
     */
    public Map<String, String> effectNames;

    /**
     * Parses the {@link #color} field into an ARGB int.
     * Falls back to {@code 0xFFAAAAAA} (opaque light-grey) on parse error.
     */
    public int getColorInt() {
        if (color == null) return 0xFFAAAAAA;
        try {
            String c = color.trim();
            if (c.startsWith("0x") || c.startsWith("0X")) {
                return parseHex(c.substring(2));
            }
            if (c.startsWith("#")) {
                return parseHex(c.substring(1));
            }
            return parseHex(c);
        } catch (NumberFormatException e) {
            return 0xFFAAAAAA;
        }
    }

    private static int parseHex(String hex) {
        // If only 6 digits, prepend full-alpha FF
        if (hex.length() == 6) hex = "FF" + hex;
        return (int) Long.parseLong(hex, 16);
    }
}
