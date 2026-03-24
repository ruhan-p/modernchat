package com.infloat.modernchat.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
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

@Mixin(ChatScreen.class)
public abstract class ChatAutocompleteMixin extends Screen {

    @Shadow
    protected TextFieldWidget chatField;

    @Unique
    private static final int ENTRY_HEIGHT = 12;

    @Unique
    private static final int MAX_VISIBLE = 10;

    // Parallel lists for suggestion data
    @Unique
    private final List<String> suggestionDisplays = new ArrayList<String>();

    @Unique
    private final List<String> suggestionCompletions = new ArrayList<String>(); // null = non-completable hint

    @Unique
    private int selectedIndex = -1;

    @Unique
    private int scrollOffset = 0;

    @Unique
    private String lastFilterText = "";

    @Unique
    private boolean browsing = false;

    // Syntax tree: command -> list of argument token arrays
    @Unique
    private static final Map<String, List<String[]>> COMMAND_SYNTAX = new LinkedHashMap<String, List<String[]>>();

    @Unique
    private static final List<String> COMMAND_NAMES;

    // Commands eligible for coordinate autocomplete from crosshair
    @Unique
    private static final Set<String> COORD_COMMANDS = new HashSet<String>(Arrays.asList(
            "/blockdata", "/clone", "/fill", "/replaceitem", "/setblock",
            "/setworldspawn", "/spawnpoint", "/stats", "/testforblock", "/testforblocks"
    ));

    // Tokens that trigger player name suggestions
    @Unique
    private static final Set<String> PLAYER_TOKENS = new HashSet<String>(Arrays.asList(
            "<player>", "<address|player>", "<player|entity>", "<target>", "<source>",
            "<entity>", "<selector>"
    ));

    // Tokens that trigger block registry suggestions
    @Unique
    private static final Set<String> BLOCK_TOKENS = new HashSet<String>(Arrays.asList(
            "<tileName>", "<replaceTileName>", "<block>"
    ));

    // Tokens that trigger enchantment suggestions
    @Unique
    private static final Set<String> ENCHANTMENT_TOKENS = new HashSet<String>(Arrays.asList(
            "<enchantmentId>"
    ));

    // Tokens that trigger potion effect suggestions
    @Unique
    private static final Set<String> EFFECT_TOKENS = new HashSet<String>(Arrays.asList(
            "<effect>"
    ));

    // Tokens that trigger entity name suggestions
    @Unique
    private static final Set<String> ENTITY_NAME_TOKENS = new HashSet<String>(Arrays.asList(
            "<entityName>"
    ));

    // Tokens that trigger item registry suggestions
    @Unique
    private static final Set<String> ITEM_TOKENS = new HashSet<String>(Arrays.asList(
            "<item>"
    ));

    // Commands that accept all selectors
    @Unique
    private static final Set<String> SELECTOR_COMMANDS = new HashSet<String>(Arrays.asList(
            "/clear", "/effect", "/enchant", "/execute", "/give", "/kill",
            "/msg", "/particle", "/playsound", "/replaceitem", "/scoreboard",
            "/spawnpoint", "/spreadplayers", "/stats", "/summon", "/tell",
            "/tellraw", "/testfor", "/tp", "/teleport", "/trigger", "/w", "/xp"
    ));

    // Commands that only accept @p and @r
    @Unique
    private static final Set<String> LIMITED_SELECTOR_COMMANDS = new HashSet<String>(Arrays.asList(
            "/kick", "/me"
    ));

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

    // Enchantment ID
    @Unique
    private static final Map<Integer, String> ENCHANTMENT_NAMES = new LinkedHashMap<Integer, String>();

    // Potion effect ID
    @Unique
    private static final Map<Integer, String> EFFECT_NAMES = new LinkedHashMap<Integer, String>();

    @Unique
    private static void addSyntax(String command, String... entries) {
        List<String[]> tokenized = new ArrayList<String[]>();
        for (String entry : entries) {
            if (entry.isEmpty()) {
                tokenized.add(new String[0]);
            } else {
                tokenized.add(entry.split(" "));
            }
        }
        COMMAND_SYNTAX.put(command, tokenized);
    }

    static {
        // /ban
        addSyntax("/ban", "<player>", "<player> [reason]");

        // /ban-ip
        addSyntax("/ban-ip", "<address|player>", "<address|player> [reason]");

        // /banlist
        addSyntax("/banlist", "", "ips", "players");

        // /blockdata
        addSyntax("/blockdata", "<x> <y> <z> <dataTag>");

        // /clear
        addSyntax("/clear", "", "<player>", "<player> <item>", "<player> <item> <data>",
                "<player> <item> <data> <maxCount>", "<player> <item> <data> <maxCount> <dataTag>");

        // /clone
        addSyntax("/clone",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z>",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> replace",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> replace force",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> replace move",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> replace normal",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> masked",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> masked force",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> masked move",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> masked normal",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> filtered <tileName>",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> filtered <tileName> force",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> filtered <tileName> move",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> filtered <tileName> normal");

        // /debug
        addSyntax("/debug", "start", "stop");

        // /defaultgamemode
        addSyntax("/defaultgamemode", "survival", "creative", "adventure", "spectator", "0", "1", "2", "3");

        // /deop
        addSyntax("/deop", "<player>");

        // /difficulty
        addSyntax("/difficulty", "peaceful", "easy", "normal", "hard", "0", "1", "2", "3");

        // /effect
        addSyntax("/effect",
                "<player> clear",
                "<player> <effect>",
                "<player> <effect> <seconds>",
                "<player> <effect> <seconds> <amplifier>",
                "<player> <effect> <seconds> <amplifier> true",
                "<player> <effect> <seconds> <amplifier> false");

        // /enchant
        addSyntax("/enchant", "<player> <enchantmentId>", "<player> <enchantmentId> <level>");

        // /entitydata
        addSyntax("/entitydata", "<entity> <dataTag>");

        // /execute
        addSyntax("/execute",
                "<entity> <x> <y> <z> <command>",
                "<entity> <x> <y> <z> detect <x2> <y2> <z2> <block> <data> <command>");

        // /fill
        addSyntax("/fill",
                "<x1> <y1> <z1> <x2> <y2> <z2> <tileName>",
                "<x1> <y1> <z1> <x2> <y2> <z2> <tileName> <dataValue>",
                "<x1> <y1> <z1> <x2> <y2> <z2> <tileName> <dataValue> replace",
                "<x1> <y1> <z1> <x2> <y2> <z2> <tileName> <dataValue> replace <replaceTileName>",
                "<x1> <y1> <z1> <x2> <y2> <z2> <tileName> <dataValue> replace <replaceTileName> <replaceDataValue>",
                "<x1> <y1> <z1> <x2> <y2> <z2> <tileName> <dataValue> destroy",
                "<x1> <y1> <z1> <x2> <y2> <z2> <tileName> <dataValue> hollow",
                "<x1> <y1> <z1> <x2> <y2> <z2> <tileName> <dataValue> keep",
                "<x1> <y1> <z1> <x2> <y2> <z2> <tileName> <dataValue> outline",
                "<x1> <y1> <z1> <x2> <y2> <z2> <tileName> <dataValue> 0 <dataTag>");

        // /gamemode
        addSyntax("/gamemode",
                "survival", "creative", "adventure", "spectator", "0", "1", "2", "3",
                "survival <player>", "creative <player>", "adventure <player>", "spectator <player>",
                "0 <player>", "1 <player>", "2 <player>", "3 <player>");

        // /gamerule
        addSyntax("/gamerule",
                "",
                "announceAdvancements",
                "commandBlockOutput", "disableElytraMovementCheck",
                "doDaylightCycle", "doEntityDrops", "doFireTick", "doMobLoot",
                "doMobSpawning", "doTileDrops", "doWeatherCycle",
                "keepInventory", "logAdminCommands", "mobGriefing",
                "naturalRegeneration", "randomTickSpeed", "reducedDebugInfo",
                "sendCommandFeedback", "showDeathMessages", "spawnRadius",
                "spectatorsGenerateChunks",
                "commandBlockOutput true", "commandBlockOutput false",
                "doDaylightCycle true", "doDaylightCycle false",
                "doFireTick true", "doFireTick false",
                "doMobLoot true", "doMobLoot false",
                "doMobSpawning true", "doMobSpawning false",
                "doTileDrops true", "doTileDrops false",
                "keepInventory true", "keepInventory false",
                "mobGriefing true", "mobGriefing false",
                "naturalRegeneration true", "naturalRegeneration false",
                "randomTickSpeed <value>", "spawnRadius <value>");

        // /give
        addSyntax("/give",
                "<player> <item>",
                "<player> <item> <amount>",
                "<player> <item> <amount> <data>",
                "<player> <item> <amount> <data> <dataTag>");

        // /help
        addSyntax("/help", "", "<page>", "<command>");
        addSyntax("/?", "", "<page>", "<command>");

        // /kick
        addSyntax("/kick", "<player>", "<player> [reason]");

        // /kill
        addSyntax("/kill", "", "<player|entity>");

        // /list
        addSyntax("/list", "", "uuids");

        // /me
        addSyntax("/me", "<action>");

        // /msg
        addSyntax("/msg", "<player> <message>");

        // /op
        addSyntax("/op", "<player>");

        // /pardon
        addSyntax("/pardon", "<player>");

        // /pardon-ip
        addSyntax("/pardon-ip", "<address>");

        // /particle
        addSyntax("/particle",
                "<name> <x> <y> <z> <xd> <yd> <zd> <speed>",
                "<name> <x> <y> <z> <xd> <yd> <zd> <speed> <count>",
                "<name> <x> <y> <z> <xd> <yd> <zd> <speed> <count> normal",
                "<name> <x> <y> <z> <xd> <yd> <zd> <speed> <count> force",
                "<name> <x> <y> <z> <xd> <yd> <zd> <speed> <count> normal <player>",
                "<name> <x> <y> <z> <xd> <yd> <zd> <speed> <count> force <player>");

        // /playsound
        addSyntax("/playsound",
                "<sound> <player>",
                "<sound> <player> <x> <y> <z>",
                "<sound> <player> <x> <y> <z> <volume>",
                "<sound> <player> <x> <y> <z> <volume> <pitch>",
                "<sound> <player> <x> <y> <z> <volume> <pitch> <minimumVolume>");

        // /publish
        addSyntax("/publish", "");

        // /replaceitem
        addSyntax("/replaceitem",
                "block <x> <y> <z> <slot> <item>",
                "block <x> <y> <z> <slot> <item> <amount>",
                "block <x> <y> <z> <slot> <item> <amount> <data>",
                "block <x> <y> <z> <slot> <item> <amount> <data> <dataTag>",
                "entity <selector> <slot> <item>",
                "entity <selector> <slot> <item> <amount>",
                "entity <selector> <slot> <item> <amount> <data>",
                "entity <selector> <slot> <item> <amount> <data> <dataTag>");

        // /save-all, /save-off, /save-on
        addSyntax("/save-all", "", "flush");
        addSyntax("/save-off", "");
        addSyntax("/save-on", "");

        // /say
        addSyntax("/say", "<message>");

        // /scoreboard
        addSyntax("/scoreboard",
                "objectives list",
                "objectives add <name> <criteriaType>",
                "objectives add <name> <criteriaType> <displayName>",
                "objectives remove <name>",
                "objectives setdisplay list",
                "objectives setdisplay list <name>",
                "objectives setdisplay sidebar",
                "objectives setdisplay sidebar <name>",
                "objectives setdisplay belowName",
                "objectives setdisplay belowName <name>",
                "players list",
                "players list <player>",
                "players set <player> <objective> <score>",
                "players set <player> <objective> <score> <dataTag>",
                "players add <player> <objective> <count>",
                "players add <player> <objective> <count> <dataTag>",
                "players remove <player> <objective> <count>",
                "players remove <player> <objective> <count> <dataTag>",
                "players reset <player>",
                "players reset <player> <objective>",
                "players enable <player> <objective>",
                "players test <player> <objective> <min>",
                "players test <player> <objective> <min> <max>",
                "players operation <player> <objective> <operation> <source> <sourceObjective>",
                "teams list",
                "teams list <team>",
                "teams add <team>",
                "teams add <team> <displayName>",
                "teams remove <team>",
                "teams empty <team>",
                "teams join <team>",
                "teams join <team> <player>",
                "teams leave",
                "teams leave <player>",
                "teams option <team> color <value>",
                "teams option <team> friendlyfire true",
                "teams option <team> friendlyfire false",
                "teams option <team> seeFriendlyInvisibles true",
                "teams option <team> seeFriendlyInvisibles false",
                "teams option <team> nametagVisibility never",
                "teams option <team> nametagVisibility always",
                "teams option <team> nametagVisibility hideForOtherTeams",
                "teams option <team> nametagVisibility hideForOwnTeam",
                "teams option <team> deathMessageVisibility never",
                "teams option <team> deathMessageVisibility always",
                "teams option <team> deathMessageVisibility hideForOtherTeams",
                "teams option <team> deathMessageVisibility hideForOwnTeam");

        // /seed
        addSyntax("/seed", "");

        // /setblock
        addSyntax("/setblock",
                "<x> <y> <z> <tileName>",
                "<x> <y> <z> <tileName> <dataValue>",
                "<x> <y> <z> <tileName> <dataValue> replace",
                "<x> <y> <z> <tileName> <dataValue> keep",
                "<x> <y> <z> <tileName> <dataValue> destroy",
                "<x> <y> <z> <tileName> <dataValue> replace <dataTag>",
                "<x> <y> <z> <tileName> <dataValue> keep <dataTag>",
                "<x> <y> <z> <tileName> <dataValue> destroy <dataTag>");

        // /setidletimeout
        addSyntax("/setidletimeout", "<minutes>");

        // /setworldspawn
        addSyntax("/setworldspawn", "", "<x> <y> <z>");

        // /spawnpoint
        addSyntax("/spawnpoint", "", "<player>", "<player> <x> <y> <z>");

        // /spreadplayers
        addSyntax("/spreadplayers",
                "<x> <z> <spreadDistance> <maxRange> true <player>",
                "<x> <z> <spreadDistance> <maxRange> false <player>");

        // /stats
        addSyntax("/stats",
                "block <x> <y> <z> clear",
                "block <x> <y> <z> set AffectedBlocks <selector> <objective>",
                "block <x> <y> <z> set AffectedEntities <selector> <objective>",
                "block <x> <y> <z> set AffectedItems <selector> <objective>",
                "block <x> <y> <z> set QueryResult <selector> <objective>",
                "block <x> <y> <z> set SuccessCount <selector> <objective>",
                "entity <selector> clear",
                "entity <selector> set AffectedBlocks <selector> <objective>",
                "entity <selector> set AffectedEntities <selector> <objective>",
                "entity <selector> set AffectedItems <selector> <objective>",
                "entity <selector> set QueryResult <selector> <objective>",
                "entity <selector> set SuccessCount <selector> <objective>");

        // /stop
        addSyntax("/stop", "");

        // /summon
        addSyntax("/summon",
                "<entityName>",
                "<entityName> <x> <y> <z>",
                "<entityName> <x> <y> <z> <dataTag>");

        // /tell
        addSyntax("/tell", "<player> <message>");

        // /tellraw
        addSyntax("/tellraw", "<player> <rawJson>");

        // /testfor
        addSyntax("/testfor", "<player>", "<player> <dataTag>");

        // /testforblock
        addSyntax("/testforblock",
                "<x> <y> <z> <tileName>",
                "<x> <y> <z> <tileName> <dataValue>",
                "<x> <y> <z> <tileName> <dataValue> <dataTag>");

        // /testforblocks
        addSyntax("/testforblocks",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z>",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> all",
                "<x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> masked");

        // /time
        addSyntax("/time",
                "add <value>",
                "set day", "set night", "set <value>",
                "query daytime", "query gametime");

        // /title
        addSyntax("/title",
                "<player> clear",
                "<player> reset",
                "<player> title <rawJson>",
                "<player> subtitle <rawJson>",
                "<player> actionbar <rawJson>",
                "<player> times <fadeIn> <stay> <fadeOut>");

        // /toggledownfall
        addSyntax("/toggledownfall", "");

        // /tp
        addSyntax("/tp",
                "<target>",
                "<player> <target>",
                "<x> <y> <z>",
                "<player> <x> <y> <z>",
                "<player> <x> <y> <z> <yRot> <xRot>");

        // /teleport
        addSyntax("/teleport",
                "<target>",
                "<player> <target>",
                "<x> <y> <z>",
                "<player> <x> <y> <z>",
                "<player> <x> <y> <z> <yRot> <xRot>");

        // /trigger
        addSyntax("/trigger",
                "<objective> add <value>",
                "<objective> set <value>");

        // /w
        addSyntax("/w", "<player> <message>");

        // /weather
        addSyntax("/weather",
                "clear", "rain", "thunder",
                "clear <duration>", "rain <duration>", "thunder <duration>");

        // /whitelist
        addSyntax("/whitelist",
                "add <player>", "remove <player>",
                "list", "on", "off", "reload");

        // /worldborder
        addSyntax("/worldborder",
                "add <sizeInBlocks>",
                "add <sizeInBlocks> <timeInSeconds>",
                "set <sizeInBlocks>",
                "set <sizeInBlocks> <timeInSeconds>",
                "center <x> <z>",
                "damage amount <damagePerBlock>",
                "damage buffer <sizeInBlocks>",
                "warning distance <sizeInBlocks>",
                "warning time <timeInSeconds>",
                "get");

        // /xp
        addSyntax("/xp",
                "<amount>",
                "<amount> <player>",
                "<amount>L",
                "<amount>L <player>");

        COMMAND_NAMES = new ArrayList<String>(COMMAND_SYNTAX.keySet());
        Collections.sort(COMMAND_NAMES);

        // Enchantment IDs (1.8.9)
        ENCHANTMENT_NAMES.put(0, "Protection");
        ENCHANTMENT_NAMES.put(1, "Fire Protection");
        ENCHANTMENT_NAMES.put(2, "Feather Falling");
        ENCHANTMENT_NAMES.put(3, "Blast Protection");
        ENCHANTMENT_NAMES.put(4, "Projectile Protection");
        ENCHANTMENT_NAMES.put(5, "Respiration");
        ENCHANTMENT_NAMES.put(6, "Aqua Affinity");
        ENCHANTMENT_NAMES.put(7, "Thorns");
        ENCHANTMENT_NAMES.put(8, "Depth Strider");
        ENCHANTMENT_NAMES.put(16, "Sharpness");
        ENCHANTMENT_NAMES.put(17, "Smite");
        ENCHANTMENT_NAMES.put(18, "Bane of Arthropods");
        ENCHANTMENT_NAMES.put(19, "Knockback");
        ENCHANTMENT_NAMES.put(20, "Fire Aspect");
        ENCHANTMENT_NAMES.put(21, "Looting");
        ENCHANTMENT_NAMES.put(32, "Efficiency");
        ENCHANTMENT_NAMES.put(33, "Silk Touch");
        ENCHANTMENT_NAMES.put(34, "Unbreaking");
        ENCHANTMENT_NAMES.put(35, "Fortune");
        ENCHANTMENT_NAMES.put(48, "Power");
        ENCHANTMENT_NAMES.put(49, "Punch");
        ENCHANTMENT_NAMES.put(50, "Flame");
        ENCHANTMENT_NAMES.put(51, "Infinity");
        ENCHANTMENT_NAMES.put(61, "Luck of the Sea");
        ENCHANTMENT_NAMES.put(62, "Lure");

        // Potion effect IDs (1.8.9)
        EFFECT_NAMES.put(1, "Speed");
        EFFECT_NAMES.put(2, "Slowness");
        EFFECT_NAMES.put(3, "Haste");
        EFFECT_NAMES.put(4, "Mining Fatigue");
        EFFECT_NAMES.put(5, "Strength");
        EFFECT_NAMES.put(6, "Instant Health");
        EFFECT_NAMES.put(7, "Instant Damage");
        EFFECT_NAMES.put(8, "Jump Boost");
        EFFECT_NAMES.put(9, "Nausea");
        EFFECT_NAMES.put(10, "Regeneration");
        EFFECT_NAMES.put(11, "Resistance");
        EFFECT_NAMES.put(12, "Fire Resistance");
        EFFECT_NAMES.put(13, "Water Breathing");
        EFFECT_NAMES.put(14, "Invisibility");
        EFFECT_NAMES.put(15, "Blindness");
        EFFECT_NAMES.put(16, "Night Vision");
        EFFECT_NAMES.put(17, "Hunger");
        EFFECT_NAMES.put(18, "Weakness");
        EFFECT_NAMES.put(19, "Poison");
        EFFECT_NAMES.put(20, "Wither");
        EFFECT_NAMES.put(21, "Health Boost");
        EFFECT_NAMES.put(22, "Absorption");
        EFFECT_NAMES.put(23, "Saturation");
    }

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
    private void modernchat$addSuggestion(String display, String completion, Set<String> seen, String key) {
        if (seen.add(key)) {
            this.suggestionDisplays.add(display);
            this.suggestionCompletions.add(completion);
        }
    }

    // --- Core suggestion computation ---

    @Unique
    private void modernchat$computeSuggestions(String text) {
        this.suggestionDisplays.clear();
        this.suggestionCompletions.clear();
        this.scrollOffset = 0;

        if (!text.startsWith("/") || text.length() < 2) {
            this.selectedIndex = -1;
            return;
        }

        if (!text.contains(" ")) {
            String prefix = text.toLowerCase();
            for (String cmd : COMMAND_NAMES) {
                if (cmd.startsWith(prefix) && !cmd.equals(prefix)) {
                    this.suggestionDisplays.add(cmd);
                    this.suggestionCompletions.add(cmd);
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
                            seen, "p:" + name);
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
                                seen, "s:" + sel[0]);
                        anyMatch = true;
                    }
                }

                if (!anyMatch) {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken);
                }

            } else if (modernchat$isCoordToken(nextToken) && COORD_COMMANDS.contains(command)) {
                // Coordinate suggestion from crosshair
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
                                seen, "c:" + nextToken);
                    }
                } else {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken);
                }

            } else if (BLOCK_TOKENS.contains(nextToken)) {
                List<String> blocks = modernchat$getBlockNames();
                boolean anyMatch = false;
                for (String name : blocks) {
                    if (!trailingSpace && !name.toLowerCase().startsWith(partial)) continue;
                    modernchat$addSuggestion(name,
                            modernchat$buildCompletion(parts, argTargetPos, name),
                            seen, "b:" + name);
                    anyMatch = true;
                }
                if (!anyMatch) {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken);
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
                            seen, "e:" + idStr);
                    anyMatch = true;
                }
                if (!anyMatch) {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken);
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
                            seen, "fx:" + idStr);
                    anyMatch = true;
                }
                if (!anyMatch) {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken);
                }

            } else if (ENTITY_NAME_TOKENS.contains(nextToken)) {
                List<String> entities = modernchat$getEntityNames();
                boolean anyMatch = false;
                for (String name : entities) {
                    if (!trailingSpace && !name.toLowerCase().startsWith(partial)) continue;
                    modernchat$addSuggestion(name,
                            modernchat$buildCompletion(parts, argTargetPos, name),
                            seen, "en:" + name);
                    anyMatch = true;
                }
                if (!anyMatch) {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken);
                }

            } else if (ITEM_TOKENS.contains(nextToken)) {
                List<String> items = modernchat$getItemNames();
                boolean anyMatch = false;
                for (String name : items) {
                    String lowerName = name.toLowerCase();
                    boolean fullMatches = trailingSpace || lowerName.startsWith(partial);
                    // Also offer the bare name without the "minecraft:" namespace prefix
                    String shortName = lowerName.startsWith("minecraft:") ? lowerName.substring(10) : null;
                    boolean shortMatches = shortName != null && (trailingSpace || shortName.startsWith(partial));
                    if (fullMatches) {
                        modernchat$addSuggestion(name,
                                modernchat$buildCompletion(parts, argTargetPos, name),
                                seen, "it:" + name);
                        anyMatch = true;
                    }
                    if (shortMatches) {
                        String shortDisplay = name.substring(10);
                        modernchat$addSuggestion(shortDisplay,
                                modernchat$buildCompletion(parts, argTargetPos, shortDisplay),
                                seen, "its:" + shortDisplay);
                        anyMatch = true;
                    }
                }
                if (!anyMatch) {
                    modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken);
                }

            } else if (modernchat$isPlaceholder(nextToken)) {
                // Generic placeholder hint (non-completable)
                modernchat$addSuggestion(nextToken, null, seen, "h:" + nextToken);

            } else {
                // Literal keyword
                if (!trailingSpace && !nextToken.toLowerCase().startsWith(partial)) continue;
                modernchat$addSuggestion(nextToken,
                        modernchat$buildCompletion(parts, argTargetPos, nextToken),
                        seen, "l:" + nextToken);
            }
        }
    }

    // --- Render ---

    @Inject(method = "render", at = @At("TAIL"))
    private void modernchat$renderAutocomplete(int mouseX, int mouseY, float delta, CallbackInfo ci) {
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

            int textColor;
            if (isHint) {
                textColor = 0xFF777777;
            } else {
                textColor = (idx == this.selectedIndex) ? 0xFFFFFF00 : 0xFFAAAAAA;
            }
            tr.drawWithShadow(this.suggestionDisplays.get(idx), boxX + 4, entryY + 2, textColor);
        }
    }

    // --- Key handling ---

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void modernchat$onKeyPressed(char chr, int keyCode, CallbackInfo ci) {
        if (this.suggestionDisplays.isEmpty()) return;

        if (keyCode == 15) { // Tab
            int idx = this.selectedIndex >= 0 ? this.selectedIndex : 0;
            if (idx < this.suggestionCompletions.size()) {
                String completion = this.suggestionCompletions.get(idx);
                if (completion != null) {
                    this.chatField.setText(completion);
                    this.suggestionDisplays.clear();
                    this.suggestionCompletions.clear();
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
