package com.infloat.modernchat;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * Config screen that lets the user manage the friends list for each server.
 * Friends are persisted in the server's JSON file and are offered as player-name
 * autocomplete suggestions even when those players are not visible in the tablist.
 *
 * Layout: two side-by-side panels separated by a vertical divider.
 *   Left  — scrollable server list (click to select).
 *   Right — scrollable friends list for the selected server, with an add field
 *           pinned to the bottom of the panel.
 * Each panel has its own scrollbar; the mouse wheel scrolls whichever panel the
 * cursor is currently over.
 */
public class AutocompleteFriendsScreen extends Screen {

    private final Screen parent;

    private List<CommandSyntaxDef> entries = new ArrayList<CommandSyntaxDef>();
    private int selectedIndex      = -1;
    private int serverScrollOffset = 0;
    private int friendScrollOffset = 0;

    /** Updated every render() so handleMouse() knows which panel to scroll. */
    private int lastMouseX = 0;

    // ── Layout constants ────────────────────────────────────────────────────
    private static final int LIST_TOP     = 30;  // y of both panel headers
    private static final int HEADER_H     = 14;  // height of the header row
    private static final int ROW_HEIGHT   = 24;  // server rows (name + count)
    private static final int FRIEND_ROW_H = 16;  // friend rows
    private static final int SCROLLBAR_W  = 4;

    // ── Computed in init() ──────────────────────────────────────────────────
    private int splitX;          // x of the vertical divider
    private int listBottom;      // y where both panels end

    // Left panel
    private int leftX;           // = 4
    private int leftRight;       // = splitX - 3
    private int leftContentRight;// leftRight - SCROLLBAR_W - 2  (excludes scrollbar)
    private int maxServerVisible;

    // Right panel
    private int rightX;          // = splitX + 3
    private int rightRight;      // = this.width - 4
    private int rightContentRight;// rightRight - SCROLLBAR_W - 2  (excludes scrollbar)
    private int friendsListTop;  // y where friend rows start
    private int addRowY;         // y of the add-friend field / button
    private int maxFriendVisible;

    private TextFieldWidget addFriendField;
    private static final int ID_ADD  = 1;
    private static final int ID_DONE = 2;

    public AutocompleteFriendsScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        entries.clear();
        selectedIndex      = -1;
        serverScrollOffset = 0;
        friendScrollOffset = 0;

        entries = CommandSyntaxLoader.loadAllDefs();

        splitX    = this.width / 2;
        listBottom = this.height - 32;

        leftX            = 4;
        leftRight        = splitX - 3;
        leftContentRight = leftRight - SCROLLBAR_W - 2;

        rightX             = splitX + 3;
        rightRight         = this.width - 4;
        rightContentRight  = rightRight - SCROLLBAR_W - 2;

        friendsListTop   = LIST_TOP + HEADER_H;
        addRowY          = listBottom - 26;

        maxServerVisible = Math.max(1, (listBottom - friendsListTop) / ROW_HEIGHT);
        maxFriendVisible = Math.max(1, (addRowY - friendsListTop) / FRIEND_ROW_H);

        // Add-friend field: fills right content width minus button gap + button
        int addFieldW = rightContentRight - rightX - 4 - 36;
        addFriendField = new TextFieldWidget(
                0, this.textRenderer,
                rightX, addRowY, addFieldW, 20);
        addFriendField.setMaxLength(16);

        this.buttons.add(new ButtonWidget(ID_ADD,
                rightX + addFieldW + 4, addRowY - 1, 36, 20, "Add"));

        this.buttons.add(new ButtonWidget(ID_DONE,
                this.width / 2 - 75, this.height - 26, 150, 20, "Done"));
    }

    // ── Tick ────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        addFriendField.tick();
    }

    // ── Render ──────────────────────────────────────────────────────────────

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;

        this.renderBackground();
        this.drawCenteredString(this.textRenderer,
                "Autocomplete Friends", this.width / 2, 10, 0xFFFFFF);

        renderServerPanel(mouseX, mouseY);
        renderFriendsPanel(mouseX, mouseY);

        // Vertical divider between the two panels
        this.fill(splitX - 1, LIST_TOP - 2, splitX + 1, listBottom, 0x55FFFFFF);

        // Horizontal separator above Done button
        this.fill(4, listBottom, this.width - 4, listBottom + 1, 0x55FFFFFF);

        addFriendField.render();
        super.render(mouseX, mouseY, delta);
    }

    private void renderServerPanel(int mouseX, int mouseY) {
        this.drawWithShadow(this.textRenderer, "Servers", leftX, LIST_TOP, 0xAAAAAA);

        if (entries.isEmpty()) {
            this.drawWithShadow(this.textRenderer,
                    "No files found.", leftX, friendsListTop + 2, 0x666666);
            return;
        }

        int visibleCount = Math.min(maxServerVisible, entries.size() - serverScrollOffset);

        for (int i = 0; i < visibleCount; i++) {
            int entryIndex = serverScrollOffset + i;
            CommandSyntaxDef def = entries.get(entryIndex);
            int rowY = friendsListTop + i * ROW_HEIGHT;

            boolean isSelected = (entryIndex == selectedIndex);
            boolean isHovered  = mouseX >= leftX && mouseX <= leftContentRight
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 1;

            if (isSelected) {
                this.fill(leftX, rowY, leftContentRight, rowY + ROW_HEIGHT - 1, 0x882244AA);
            } else if (isHovered) {
                this.fill(leftX, rowY, leftContentRight, rowY + ROW_HEIGHT - 1, 0x33FFFFFF);
            }

            String name = (def.name != null && !def.name.isEmpty())
                    ? Character.toUpperCase(def.name.charAt(0)) + def.name.substring(1)
                    : "Unknown";
            name = this.textRenderer.trimToWidth(name, leftContentRight - leftX - 6);
            this.drawWithShadow(this.textRenderer, name,
                    leftX + 4, rowY + 3, isSelected ? 0xFFFF55 : 0xFFFFFF);

            int friendCount = (def.friends != null) ? def.friends.size() : 0;
            String countStr = friendCount + " friend" + (friendCount == 1 ? "" : "s");
            this.drawWithShadow(this.textRenderer, countStr,
                    leftX + 4, rowY + 13, 0x777777);
        }

        // Scrollbar
        if (entries.size() > maxServerVisible) {
            int sbX      = leftRight - SCROLLBAR_W;
            int sbTop    = friendsListTop;
            int sbHeight = listBottom - sbTop;
            int thumbH   = Math.max(10, sbHeight * maxServerVisible / entries.size());
            int maxOff   = entries.size() - maxServerVisible;
            int thumbY   = sbTop + (sbHeight - thumbH) * serverScrollOffset / Math.max(1, maxOff);
            this.fill(sbX, sbTop, sbX + SCROLLBAR_W, listBottom, 0x33FFFFFF);
            this.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    private void renderFriendsPanel(int mouseX, int mouseY) {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            this.drawWithShadow(this.textRenderer,
                    "Select a server \u2190", rightX, LIST_TOP, 0x888888);
            return;
        }

        CommandSyntaxDef def = entries.get(selectedIndex);
        String serverName = (def.name != null && !def.name.isEmpty())
                ? Character.toUpperCase(def.name.charAt(0)) + def.name.substring(1)
                : "Unknown";

        this.drawWithShadow(this.textRenderer,
                "Friends \u2014 " + serverName, rightX, LIST_TOP, 0xAAAAAA);

        List<String> friends = (def.friends != null) ? def.friends : new ArrayList<String>();

        if (friends.isEmpty()) {
            this.drawWithShadow(this.textRenderer,
                    "No friends yet.", rightX, friendsListTop + 2, 0x666666);
        }

        int visibleCount = Math.min(maxFriendVisible, friends.size() - friendScrollOffset);

        for (int i = 0; i < visibleCount; i++) {
            int fi    = friendScrollOffset + i;
            String fr = friends.get(fi);
            int rowY  = friendsListTop + i * FRIEND_ROW_H;

            boolean isHovered = mouseX >= rightX && mouseX <= rightContentRight
                    && mouseY >= rowY && mouseY < rowY + FRIEND_ROW_H;
            if (isHovered) {
                this.fill(rightX, rowY, rightContentRight, rowY + FRIEND_ROW_H, 0x22FFFFFF);
            }

            this.drawWithShadow(this.textRenderer, fr, rightX + 4, rowY + 3, 0xFFFFFF);

            String removeText = "[-]";
            int removeX = rightContentRight - this.textRenderer.getStringWidth(removeText) - 2;
            this.drawWithShadow(this.textRenderer, removeText, removeX, rowY + 3, 0xFF4444);
        }

        // Scrollbar for friends list
        if (friends.size() > maxFriendVisible) {
            int sbX      = rightRight - SCROLLBAR_W;
            int sbTop    = friendsListTop;
            int sbHeight = addRowY - sbTop - 4;
            int thumbH   = Math.max(10, sbHeight * maxFriendVisible / friends.size());
            int maxOff   = friends.size() - maxFriendVisible;
            int thumbY   = sbTop + (sbHeight - thumbH) * friendScrollOffset / Math.max(1, maxOff);
            this.fill(sbX, sbTop, sbX + SCROLLBAR_W, addRowY - 4, 0x33FFFFFF);
            this.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    // ── Input ───────────────────────────────────────────────────────────────

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == ID_DONE) {
            this.client.setScreen(parent);
        } else if (button.id == ID_ADD) {
            addFriend();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        addFriendField.mouseClicked(mouseX, mouseY, button);

        // Left panel: server selection
        if (mouseX >= leftX && mouseX <= leftContentRight
                && mouseY >= friendsListTop && mouseY < listBottom) {
            int clickedRow = (mouseY - friendsListTop) / ROW_HEIGHT;
            int entryIndex = serverScrollOffset + clickedRow;
            if (entryIndex >= 0 && entryIndex < entries.size()
                    && entryIndex != selectedIndex) {
                selectedIndex      = entryIndex;
                friendScrollOffset = 0;
                addFriendField.setText("");
                addFriendField.setFocused(false);
            }
        }

        // Right panel: remove-friend click
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            CommandSyntaxDef def = entries.get(selectedIndex);
            List<String> friends = (def.friends != null) ? def.friends : new ArrayList<String>();
            int visibleCount = Math.min(maxFriendVisible, friends.size() - friendScrollOffset);

            for (int i = 0; i < visibleCount; i++) {
                int rowY = friendsListTop + i * FRIEND_ROW_H;
                if (mouseY >= rowY && mouseY < rowY + FRIEND_ROW_H
                        && mouseX >= rightX && mouseX <= rightContentRight) {
                    String removeText = "[-]";
                    int removeX = rightContentRight - this.textRenderer.getStringWidth(removeText) - 2;
                    if (mouseX >= removeX - 2) {
                        int fi = friendScrollOffset + i;
                        if (fi >= 0 && fi < friends.size()) {
                            friends.remove(fi);
                            if (def.friends == null) def.friends = friends;
                            int maxOff = Math.max(0, friends.size() - maxFriendVisible);
                            friendScrollOffset = Math.min(friendScrollOffset, maxOff);
                            CommandSyntaxLoader.saveFriends(def.sourceFile, friends);
                        }
                    }
                    break;
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;

        if (lastMouseX < splitX) {
            // Scroll server list
            if (wheel > 0) {
                serverScrollOffset = Math.max(0, serverScrollOffset - 1);
            } else {
                int maxOff = Math.max(0, entries.size() - maxServerVisible);
                serverScrollOffset = Math.min(maxOff, serverScrollOffset + 1);
            }
        } else {
            // Scroll friend list
            if (selectedIndex < 0 || selectedIndex >= entries.size()) return;
            CommandSyntaxDef def = entries.get(selectedIndex);
            List<String> friends = (def.friends != null) ? def.friends : new ArrayList<String>();
            if (wheel > 0) {
                friendScrollOffset = Math.max(0, friendScrollOffset - 1);
            } else {
                int maxOff = Math.max(0, friends.size() - maxFriendVisible);
                friendScrollOffset = Math.min(maxOff, friendScrollOffset + 1);
            }
        }
    }

    @Override
    protected void keyPressed(char chr, int keyCode) {
        if (addFriendField.isFocused()) {
            if (keyCode == 28) { // Enter
                addFriend();
                addFriendField.setFocused(false);
            } else if (keyCode == 1) { // Escape
                addFriendField.setFocused(false);
            } else {
                addFriendField.keyPressed(chr, keyCode);
            }
            return;
        }
        super.keyPressed(chr, keyCode);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void addFriend() {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) return;
        String name = addFriendField.getText().trim();
        if (name.isEmpty()) return;

        CommandSyntaxDef def = entries.get(selectedIndex);
        if (def.friends == null) def.friends = new ArrayList<String>();

        for (String existing : def.friends) {
            if (existing.equalsIgnoreCase(name)) return; // no duplicates
        }

        def.friends.add(name);
        CommandSyntaxLoader.saveFriends(def.sourceFile, def.friends);
        addFriendField.setText("");
    }
}
