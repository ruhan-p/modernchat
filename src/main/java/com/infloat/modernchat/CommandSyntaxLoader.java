package com.infloat.modernchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads command-syntax definition files from config/modernchat/servers/.
 *
 * On the first call to loadAll() if the directory is empty (or does not
 * yet contain singleplayer.json the bundled default is copied from the mod's
 * classpath resources so users have a starting point they can freely edit.
 *
 * Each json file in that directory is parsed into a CommandSyntaxDef.
 * Files are loaded in alphabetical order so the loading order is deterministic.
 */

public class CommandSyntaxLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SYNTAX_DIR = new File("config/modernchat/servers");
    private static final String BUNDLED_SINGLEPLAYER  = "/assets/modernchat/servers/singleplayer.json";
    private static final String BUNDLED_HYPIXEL  = "/assets/modernchat/servers/hypixel.json";

    public static List<CommandSyntaxDef> loadAll() {
        ensureDefaultsExist();

        List<CommandSyntaxDef> defs = new ArrayList<CommandSyntaxDef>();
        File[] files = SYNTAX_DIR.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".json");
            }
        });

        if (files == null || files.length == 0) return defs;

        Arrays.sort(files); // alphabetical – deterministic load order

        for (File file : files) {
            CommandSyntaxDef def = loadFile(file);
            if (def != null && def.commands != null) {
                defs.add(def);
            }
        }
        return defs;
    }

    /**
     * Aggregated sets and maps built by merging contributions from all loaded
     * CommandSyntaxDef files.  Used by AutocompleteMixin to replace its
     * hardcoded static sets and ID-to-name maps.
     */
    public static class AggregatedData {
        public final Set<String> coordCommands           = new HashSet<String>();
        public final Set<String> selectorCommands        = new HashSet<String>();
        public final Set<String> limitedSelectorCommands = new HashSet<String>();
        public final Set<String> playerTokens            = new HashSet<String>();
        public final Set<String> blockTokens             = new HashSet<String>();
        public final Set<String> enchantmentTokens       = new HashSet<String>();
        public final Set<String> effectTokens            = new HashSet<String>();
        public final Set<String> entityNameTokens        = new HashSet<String>();
        public final Set<String> itemTokens              = new HashSet<String>();
        public final Map<Integer, String> enchantmentNames = new LinkedHashMap<Integer, String>();
        public final Map<Integer, String> effectNames      = new LinkedHashMap<Integer, String>();
        public final Map<String, Integer> rankColors       = new LinkedHashMap<String, Integer>();
        public final List<String> friends                  = new ArrayList<String>();
    }

    // Token strings that are universal across all server definitions.
    private static final String[] DEFAULT_PLAYER_TOKENS      = {"<player>", "<address|player>", "<player|entity>", "<target>", "<source>", "<entity>", "<selector>"};
    private static final String[] DEFAULT_BLOCK_TOKENS       = {"<tileName>", "<replaceTileName>", "<block>"};
    private static final String[] DEFAULT_ENCHANTMENT_TOKENS = {"<enchantmentId>", "<enchantment>"};
    private static final String[] DEFAULT_EFFECT_TOKENS      = {"<effect>"};
    private static final String[] DEFAULT_ENTITY_NAME_TOKENS = {"<entityName>", "<entity>"};
    private static final String[] DEFAULT_ITEM_TOKENS        = {"<item>"};

    /**
     * Merges all contributions from each CommandSyntaxDef into a single
     * AggregatedData object.  Null fields in a def simply contribute nothing.
     * For ID maps, non-integer keys are skipped with a warning.
     */
    public static AggregatedData aggregate(List<CommandSyntaxDef> defs) {
        AggregatedData agg = new AggregatedData();
        java.util.Collections.addAll(agg.playerTokens,      DEFAULT_PLAYER_TOKENS);
        java.util.Collections.addAll(agg.blockTokens,       DEFAULT_BLOCK_TOKENS);
        java.util.Collections.addAll(agg.enchantmentTokens, DEFAULT_ENCHANTMENT_TOKENS);
        java.util.Collections.addAll(agg.effectTokens,      DEFAULT_EFFECT_TOKENS);
        java.util.Collections.addAll(agg.entityNameTokens,  DEFAULT_ENTITY_NAME_TOKENS);
        java.util.Collections.addAll(agg.itemTokens,        DEFAULT_ITEM_TOKENS);
        for (CommandSyntaxDef def : defs) {
            if (def.coordCommands           != null) agg.coordCommands.addAll(def.coordCommands);
            if (def.selectorCommands        != null) agg.selectorCommands.addAll(def.selectorCommands);
            if (def.limitedSelectorCommands != null) agg.limitedSelectorCommands.addAll(def.limitedSelectorCommands);
            if (def.playerTokens            != null) agg.playerTokens.addAll(def.playerTokens);
            if (def.blockTokens             != null) agg.blockTokens.addAll(def.blockTokens);
            if (def.enchantmentTokens       != null) agg.enchantmentTokens.addAll(def.enchantmentTokens);
            if (def.effectTokens            != null) agg.effectTokens.addAll(def.effectTokens);
            if (def.entityNameTokens        != null) agg.entityNameTokens.addAll(def.entityNameTokens);
            if (def.itemTokens              != null) agg.itemTokens.addAll(def.itemTokens);
            if (def.enchantmentNames != null) {
                for (Map.Entry<String, String> e : def.enchantmentNames.entrySet()) {
                    try {
                        agg.enchantmentNames.put(Integer.parseInt(e.getKey()), e.getValue());
                    } catch (NumberFormatException ex) {
                        System.err.println("[ModernChat] Skipping non-integer enchantment key '" + e.getKey() + "' in " + def.name);
                    }
                }
            }
            if (def.effectNames != null) {
                for (Map.Entry<String, String> e : def.effectNames.entrySet()) {
                    try {
                        agg.effectNames.put(Integer.parseInt(e.getKey()), e.getValue());
                    } catch (NumberFormatException ex) {
                        System.err.println("[ModernChat] Skipping non-integer effect key '" + e.getKey() + "' in " + def.name);
                    }
                }
            }
            if (def.rankColors != null) {
                for (Map.Entry<String, String> e : def.rankColors.entrySet()) {
                    try {
                        String c = e.getValue().trim();
                        if (c.startsWith("0x") || c.startsWith("0X")) c = c.substring(2);
                        else if (c.startsWith("#")) c = c.substring(1);
                        if (c.length() == 6) c = "FF" + c;
                        agg.rankColors.put(e.getKey(), (int) Long.parseLong(c, 16));
                    } catch (NumberFormatException ex) {
                        System.err.println("[ModernChat] Invalid rank color '" + e.getValue() + "' for '" + e.getKey() + "' in " + def.name);
                    }
                }
            }
            if (def.friends != null) {
                for (String f : def.friends) {
                    if (f != null && !f.isEmpty() && !agg.friends.contains(f)) {
                        agg.friends.add(f);
                    }
                }
            }
        }
        return agg;
    }

    /**
     * Loads syntax definitions filtered for the given server host.
     *
     * Defs with no ip field are always-on (e.g. singleplayer).
     *
     * @param serverHost Normalized server hostname (port stripped, lowercased),
     * or null for singleplayer / no active server.
     */
    public static List<CommandSyntaxDef> loadForServer(String serverHost) {
        List<CommandSyntaxDef> all = loadAll();

        List<CommandSyntaxDef> alwaysOn   = new ArrayList<CommandSyntaxDef>();
        List<CommandSyntaxDef> serverDefs = new ArrayList<CommandSyntaxDef>();

        for (CommandSyntaxDef def : all) {
            if (def.ip == null || def.ip.isEmpty()) {
                alwaysOn.add(def);
            } else if (serverHost != null && ipMatches(normalizeHost(def.ip), serverHost)) {
                serverDefs.add(def);
            }
        }

        boolean disableSingleplayer = false;
        for (CommandSyntaxDef def : serverDefs) {
            if (def.disableSingleplayer) { disableSingleplayer = true; break; }
        }

        List<CommandSyntaxDef> result = new ArrayList<CommandSyntaxDef>();
        if (!disableSingleplayer) result.addAll(alwaysOn);
        result.addAll(serverDefs);
        return result;
    }

    /**
     * Returns true if address equals ip or is a subdomain of it.
     */
    public static boolean ipMatches(String ip, String address) {
        if (ip == null || address == null) return false;
        return address.equals(ip) || address.endsWith("." + ip);
    }

    private static String normalizeHost(String address) {
        if (address == null) return null;
        int colon = address.lastIndexOf(':');
        int bracket = address.lastIndexOf(']');
        if (colon > bracket) address = address.substring(0, colon);
        return address.toLowerCase(java.util.Locale.ROOT).trim();
    }

    // -------------------------------------------------------------------------

    /**
     * Loads every syntax JSON in the directory, regardless of whether it has a
     * commands map.
     */
    public static List<CommandSyntaxDef> loadAllDefs() {
        ensureDefaultsExist();
        File[] files = SYNTAX_DIR.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) { return name.endsWith(".json"); }
        });
        if (files == null) return new ArrayList<CommandSyntaxDef>();
        Arrays.sort(files);

        List<CommandSyntaxDef> result = new ArrayList<CommandSyntaxDef>();
        for (File file : files) {
            CommandSyntaxDef def = loadFile(file);
            if (def != null) {
                def.sourceFile = file;
                result.add(def);
            }
        }
        return result;
    }

    /**
     * Overwrites only the color key inside the given syntax JSON file,
     * preserving all other fields.  Returns true on success.
     */
    public static boolean saveColor(File file, String colorHex) {
        if (file == null || !file.exists()) return false;
        try {
            JsonObject obj;
            Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            try {
                obj = GSON.fromJson(reader, JsonObject.class);
            } finally {
                reader.close();
            }
            if (obj == null) return false;
            obj.addProperty("color", colorHex);
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            try {
                GSON.toJson(obj, writer);
            } finally {
                writer.close();
            }
            return true;
        } catch (Exception e) {
            System.err.println("[ModernChat] Failed to save color to '" + file.getName() + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Overwrites only the friends array inside the given server JSON file,
     * preserving all other fields.  Returns true on success.
     */
    public static boolean saveFriends(File file, List<String> friends) {
        if (file == null || !file.exists()) return false;
        try {
            JsonObject obj;
            Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            try {
                obj = GSON.fromJson(reader, JsonObject.class);
            } finally {
                reader.close();
            }
            if (obj == null) return false;
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            if (friends != null) {
                for (String name : friends) arr.add(new com.google.gson.JsonPrimitive(name));
            }
            obj.add("friends", arr);
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            try {
                GSON.toJson(obj, writer);
            } finally {
                writer.close();
            }
            return true;
        } catch (Exception e) {
            System.err.println("[ModernChat] Failed to save friends to '" + file.getName() + "': " + e.getMessage());
            return false;
        }
    }

    private static CommandSyntaxDef loadFile(File file) {
        try {
            Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            try {
                return GSON.fromJson(reader, CommandSyntaxDef.class);
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            System.err.println("[ModernChat] Failed to load syntax file '" + file.getName() + "': " + e.getMessage());
            return null;
        }
    }

    private static void ensureDefaultsExist() {
        SYNTAX_DIR.mkdirs();
        // Always overwrite bundled syntaxes so they stay in sync with the mod.
        // Users who want to customize behavior should create a separate file
        // (e.g. custom.json) in the same directory.
        copyBundledResource(BUNDLED_SINGLEPLAYER, new File(SYNTAX_DIR, "singleplayer.json"));
        copyBundledResource(BUNDLED_HYPIXEL, new File(SYNTAX_DIR, "hypixel.json"));
    }

    private static void copyBundledResource(String resourcePath, File dest) {
        // Only create the file if it doesn't already exist so that user edit.
        if (dest.exists()) return;
        InputStream in = CommandSyntaxLoader.class.getResourceAsStream(resourcePath);
        if (in == null) {
            System.err.println("[ModernChat] Bundled resource not found: " + resourcePath);
            return;
        }
        try {
            OutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
            }
        } catch (IOException e) {
            System.err.println("[ModernChat] Failed to write default syntax file: " + e.getMessage());
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
    }
}
