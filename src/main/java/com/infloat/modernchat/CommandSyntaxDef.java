package com.infloat.modernchat;

import java.util.List;
import java.util.Map;

/**
 * Represents one command-syntax group loaded from a JSON file in
 * config/modernchat/servers/.
 *
 * JSON structure:
 *
 * {
 *   "name":  "singleplayer",
 *   "color": "0xFFAAAAAA", (color of autocomplete text)
 *   "commands": {
 *     "/ban": ["<player>", "<player> [reason]"],
 *     ...
 *   }
 * }
 *
 *
 * The color field accepts:
 *   "0xAARRGGBB"  (with or without leading 0x / 0X)
 *   "#AARRGGBB"   or  "#RRGGBB"  (alpha defaults to FF when omitted)
 */

public class CommandSyntaxDef {

    public String name = "unnamed";

    public String ip = null;
    public boolean disableSingleplayer = false;
    public String color = "0xFFAAAAAA";
    public transient java.io.File sourceFile;
    public Map<String, List<String>> commands;

    public List<String> coordCommands;
    public List<String> selectorCommands;
    public List<String> limitedSelectorCommands;
    public List<String> playerTokens;
    public List<String> blockTokens;
    public List<String> enchantmentTokens;
    public List<String> effectTokens;
    public List<String> entityNameTokens;
    public List<String> itemTokens;

    public List<String> friends;

    public Map<String, String> rankColors;

    public Map<String, String> enchantmentNames;
    public Map<String, String> effectNames;

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
