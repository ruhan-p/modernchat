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
 * Left list shows servers, right shows friends. To add a friend, select the server
 * and enter the name. You can also copy friend lists from other servers.
 */
public class AutocompleteFriendsScreen extends Screen {

    private final Screen parent;

    private List<CommandSyntaxDef> entries = new ArrayList<CommandSyntaxDef>();
    private int selectedIndex      = -1;
    private int serverScrollOffset = 0;
    private int friendScrollOffset = 0;

    private int lastMouseX = 0;
    private int lastMouseY = 0;

    private static final int LIST_TOP     = 30;
    private static final int HEADER_H     = 14;
    private static final int ROW_HEIGHT   = 24;
    private static final int FRIEND_ROW_H = 16;
    private static final int SCROLLBAR_W  = 4;

    private int splitX;
    private int listBottom;

    private int leftX;
    private int leftRight;
    private int leftContentRight;
    private int maxServerVisible;

    private int rightX;
    private int rightRight;
    private int rightContentRight;
    private int friendsListTop;
    private int addRowY;
    private int copyBtnY;
    private int maxFriendVisible;

    private TextFieldWidget addFriendField;
    private static final int ID_ADD  = 1;
    private static final int ID_DONE = 2;
    private static final int ID_COPY = 3;

    // 0 = normal, 1 = dropdown open, 2 = confirmation overlay showing
    private int copyState = 0;
    private int pendingCopySourceIndex  = -1;
    private int copyDropdownScrollOffset = 0;

    public AutocompleteFriendsScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        entries.clear();
        selectedIndex      = -1;
        serverScrollOffset = 0;
        friendScrollOffset = 0;
        copyState          = 0;
        pendingCopySourceIndex   = -1;
        copyDropdownScrollOffset = 0;

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
        copyBtnY = listBottom - 26;
        addRowY  = listBottom - 50;

        maxServerVisible = Math.max(1, (listBottom - friendsListTop) / ROW_HEIGHT);
        maxFriendVisible = Math.max(1, (addRowY - friendsListTop) / FRIEND_ROW_H);

        int addFieldW = rightContentRight - rightX - 4 - 36;
        addFriendField = new TextFieldWidget(
                0, this.textRenderer,
                rightX, addRowY, addFieldW, 20);
        addFriendField.setMaxLength(16);

        this.buttons.add(new ButtonWidget(ID_ADD,
                rightX + addFieldW + 4, addRowY - 1, 36, 20, "Add"));

        int copyBtnW = rightContentRight - rightX;
        this.buttons.add(new ButtonWidget(ID_COPY,
                rightX, copyBtnY, copyBtnW, 20, "Copy from..."));

        this.buttons.add(new ButtonWidget(ID_DONE,
                this.width / 2 - 75, this.height - 26, 150, 20, "Done"));
    }

    @Override
    public void tick() {
        addFriendField.tick();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        boolean hasSelection = (selectedIndex >= 0 && selectedIndex < entries.size());
        for (Object obj : this.buttons) {
            ButtonWidget btn = (ButtonWidget) obj;
            if (btn.id == ID_ADD || btn.id == ID_COPY) {
                btn.active = hasSelection;
            }
        }
        addFriendField.setEditable(hasSelection);

        this.renderBackground();
        this.drawCenteredString(this.textRenderer,
                "Autocomplete Friends", this.width / 2, 10, 0xFFFFFF);

        renderServerPanel(mouseX, mouseY);
        renderFriendsPanel(mouseX, mouseY);

        this.fill(splitX - 1, LIST_TOP - 2, splitX + 1, listBottom, 0x55FFFFFF);

        this.fill(4, listBottom, this.width - 4, listBottom + 1, 0x55FFFFFF);

        addFriendField.render();
        super.render(mouseX, mouseY, delta);

        if (copyState == 1) {
            renderCopyDropdown(mouseX, mouseY);
        } else if (copyState == 2) {
            renderCopyConfirm(mouseX, mouseY);
        }
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

        if (entries.size() > maxServerVisible) {
            int sbX      = leftRight - SCROLLBAR_W;
            int sbTop    = friendsListTop;
            int sbBottom = listBottom;
            int sbHeight = sbBottom - sbTop;
            int thumbH   = Math.max(10, sbHeight * maxServerVisible / entries.size());
            int maxOff   = entries.size() - maxServerVisible;
            int thumbY   = sbTop + (sbHeight - thumbH) * serverScrollOffset / Math.max(1, maxOff);
            this.fill(sbX, sbTop, sbX + SCROLLBAR_W, sbBottom, 0x33FFFFFF);
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

    private void renderCopyDropdown(int mouseX, int mouseY) {
        List<Integer> copyable = buildCopyableList();

        int panelX      = rightX;
        int panelRight  = rightContentRight;
        int panelTop    = LIST_TOP;
        int panelBottom = addRowY - 4;

        this.fill(panelX - 1, panelTop - 1, panelRight + 1, panelBottom + 1, 0xFF555555);
        this.fill(panelX, panelTop, panelRight, panelBottom, 0xF0111111);

        this.drawWithShadow(this.textRenderer,
                "Copy friends from:", panelX + 4, panelTop + 3, 0xAAAAAA);
        int listTop = panelTop + HEADER_H + 2;
        this.fill(panelX, listTop - 1, panelRight, listTop, 0x44FFFFFF);

        int maxVisible   = Math.max(1, (panelBottom - listTop) / FRIEND_ROW_H);
        int visibleCount = Math.min(maxVisible, copyable.size() - copyDropdownScrollOffset);

        if (copyable.isEmpty()) {
            this.drawWithShadow(this.textRenderer,
                    "No other servers.", panelX + 4, listTop + 3, 0x666666);
        }

        for (int i = 0; i < visibleCount; i++) {
            int entryIdx = copyable.get(copyDropdownScrollOffset + i);
            CommandSyntaxDef def = entries.get(entryIdx);
            int rowY = listTop + i * FRIEND_ROW_H;

            boolean isHovered = mouseX >= panelX && mouseX <= panelRight
                    && mouseY >= rowY && mouseY < rowY + FRIEND_ROW_H;
            if (isHovered) {
                this.fill(panelX, rowY, panelRight, rowY + FRIEND_ROW_H, 0x44FFFFFF);
            }

            String name = (def.name != null && !def.name.isEmpty())
                    ? Character.toUpperCase(def.name.charAt(0)) + def.name.substring(1)
                    : "Unknown";
            int friendCount = (def.friends != null) ? def.friends.size() : 0;
            String label = name + " (" + friendCount + " friend" + (friendCount == 1 ? "" : "s") + ")";
            label = this.textRenderer.trimToWidth(label, panelRight - panelX - 8);
            this.drawWithShadow(this.textRenderer, label,
                    panelX + 4, rowY + 3, isHovered ? 0xFFFF55 : 0xFFFFFF);
        }

        if (copyable.size() > maxVisible) {
            int sbX    = panelRight - SCROLLBAR_W;
            int sbH    = panelBottom - listTop;
            int thumbH = Math.max(8, sbH * maxVisible / copyable.size());
            int maxOff = copyable.size() - maxVisible;
            int thumbY = listTop + (sbH - thumbH) * copyDropdownScrollOffset / Math.max(1, maxOff);
            this.fill(sbX, listTop, sbX + SCROLLBAR_W, panelBottom, 0x33FFFFFF);
            this.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    private void renderCopyConfirm(int mouseX, int mouseY) {
        if (pendingCopySourceIndex < 0 || pendingCopySourceIndex >= entries.size()) return;

        CommandSyntaxDef src = entries.get(pendingCopySourceIndex);
        String srcName = (src.name != null && !src.name.isEmpty())
                ? Character.toUpperCase(src.name.charAt(0)) + src.name.substring(1)
                : "Unknown";

        int boxW = 180;
        int boxH = 64;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;

        this.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xFF555555);
        this.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF1A1A1A);

        this.drawCenteredString(this.textRenderer,
                "Copy friends from", this.width / 2, boxY + 8, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer,
                srcName + "?", this.width / 2, boxY + 20, 0xFFFF55);

        int btnY  = boxY + boxH - 22;
        int yesX  = boxX + 16;
        int noX   = boxX + boxW - 66;
        int btnW  = 50;
        int btnH  = 14;

        boolean yesHover = mouseX >= yesX && mouseX < yesX + btnW
                && mouseY >= btnY && mouseY < btnY + btnH;
        boolean noHover  = mouseX >= noX  && mouseX < noX  + btnW
                && mouseY >= btnY && mouseY < btnY + btnH;

        this.fill(yesX, btnY, yesX + btnW, btnY + btnH,
                yesHover ? 0xFF228822 : 0xFF114411);
        this.fill(noX, btnY, noX + btnW, btnY + btnH,
                noHover ? 0xFF882222 : 0xFF441111);

        this.drawCenteredString(this.textRenderer, "Yes",    yesX + btnW / 2, btnY + 3, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer, "Cancel", noX  + btnW / 2, btnY + 3, 0xFFFFFF);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == ID_DONE) {
            this.client.setScreen(parent);
        } else if (button.id == ID_ADD) {
            addFriend();
        } else if (button.id == ID_COPY) {
            copyState = 1;
            copyDropdownScrollOffset = 0;
            pendingCopySourceIndex   = -1;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (copyState == 1) {
            List<Integer> copyable = buildCopyableList();

            int panelX      = rightX;
            int panelRight  = rightContentRight;
            int panelTop    = LIST_TOP;
            int panelBottom = addRowY - 4;
            int listTop     = panelTop + HEADER_H + 2;
            int maxVisible  = Math.max(1, (panelBottom - listTop) / FRIEND_ROW_H);
            int visibleCount = Math.min(maxVisible, copyable.size() - copyDropdownScrollOffset);

            boolean inPanel = mouseX >= panelX && mouseX <= panelRight
                    && mouseY >= panelTop && mouseY < panelBottom;

            if (inPanel) {
                for (int i = 0; i < visibleCount; i++) {
                    int rowY = listTop + i * FRIEND_ROW_H;
                    if (mouseY >= rowY && mouseY < rowY + FRIEND_ROW_H) {
                        pendingCopySourceIndex = copyable.get(copyDropdownScrollOffset + i);
                        copyState = 2;
                        return;
                    }
                }
            } else {
                copyState = 0;
            }
            return;
        }

        if (copyState == 2) {
            int boxW = 180;
            int boxH = 64;
            int boxX = (this.width - boxW) / 2;
            int boxY = (this.height - boxH) / 2;
            int btnY = boxY + boxH - 22;
            int yesX = boxX + 16;
            int noX  = boxX + boxW - 66;
            int btnW = 50;
            int btnH = 14;

            if (mouseX >= yesX && mouseX < yesX + btnW
                    && mouseY >= btnY && mouseY < btnY + btnH) {
                copyFriendsFrom(pendingCopySourceIndex);
                copyState = 0;
                pendingCopySourceIndex = -1;
                return;
            }

            if (mouseX >= noX && mouseX < noX + btnW
                    && mouseY >= btnY && mouseY < btnY + btnH) {
                copyState = 0;
                pendingCopySourceIndex = -1;
                return;
            }

            boolean inBox = mouseX >= boxX && mouseX <= boxX + boxW
                    && mouseY >= boxY && mouseY <= boxY + boxH;
            if (!inBox) {
                copyState = 0;
                pendingCopySourceIndex = -1;
            }
            return;
        }

        addFriendField.mouseClicked(mouseX, mouseY, button);

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

        if (copyState == 1) {
            List<Integer> copyable = buildCopyableList();
            int listTop    = LIST_TOP + HEADER_H + 2;
            int panelBottom = addRowY - 4;
            int maxVisible = Math.max(1, (panelBottom - listTop) / FRIEND_ROW_H);
            if (wheel > 0) {
                copyDropdownScrollOffset = Math.max(0, copyDropdownScrollOffset - 1);
            } else {
                int maxOff = Math.max(0, copyable.size() - maxVisible);
                copyDropdownScrollOffset = Math.min(maxOff, copyDropdownScrollOffset + 1);
            }
            return;
        }

        if (lastMouseX < splitX) {
            if (wheel > 0) {
                serverScrollOffset = Math.max(0, serverScrollOffset - 1);
            } else {
                int maxOff = Math.max(0, entries.size() - maxServerVisible);
                serverScrollOffset = Math.min(maxOff, serverScrollOffset + 1);
            }
        } else {
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
        if (keyCode == 1) { // Escape
            if (copyState != 0) {
                copyState = 0;
                pendingCopySourceIndex = -1;
                return;
            }
        }

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

    private List<Integer> buildCopyableList() {
        List<Integer> list = new ArrayList<Integer>();
        for (int i = 0; i < entries.size(); i++) {
            if (i != selectedIndex) list.add(i);
        }
        return list;
    }

    private void copyFriendsFrom(int sourceIndex) {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) return;
        if (sourceIndex  < 0 || sourceIndex  >= entries.size()) return;

        CommandSyntaxDef dest = entries.get(selectedIndex);
        CommandSyntaxDef src  = entries.get(sourceIndex);

        if (src.friends == null || src.friends.isEmpty()) return;
        if (dest.friends == null) dest.friends = new ArrayList<String>();

        for (String friend : src.friends) {
            boolean exists = false;
            for (String existing : dest.friends) {
                if (existing.equalsIgnoreCase(friend)) { exists = true; break; }
            }
            if (!exists) dest.friends.add(friend);
        }

        CommandSyntaxLoader.saveFriends(dest.sourceFile, dest.friends);
    }
}
