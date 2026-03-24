package com.infloat.modernchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads command-syntax definition files from {@code config/modernchat/syntaxes/}.
 *
 * On the first call to {@link #loadAll()}, if the directory is empty (or does not
 * yet contain {@code vanilla.json}), the bundled default is copied from the mod's
 * classpath resources so users have a starting point they can freely edit.
 *
 * Each {@code *.json} file in that directory is parsed into a {@link CommandSyntaxDef}.
 * Files are loaded in alphabetical order so the loading order is deterministic.
 */
public class CommandSyntaxLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SYNTAX_DIR = new File("config/modernchat/syntaxes");
    private static final String BUNDLED_VANILLA = "/assets/modernchat/syntaxes/vanilla.json";

    /**
     * Ensures the config directory exists and the bundled defaults are present,
     * then reads and returns all syntax definitions.
     */
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

    // -------------------------------------------------------------------------

    /**
     * Aggregated sets and maps built by merging contributions from all loaded
     * {@link CommandSyntaxDef} files.  Used by AutocompleteMixin to replace its
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
    }

    /**
     * Merges all contributions from each {@link CommandSyntaxDef} into a single
     * {@link AggregatedData} object.  Null fields in a def simply contribute nothing.
     * For ID maps, non-integer keys are skipped with a warning.
     */
    public static AggregatedData aggregate(List<CommandSyntaxDef> defs) {
        AggregatedData agg = new AggregatedData();
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
        }
        return agg;
    }

    // -------------------------------------------------------------------------

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
        File vanillaFile = new File(SYNTAX_DIR, "vanilla.json");
        if (!vanillaFile.exists()) {
            copyBundledResource(BUNDLED_VANILLA, vanillaFile);
        }
    }

    private static void copyBundledResource(String resourcePath, File dest) {
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
