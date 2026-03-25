package com.infloat.modernchat;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Browsable list of community server presets fetched from GitHub.
 * Selecting a preset and clicking Install downloads it and saves it to the
 * commands config directory, making it available immediately in the syntax editor.
 * Installed presets can be uninstalled, which also removes their friend list.
 */
public class AddServerPresetScreen extends Screen {

    private final Screen parent;

    // ---- State machine ------------------------------------------------------
    private static final int STATE_LOADING    = 0;
    private static final int STATE_READY      = 1;
    private static final int STATE_INSTALLING = 2;
    private static final int STATE_ERROR      = 3;

    // Written by background threads; read by the game thread each render frame.
    private volatile int                     state         = STATE_LOADING;
    private volatile List<ServerPresetEntry> presetList    = null;
    private volatile String                  statusMessage = "";
    private volatile boolean                 lastInstallOk = false;

    // ---- Selection & scroll -------------------------------------------------
    private int selectedIndex  = -1;
    private int scrollOffset   = 0;
    private boolean confirmUninstall = false;

    // ---- "Already installed" cache ------------------------------------------
    private Set<String> installedKeys = new HashSet<String>();

    // ---- Layout (computed in init) ------------------------------------------
    private static final int LIST_TOP    = 30;
    private static final int ROW_HEIGHT  = 22;
    private static final int SCROLLBAR_W = 5;
    private int listBottom;
    private int maxVisible;

    // ---- Button IDs ---------------------------------------------------------
    private static final int ID_INSTALL   = 1;
    private static final int ID_DONE      = 2;
    private static final int ID_UNINSTALL = 3;

    // =========================================================================

    public AddServerPresetScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        listBottom = this.height - 48;
        maxVisible = Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);

        // 3 buttons evenly spaced, each 80px wide
        int bStart = this.width / 2 - 122;
        this.buttons.add(new ButtonWidget(ID_INSTALL,   bStart,       this.height - 26, 80, 20, "Install"));
        this.buttons.add(new ButtonWidget(ID_UNINSTALL, bStart + 84,  this.height - 26, 80, 20, "Uninstall"));
        this.buttons.add(new ButtonWidget(ID_DONE,      bStart + 168, this.height - 26, 80, 20, "Done"));

        // Only start the fetch once — re-init on resize must not spawn a second thread.
        if (state == STATE_LOADING && presetList == null) {
            startFetch();
        }

        refreshButtons();
    }

    private void startFetch() {
        state = STATE_LOADING;
        PresetFetcher.fetchIndex(new PresetFetcher.Callback<List<ServerPresetEntry>>() {
            public void onResult(List<ServerPresetEntry> value, String error) {
                if (error != null) {
                    statusMessage = "Could not load presets: " + error;
                    state = STATE_ERROR;
                } else {
                    presetList = value;
                    rebuildInstalledKeys();
                    state = STATE_READY;
                }
            }
        });
    }

    private void rebuildInstalledKeys() {
        Set<String> keys = new HashSet<String>();
        List<ServerPresetEntry> list = presetList;
        if (list != null) {
            for (ServerPresetEntry e : list) {
                if (new File("config/modernchat/commands/" + e.key + ".json").exists()) {
                    keys.add(e.key);
                }
            }
        }
        installedKeys = keys;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, "Add Server Preset", this.width / 2, 10, 0xFFFFFF);

        refreshButtons();

        int mid = (listBottom + LIST_TOP) / 2 - 4;

        if (state == STATE_LOADING) {
            this.drawCenteredString(this.textRenderer, "Loading presets...", this.width / 2, mid, 0xAAAAAA);

        } else if (state == STATE_ERROR) {
            this.drawCenteredString(this.textRenderer, statusMessage, this.width / 2, mid, 0xFF5555);

        } else {
            renderList(mouseX, mouseY);

            // Status banner above buttons (transient install/uninstall feedback)
            if (!statusMessage.isEmpty() && state != STATE_INSTALLING) {
                int color = lastInstallOk ? 0x55FF55 : 0xFF5555;
                this.drawCenteredString(this.textRenderer, statusMessage,
                        this.width / 2, listBottom + 6, color);
            }

            if (state == STATE_INSTALLING) {
                this.drawCenteredString(this.textRenderer, "Installing...",
                        this.width / 2, listBottom + 6, 0xFFFF55);
            }
        }

        super.render(mouseX, mouseY, delta);

        // Confirm overlay rendered last so it appears on top
        if (confirmUninstall) renderConfirmUninstall(mouseX, mouseY);
    }

    private void renderList(int mx, int my) {
        List<ServerPresetEntry> list = presetList;
        if (list == null || list.isEmpty()) {
            this.drawCenteredString(this.textRenderer, "No presets available.",
                    this.width / 2, (listBottom + LIST_TOP) / 2 - 4, 0x666666);
            return;
        }

        int listRight = this.width - SCROLLBAR_W - 4;
        int vis = Math.min(maxVisible, list.size() - scrollOffset);

        for (int i = 0; i < vis; i++) {
            int idx  = scrollOffset + i;
            int rowY = LIST_TOP + i * ROW_HEIGHT;
            ServerPresetEntry e = list.get(idx);
            boolean sel       = (idx == selectedIndex);
            boolean installed = installedKeys.contains(e.key);
            boolean hov       = mx >= 4 && mx <= listRight && my >= rowY && my < rowY + ROW_HEIGHT;

            if (sel)       this.fill(4, rowY, listRight, rowY + ROW_HEIGHT, 0x882244AA);
            else if (hov)  this.fill(4, rowY, listRight, rowY + ROW_HEIGHT, 0x33FFFFFF);

            int nameColor = installed ? 0x888888 : 0xFFFFFF;
            this.drawWithShadow(this.textRenderer, e.name, 10, rowY + 6, nameColor);

            if (e.ip != null && !e.ip.isEmpty()) {
                int ipW = this.textRenderer.getStringWidth(e.ip);
                this.drawWithShadow(this.textRenderer, e.ip,
                        listRight - ipW - (installed ? 70 : 6), rowY + 6, 0x666666);
            }

            if (installed) {
                this.drawWithShadow(this.textRenderer, "Installed",
                        listRight - 64, rowY + 6, 0x55FF55);
            }
        }

        // Scrollbar
        if (list.size() > maxVisible) {
            int sbX = this.width - SCROLLBAR_W - 2;
            int h   = listBottom - LIST_TOP;
            int th  = Math.max(8, h * maxVisible / list.size());
            int maxOff = list.size() - maxVisible;
            int ty  = LIST_TOP + (h - th) * scrollOffset / Math.max(1, maxOff);
            this.fill(sbX, LIST_TOP, sbX + SCROLLBAR_W, listBottom, 0x33FFFFFF);
            this.fill(sbX, ty, sbX + SCROLLBAR_W, ty + th, 0xFFAAAAAA);
        }
    }

    private void renderConfirmUninstall(int mx, int my) {
        List<ServerPresetEntry> list = presetList;
        String name = (list != null && selectedIndex >= 0 && selectedIndex < list.size())
                ? list.get(selectedIndex).name : "?";

        int bW = 260, bH = 80, bX = (this.width - bW) / 2, bY = (this.height - bH) / 2;
        this.fill(bX - 1, bY - 1, bX + bW + 1, bY + bH + 1, 0xFF555555);
        this.fill(bX, bY, bX + bW, bY + bH, 0xFF1A1A1A);
        this.drawCenteredString(this.textRenderer, "Uninstall preset: " + name + "?",
                this.width / 2, bY + 8, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer, "This will also delete the friend list.",
                this.width / 2, bY + 22, 0xFFAA00);
        this.drawCenteredString(this.textRenderer, "This cannot be undone.",
                this.width / 2, bY + 34, 0xFF5555);

        int btnY = bY + bH - 20, dX = bX + 16, kX = bX + bW - 66, btnW = 50, btnH = 14;
        boolean dH = mx >= dX && mx < dX + btnW && my >= btnY && my < btnY + btnH;
        boolean kH = mx >= kX && mx < kX + btnW && my >= btnY && my < btnY + btnH;
        this.fill(dX, btnY, dX + btnW, btnY + btnH, dH ? 0xFF882222 : 0xFF441111);
        this.fill(kX, btnY, kX + btnW, btnY + btnH, kH ? 0xFF228822 : 0xFF114411);
        this.drawCenteredString(this.textRenderer, "Delete", dX + btnW / 2, btnY + 3, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer, "Cancel", kX + btnW / 2, btnY + 3, 0xFFFFFF);
    }

    private void refreshButtons() {
        List<ServerPresetEntry> list = presetList;
        boolean hasSelection = selectedIndex >= 0 && list != null && selectedIndex < list.size();
        boolean isInstalled  = hasSelection && installedKeys.contains(list.get(selectedIndex).key);

        boolean canInstall   = state == STATE_READY && hasSelection && !isInstalled;
        boolean canUninstall = state == STATE_READY && hasSelection && isInstalled;

        for (Object o : this.buttons) {
            ButtonWidget b = (ButtonWidget) o;
            if (b.id == ID_INSTALL)   b.active = canInstall;
            if (b.id == ID_UNINSTALL) b.active = canUninstall;
            if (b.id == ID_DONE)      b.active = (state != STATE_INSTALLING);
        }
    }

    // =========================================================================
    // Input
    // =========================================================================

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == ID_DONE) {
            this.client.setScreen(parent);

        } else if (button.id == ID_INSTALL) {
            List<ServerPresetEntry> list = presetList;
            if (list == null || selectedIndex < 0 || selectedIndex >= list.size()) return;
            final ServerPresetEntry entry = list.get(selectedIndex);
            if (installedKeys.contains(entry.key)) return;

            state = STATE_INSTALLING;
            statusMessage = "";

            PresetFetcher.downloadAndSave(entry, new PresetFetcher.Callback<Void>() {
                public void onResult(Void value, String error) {
                    if (error != null) {
                        statusMessage = "Error: " + error;
                        lastInstallOk = false;
                    } else {
                        statusMessage = entry.name + " installed!";
                        lastInstallOk = true;
                        rebuildInstalledKeys();
                    }
                    state = STATE_READY;
                }
            });

        } else if (button.id == ID_UNINSTALL) {
            List<ServerPresetEntry> list = presetList;
            if (list == null || selectedIndex < 0 || selectedIndex >= list.size()) return;
            if (!installedKeys.contains(list.get(selectedIndex).key)) return;
            confirmUninstall = true;
        }
    }

    private void doUninstall() {
        List<ServerPresetEntry> list = presetList;
        if (list == null || selectedIndex < 0 || selectedIndex >= list.size()) return;
        ServerPresetEntry entry = list.get(selectedIndex);
        CommandSyntaxLoader.deleteSyntax(new File("config/modernchat/commands/" + entry.key + ".json"));
        rebuildInstalledKeys();
        statusMessage = entry.name + " uninstalled.";
        lastInstallOk = false;
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) {
        if (confirmUninstall) {
            int bW = 260, bH = 80, bX = (this.width - bW) / 2, bY = (this.height - bH) / 2;
            int btnY = bY + bH - 20, dX = bX + 16, kX = bX + bW - 66, bw = 50, bh = 14;
            if (mx >= dX && mx < dX + bw && my >= btnY && my < btnY + bh) {
                doUninstall();
                confirmUninstall = false;
            } else if (mx >= kX && mx < kX + bw && my >= btnY && my < btnY + bh) {
                confirmUninstall = false;
            } else if (!(mx >= bX && mx <= bX + bW && my >= bY && my <= bY + bH)) {
                confirmUninstall = false;
            }
            return;
        }

        List<ServerPresetEntry> list = presetList;
        if (state == STATE_READY && list != null) {
            int listRight = this.width - SCROLLBAR_W - 4;
            if (mx >= 4 && mx <= listRight && my >= LIST_TOP && my < listBottom) {
                int clicked = scrollOffset + (my - LIST_TOP) / ROW_HEIGHT;
                if (clicked >= 0 && clicked < list.size()) {
                    selectedIndex = clicked;
                }
            }
        }
        super.mouseClicked(mx, my, btn);
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == 1 && confirmUninstall) { confirmUninstall = false; return; }
        super.keyPressed(chr, key);
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        List<ServerPresetEntry> list = presetList;
        if (list == null || list.size() <= maxVisible) return;
        int wheel = Mouse.getEventDWheel();
        if (wheel > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        else if (wheel < 0) scrollOffset = Math.min(list.size() - maxVisible, scrollOffset + 1);
    }
}
