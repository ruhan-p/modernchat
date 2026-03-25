package com.infloat.modernchat;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Mouse;

import java.util.*;

/**
 * Three-column layout: Servers | Commands | Variants (widest), then a
 * full-width controls strip (~25 % of screen height) below.
 *
 * Syntax notation: <arg> = required, [arg] = optional.
 *
 * Workflow:
 *   1. Click a server (left column).
 *   2. Click a command (middle column) — its variants load in the right column.
 *   3. Click a variant row to select it; its text fills the input field below.
 *      Click [-] to remove it immediately.
 *   4. Type in the field and click Add (new variant) or Update (selected one).
 *   5. Click Save Changes to write to disk.  Delete Command removes the entry.
 *   6. New Command opens a blank form; type the command name and add variants.
 */
public class AutocompleteSyntaxScreen extends Screen {

    private final Screen parent;

    // ---- Data ---------------------------------------------------------------
    private List<CommandSyntaxDef> entries     = new ArrayList<CommandSyntaxDef>();
    private int                    selectedSrv = -1;
    private List<String>           cmdNames    = new ArrayList<String>();

    // ---- Scroll -------------------------------------------------------------
    private int srvScroll = 0, cmdScroll = 0, varScroll = 0;
    private int lastMX = 0, lastMY = 0;

    // ---- Layout constants ---------------------------------------------------
    private static final int LIST_TOP    = 30;
    private static final int HEADER_H    = 14;
    private static final int SRV_ROW_H   = 20;
    private static final int CMD_ROW_H   = 18;
    private static final int VAR_ROW_H   = 16;
    private static final int SCROLLBAR_W = 4;
    private static final int DONE_AREA_H = 28;
    /** Pixels reserved for the inline label left of the variant input field. */
    private static final int FIELD_LBL_W = 72;

    // ---- Computed column bounds (set in init) --------------------------------
    private int c1L, c1R, c1CR, c1Max;   // col1 left/right/contentRight/maxVisible
    private int c2L, c2R, c2CR, c2Max;
    private int c3L, c3R, c3CR, c3Max;
    private int listBottom;

    // ---- Controls area ------------------------------------------------------
    private int ctrlH;       // height of the controls strip
    private int ctrlY;       // top of controls strip
    private int ctrlCmdY;    // y of "Command:" row
    private int ctrlFldY;    // y of variant input row
    private int ctrlBtnY;    // y of action buttons row

    // ---- Edit state ---------------------------------------------------------
    private String       editCmd       = null;
    private boolean      addingNew     = false;
    private final List<String> editVars = new ArrayList<String>();
    private int          selVar        = -1;
    private boolean      confirmDel    = false;
    private boolean      confirmDelSrv = false;

    // ---- Widgets ------------------------------------------------------------
    private TextFieldWidget newCmdFld;
    private TextFieldWidget varFld;

    // ---- Button IDs ---------------------------------------------------------
    private static final int ID_DONE       = 1;
    private static final int ID_SAVE       = 2;
    private static final int ID_DEL        = 3;
    private static final int ID_NEW        = 4;
    private static final int ID_ADD        = 5;
    private static final int ID_DEL_SRV    = 6;
    private static final int ID_ADD_PRESET = 7;

    // =========================================================================
    // init
    // =========================================================================

    public AutocompleteSyntaxScreen(Screen parent) { this.parent = parent; }

    @Override
    public void init() {
        entries.clear();
        selectedSrv = -1; srvScroll = 0; cmdScroll = 0; varScroll = 0;
        cmdNames.clear();
        editCmd = null; addingNew = false; editVars.clear();
        selVar = -1; confirmDel = false; confirmDelSrv = false;

        entries = CommandSyntaxLoader.loadAllDefs();

        // ---- Column widths --------------------------------------------------
        int inner = this.width - 8;
        int col1W = Math.max(50,  (int)(inner * 0.18f));
        int col2W = Math.max(68,  (int)(inner * 0.22f));

        c1L = 4;               c1R = c1L + col1W;     c1CR = c1R - SCROLLBAR_W - 1;
        c2L = c1R + 4;         c2R = c2L + col2W;     c2CR = c2R - SCROLLBAR_W - 1;
        c3L = c2R + 4;         c3R = this.width - 4;  c3CR = c3R - SCROLLBAR_W - 1;

        // ---- Vertical layout ------------------------------------------------
        ctrlH      = Math.max(68, this.height / 4);
        listBottom = this.height - ctrlH - DONE_AREA_H;

        int listTop = LIST_TOP + HEADER_H;
        c1Max = Math.max(1, (listBottom - listTop) / SRV_ROW_H);
        c2Max = Math.max(1, (listBottom - listTop) / CMD_ROW_H);
        c3Max = Math.max(1, (listBottom - listTop) / VAR_ROW_H);

        // ---- Controls row positions -----------------------------------------
        ctrlY    = listBottom + 2;
        ctrlCmdY = ctrlY + Math.max(6, ctrlH / 11);
        ctrlFldY = ctrlCmdY + 18;
        ctrlBtnY = ctrlFldY  + 22;

        // ---- Widgets --------------------------------------------------------
        int addBtnW   = 40;
        int addBtnX   = this.width - 8 - addBtnW;
        int fldLeft   = 8 + FIELD_LBL_W + 2;
        int fldRight  = addBtnX - 4;

        varFld = new TextFieldWidget(0, this.textRenderer,
                fldLeft, ctrlFldY, fldRight - fldLeft, 16);
        varFld.setMaxLength(256);

        newCmdFld = new TextFieldWidget(0, this.textRenderer,
                8 + 68, ctrlCmdY, this.width - 16 - 68, 14);
        newCmdFld.setMaxLength(64);

        // ---- Buttons --------------------------------------------------------
        this.buttons.add(new ButtonWidget(ID_ADD,
                addBtnX, ctrlFldY - 2, addBtnW, 20, "Add"));

        // "+" preset button — right-aligned in the Servers column header
        this.buttons.add(new ButtonWidget(ID_ADD_PRESET,
                c1CR - 16, LIST_TOP, 16, 14, "+"));

        // Bottom row: 4 equal buttons
        int bTotal = this.width - 16;
        int bW     = (bTotal - 12) / 4;
        this.buttons.add(new ButtonWidget(ID_SAVE,    8,                 ctrlBtnY, bW, 20, "Save Changes"));
        this.buttons.add(new ButtonWidget(ID_DEL,     8 + (bW + 4),      ctrlBtnY, bW, 20, "Delete Command"));
        this.buttons.add(new ButtonWidget(ID_NEW,     8 + 2 * (bW + 4),  ctrlBtnY, bW, 20, "New Command"));
        this.buttons.add(new ButtonWidget(ID_DEL_SRV, 8 + 3 * (bW + 4),  ctrlBtnY, bW, 20, "Delete Syntax"));

        this.buttons.add(new ButtonWidget(ID_DONE,
                this.width / 2 - 75, this.height - 24, 150, 20, "Done"));

        refreshButtons();
    }

    // =========================================================================
    // State helpers
    // =========================================================================

    private CommandSyntaxDef selectedDef() {
        return (selectedSrv >= 0 && selectedSrv < entries.size()) ? entries.get(selectedSrv) : null;
    }

    private void pickServer(int idx) {
        selectedSrv = idx;
        cmdScroll   = 0;
        reloadCmdNames();
        pickCmd(null);
    }

    private void pickCmd(String key) {
        editCmd   = key;
        addingNew = false;
        selVar    = -1;
        varScroll = 0;
        editVars.clear();
        varFld.setText(""); varFld.setFocused(false);
        newCmdFld.setText(""); newCmdFld.setFocused(false);
        confirmDel = false;
        if (key != null) {
            CommandSyntaxDef def = selectedDef();
            if (def != null && def.commands != null) {
                List<String> v = def.commands.get(key);
                if (v != null) editVars.addAll(v);
            }
        }
        refreshButtons();
    }

    private void beginNew() {
        editCmd = null; addingNew = true;
        selVar = -1; varScroll = 0; editVars.clear();
        varFld.setText(""); varFld.setFocused(false);
        newCmdFld.setText("/"); newCmdFld.setFocused(true);
        confirmDel = false;
        refreshButtons();
    }

    private void reloadCmdNames() {
        cmdNames.clear(); cmdScroll = 0;
        CommandSyntaxDef def = selectedDef();
        if (def != null && def.commands != null) {
            cmdNames.addAll(def.commands.keySet());
            Collections.sort(cmdNames);
        }
    }

    private void commitVariant() {
        if (!addingNew && editCmd == null) return;
        String text = varFld.getText();
        if (selVar >= 0 && selVar < editVars.size()) {
            editVars.set(selVar, text);
            selVar = -1;
        } else {
            editVars.add(text);
        }
        varFld.setText(""); varFld.setFocused(false);
        varScroll = Math.min(varScroll, Math.max(0, editVars.size() - c3Max));
        refreshButtons();
    }

    private void save() {
        CommandSyntaxDef def = selectedDef();
        if (def == null) return;
        if (def.commands == null) def.commands = new LinkedHashMap<String, List<String>>();
        String key;
        if (addingNew) {
            key = newCmdFld.getText().trim();
            if (key.isEmpty()) return;
            if (!key.startsWith("/")) key = "/" + key;
        } else {
            key = editCmd;
            if (key == null) return;
        }
        def.commands.put(key, new ArrayList<String>(editVars));
        CommandSyntaxLoader.saveCommands(def.sourceFile, def.commands);
        editCmd = key; addingNew = false;
        newCmdFld.setText(""); newCmdFld.setFocused(false);
        reloadCmdNames(); refreshButtons();
    }

    private void deleteCmd() {
        CommandSyntaxDef def = selectedDef();
        if (def == null || editCmd == null || def.commands == null) return;
        def.commands.remove(editCmd);
        CommandSyntaxLoader.saveCommands(def.sourceFile, def.commands);
        editCmd = null; addingNew = false; editVars.clear();
        selVar = -1; confirmDel = false;
        varFld.setText(""); newCmdFld.setText("");
        reloadCmdNames(); refreshButtons();
    }

    private void deleteServer() {
        CommandSyntaxDef def = selectedDef();
        if (def == null) return;
        CommandSyntaxLoader.deleteSyntax(def.sourceFile);
        entries.remove(selectedSrv);
        selectedSrv = -1; confirmDelSrv = false;
        cmdNames.clear(); editCmd = null; addingNew = false; editVars.clear();
        selVar = -1; srvScroll = 0; cmdScroll = 0; varScroll = 0;
        varFld.setText(""); newCmdFld.setText("");
        refreshButtons();
    }

    private void refreshButtons() {
        boolean hasEdit = (editCmd != null || addingNew);
        boolean hasSrv  = (selectedSrv >= 0 && selectedSrv < entries.size());
        for (Object o : this.buttons) {
            ButtonWidget b = (ButtonWidget) o;
            if (b.id == ID_SAVE)       b.active = hasEdit;
            if (b.id == ID_DEL)        b.active = (editCmd != null);
            if (b.id == ID_NEW)        b.active = hasSrv;
            if (b.id == ID_ADD)        b.active = hasEdit;
            if (b.id == ID_DEL_SRV)    b.active = hasSrv;
            if (b.id == ID_ADD_PRESET) b.active = true;
        }
        for (Object o : this.buttons) {
            ButtonWidget b = (ButtonWidget) o;
            if (b.id == ID_ADD) b.message = (selVar >= 0) ? "Update" : "Add";
        }
        varFld.setEditable(hasEdit);
        newCmdFld.setEditable(addingNew);
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void tick() {
        varFld.tick();
        if (addingNew) newCmdFld.tick();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        lastMX = mouseX; lastMY = mouseY;
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, "Autocomplete Syntaxes", this.width / 2, 10, 0xFFFFFF);

        renderServers(mouseX, mouseY);
        renderCommands(mouseX, mouseY);
        renderVariants(mouseX, mouseY);

        // Column dividers
        this.fill(c1R + 1, LIST_TOP - 2, c1R + 3, listBottom, 0x55FFFFFF);
        this.fill(c2R + 1, LIST_TOP - 2, c2R + 3, listBottom, 0x55FFFFFF);
        // Horizontal divider above controls
        this.fill(4, listBottom, this.width - 4, listBottom + 1, 0x55FFFFFF);

        renderControls(mouseX, mouseY);

        varFld.render();
        if (addingNew) newCmdFld.render();

        super.render(mouseX, mouseY, delta);
        if (confirmDel)    renderConfirmDel(mouseX, mouseY);
        if (confirmDelSrv) renderConfirmDelSrv(mouseX, mouseY);
    }

    // ---- Column 1: Servers --------------------------------------------------

    private void renderServers(int mx, int my) {
        this.drawWithShadow(this.textRenderer, "Servers", c1L, LIST_TOP, 0xAAAAAA);
        if (entries.isEmpty()) {
            this.drawWithShadow(this.textRenderer, "None", c1L, LIST_TOP + HEADER_H + 2, 0x555555);
            return;
        }
        int top = LIST_TOP + HEADER_H;
        int vis = Math.min(c1Max, entries.size() - srvScroll);
        for (int i = 0; i < vis; i++) {
            int idx  = srvScroll + i;
            int rowY = top + i * SRV_ROW_H;
            boolean sel = (idx == selectedSrv);
            boolean hov = mx >= c1L && mx <= c1CR && my >= rowY && my < rowY + SRV_ROW_H;
            if (sel) this.fill(c1L, rowY, c1CR, rowY + SRV_ROW_H, 0x882244AA);
            else if (hov) this.fill(c1L, rowY, c1CR, rowY + SRV_ROW_H, 0x33FFFFFF);
            String name = this.textRenderer.trimToWidth(cap(entries.get(idx).name), c1CR - c1L - 6);
            this.drawWithShadow(this.textRenderer, name, c1L + 4, rowY + 5, sel ? 0xFFFF55 : 0xFFFFFF);
        }
        if (entries.size() > c1Max)
            scrollbar(c1R - SCROLLBAR_W, top, listBottom, entries.size(), c1Max, srvScroll);
    }

    // ---- Column 2: Commands -------------------------------------------------

    private void renderCommands(int mx, int my) {
        String cmdHdr;
        if (selectedSrv >= 0 && selectedSrv < entries.size()) {
            cmdHdr = this.textRenderer.trimToWidth(
                    "Commands \u2014 " + cap(entries.get(selectedSrv).name), c2CR - c2L);
        } else {
            cmdHdr = "Commands";
        }
        this.drawWithShadow(this.textRenderer, cmdHdr, c2L, LIST_TOP, 0xAAAAAA);

        if (selectedSrv < 0 || selectedSrv >= entries.size()) {
            this.drawWithShadow(this.textRenderer, "\u2190 Select", c2L, LIST_TOP + HEADER_H + 4, 0x555555);
            return;
        }
        if (cmdNames.isEmpty()) {
            this.drawWithShadow(this.textRenderer, "(none)", c2L, LIST_TOP + HEADER_H + 4, 0x555555);
            return;
        }

        int top = LIST_TOP + HEADER_H;
        int vis = Math.min(c2Max, cmdNames.size() - cmdScroll);
        CommandSyntaxDef def = selectedDef();

        for (int i = 0; i < vis; i++) {
            int    idx  = cmdScroll + i;
            String cmd  = cmdNames.get(idx);
            int    rowY = top + i * CMD_ROW_H;
            boolean sel = cmd.equals(editCmd);
            boolean hov = mx >= c2L && mx <= c2CR && my >= rowY && my < rowY + CMD_ROW_H;
            if (sel) this.fill(c2L, rowY, c2CR, rowY + CMD_ROW_H, 0x882244AA);
            else if (hov) this.fill(c2L, rowY, c2CR, rowY + CMD_ROW_H, 0x33FFFFFF);

            // Variant count — right-aligned number
            List<String> vl = (def != null && def.commands != null) ? def.commands.get(cmd) : null;
            String vc  = String.valueOf(vl != null ? vl.size() : 0);
            int    vcW = this.textRenderer.getStringWidth(vc);
            int    vcX = c2CR - vcW - 1;
            this.drawWithShadow(this.textRenderer, vc, vcX, rowY + 4, 0x777777);

            String label = this.textRenderer.trimToWidth(cmd, vcX - c2L - 6);
            this.drawWithShadow(this.textRenderer, label, c2L + 3, rowY + 4, sel ? 0xFFFF55 : 0xFFFFFF);
        }
        if (cmdNames.size() > c2Max)
            scrollbar(c2R - SCROLLBAR_W, top, listBottom, cmdNames.size(), c2Max, cmdScroll);
    }

    // ---- Column 3: Variants -------------------------------------------------

    private void renderVariants(int mx, int my) {
        boolean hasCmd = (editCmd != null || addingNew);

        // Header
        String hdr;
        if (addingNew)       hdr = "Variants \u2014 (new)";
        else if (editCmd != null) hdr = "Variants \u2014 " + editCmd;
        else                 hdr = "Variants";
        hdr = this.textRenderer.trimToWidth(hdr, c3CR - c3L);
        this.drawWithShadow(this.textRenderer, hdr, c3L, LIST_TOP, hasCmd ? 0xAAAAAA : 0x666666);

        int top = LIST_TOP + HEADER_H;
        if (!hasCmd) {
            this.drawWithShadow(this.textRenderer, "Select a command \u2190", c3L, top + 4, 0x555555);
            return;
        }
        if (editVars.isEmpty()) {
            this.drawWithShadow(this.textRenderer,
                    "No variants yet \u2014 type one below and click Add", c3L, top + 4, 0x555555);
        }

        int vis = Math.min(c3Max, editVars.size() - varScroll);
        for (int i = 0; i < vis; i++) {
            int    vi   = varScroll + i;
            String v    = editVars.get(vi);
            int    rowY = top + i * VAR_ROW_H;
            boolean sel = (vi == selVar);
            boolean hov = mx >= c3L && mx <= c3CR && my >= rowY && my < rowY + VAR_ROW_H && !sel;
            if (sel) this.fill(c3L, rowY, c3CR - 22, rowY + VAR_ROW_H, 0x882244AA);
            else if (hov) this.fill(c3L, rowY, c3CR - 22, rowY + VAR_ROW_H, 0x22FFFFFF);

            // Index
            this.drawWithShadow(this.textRenderer, (vi + 1) + ".", c3L + 2, rowY + 3, 0x666666);

            // Text
            String removeStr = "[-]";
            int removeX = c3CR - this.textRenderer.getStringWidth(removeStr) - 2;
            int maxW    = removeX - (c3L + 16) - 4;
            String disp = v.isEmpty()
                    ? "\u00a77(no arguments)"
                    : this.textRenderer.trimToWidth(v, maxW);
            this.drawWithShadow(this.textRenderer, disp, c3L + 16, rowY + 3,
                    sel ? 0xFFFF55 : (v.isEmpty() ? 0x555555 : 0xDDDDDD));

            this.drawWithShadow(this.textRenderer, removeStr, removeX, rowY + 3, 0xFF4444);
        }
        if (editVars.size() > c3Max)
            scrollbar(c3R - SCROLLBAR_W, top, listBottom, editVars.size(), c3Max, varScroll);
    }

    // ---- Controls strip -----------------------------------------------------

    private void renderControls(int mx, int my) {
        this.fill(4, ctrlY, this.width - 4, ctrlY + ctrlH, 0x22FFFFFF);

        boolean hasEdit = (editCmd != null || addingNew);
        if (!hasEdit) {
            String hint = (selectedSrv >= 0)
                    ? "Click a command to edit, or click New Command"
                    : "Select a server, then click a command to edit";
            this.drawCenteredString(this.textRenderer, hint, this.width / 2, ctrlY + ctrlH / 2 - 4, 0x666666);
            return;
        }

        // Command row
        this.drawWithShadow(this.textRenderer, "Command:", 8, ctrlCmdY + 2, 0xAAAAAA);
        if (!addingNew) {
            String helpTxt = "Syntax: \u00a7f<\u00a77required\u00a7f>  \u00a77[\u00a7foptional\u00a77]";
            int helpW = this.textRenderer.getStringWidth(helpTxt);
            int maxCmdW = (this.width - 8 - helpW - 8) - (8 + 68) - 4;
            String disp = this.textRenderer.trimToWidth(editCmd != null ? editCmd : "", maxCmdW);
            this.drawWithShadow(this.textRenderer, disp, 8 + 68, ctrlCmdY + 2, 0xFFFF55);
            this.drawWithShadow(this.textRenderer, helpTxt, this.width - 8 - helpW, ctrlCmdY + 2, 0xAAAAAA);
        }

        // Variant field row label (the field widget itself is rendered by the caller)
        String lbl = (selVar >= 0) ? "Variant " + (selVar + 1) + ":" : "New variant:";
        this.drawWithShadow(this.textRenderer, lbl, 8, ctrlFldY + 2, 0x888888);
    }

    // ---- Confirm-delete overlay ---------------------------------------------

    private void renderConfirmDel(int mx, int my) {
        int bW = 210, bH = 60, bX = (this.width - bW) / 2, bY = (this.height - bH) / 2;
        this.fill(bX - 1, bY - 1, bX + bW + 1, bY + bH + 1, 0xFF555555);
        this.fill(bX, bY, bX + bW, bY + bH, 0xFF1A1A1A);
        this.drawCenteredString(this.textRenderer, "Delete command", this.width / 2, bY + 8, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer, (editCmd != null ? editCmd : "?") + "?",
                this.width / 2, bY + 20, 0xFF5555);
        int btnY = bY + bH - 20, dX = bX + 16, kX = bX + bW - 66, btnW = 50, btnH = 14;
        boolean dH = mx >= dX && mx < dX+btnW && my >= btnY && my < btnY+btnH;
        boolean kH = mx >= kX && mx < kX+btnW && my >= btnY && my < btnY+btnH;
        this.fill(dX, btnY, dX+btnW, btnY+btnH, dH ? 0xFF882222 : 0xFF441111);
        this.fill(kX, btnY, kX+btnW, btnY+btnH, kH ? 0xFF228822 : 0xFF114411);
        this.drawCenteredString(this.textRenderer, "Delete", dX + btnW / 2, btnY + 3, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer, "Cancel", kX + btnW / 2, btnY + 3, 0xFFFFFF);
    }

    private void renderConfirmDelSrv(int mx, int my) {
        CommandSyntaxDef def = selectedDef();
        String srvName = (def != null && def.name != null) ? cap(def.name) : "?";
        int bW = 260, bH = 80, bX = (this.width - bW) / 2, bY = (this.height - bH) / 2;
        this.fill(bX - 1, bY - 1, bX + bW + 1, bY + bH + 1, 0xFF555555);
        this.fill(bX, bY, bX + bW, bY + bH, 0xFF1A1A1A);
        this.drawCenteredString(this.textRenderer, "Delete syntax: " + srvName + "?", this.width / 2, bY + 8, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer, "This will also delete the friend list.", this.width / 2, bY + 22, 0xFFAA00);
        this.drawCenteredString(this.textRenderer, "This cannot be undone.", this.width / 2, bY + 34, 0xFF5555);
        int btnY = bY + bH - 20, dX = bX + 16, kX = bX + bW - 66, btnW = 50, btnH = 14;
        boolean dH = mx >= dX && mx < dX+btnW && my >= btnY && my < btnY+btnH;
        boolean kH = mx >= kX && mx < kX+btnW && my >= btnY && my < btnY+btnH;
        this.fill(dX, btnY, dX+btnW, btnY+btnH, dH ? 0xFF882222 : 0xFF441111);
        this.fill(kX, btnY, kX+btnW, btnY+btnH, kH ? 0xFF228822 : 0xFF114411);
        this.drawCenteredString(this.textRenderer, "Delete", dX + btnW / 2, btnY + 3, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer, "Cancel", kX + btnW / 2, btnY + 3, 0xFFFFFF);
    }

    // ---- Scrollbar helper ---------------------------------------------------

    private void scrollbar(int sbX, int sbTop, int sbBot, int total, int vis, int off) {
        int h = sbBot - sbTop;
        int th = Math.max(8, h * vis / total);
        int maxOff = total - vis;
        int ty = sbTop + (h - th) * off / Math.max(1, maxOff);
        this.fill(sbX, sbTop, sbX + SCROLLBAR_W, sbBot, 0x33FFFFFF);
        this.fill(sbX, ty,    sbX + SCROLLBAR_W, ty + th, 0xFFAAAAAA);
    }

    // =========================================================================
    // Input
    // =========================================================================

    @Override
    protected void buttonClicked(ButtonWidget b) {
        switch (b.id) {
            case ID_DONE:       this.client.setScreen(parent); break;
            case ID_SAVE:       save(); break;
            case ID_DEL:        if (editCmd != null) confirmDel = true; break;
            case ID_NEW:        beginNew(); break;
            case ID_ADD:        commitVariant(); break;
            case ID_DEL_SRV:    if (selectedSrv >= 0 && selectedSrv < entries.size()) confirmDelSrv = true; break;
            case ID_ADD_PRESET: this.client.setScreen(new AddServerPresetScreen(this)); break;
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) {
        if (confirmDelSrv) {
            int bW = 260, bH = 80, bX = (this.width - bW) / 2, bY = (this.height - bH) / 2;
            int btnY = bY + bH - 20, dX = bX + 16, kX = bX + bW - 66, bw = 50, bh = 14;
            if (mx >= dX && mx < dX+bw && my >= btnY && my < btnY+bh) deleteServer();
            else if (mx >= kX && mx < kX+bw && my >= btnY && my < btnY+bh) confirmDelSrv = false;
            else if (!(mx >= bX && mx <= bX+bW && my >= bY && my <= bY+bH)) confirmDelSrv = false;
            return;
        }
        if (confirmDel) {
            int bW = 210, bH = 60, bX = (this.width - bW) / 2, bY = (this.height - bH) / 2;
            int btnY = bY + bH - 20, dX = bX + 16, kX = bX + bW - 66, bw = 50, bh = 14;
            if (mx >= dX && mx < dX+bw && my >= btnY && my < btnY+bh) deleteCmd();
            else if (mx >= kX && mx < kX+bw && my >= btnY && my < btnY+bh) confirmDel = false;
            else if (!(mx >= bX && mx <= bX+bW && my >= bY && my <= bY+bH)) confirmDel = false;
            return;
        }

        varFld.mouseClicked(mx, my, btn);
        if (addingNew) newCmdFld.mouseClicked(mx, my, btn);

        int listTop = LIST_TOP + HEADER_H;

        // Col1 – server selection
        if (mx >= c1L && mx <= c1CR && my >= listTop && my < listBottom) {
            int idx = srvScroll + (my - listTop) / SRV_ROW_H;
            if (idx >= 0 && idx < entries.size() && idx != selectedSrv) pickServer(idx);
        }

        // Col2 – command selection
        if (selectedSrv >= 0 && mx >= c2L && mx <= c2CR && my >= listTop && my < listBottom) {
            int idx = cmdScroll + (my - listTop) / CMD_ROW_H;
            if (idx >= 0 && idx < cmdNames.size()) pickCmd(cmdNames.get(idx));
        }

        // Col3 – variant interaction
        boolean hasEdit = (editCmd != null || addingNew);
        if (hasEdit && mx >= c3L && mx <= c3CR && my >= listTop && my < listBottom) {
            int vis = Math.min(c3Max, editVars.size() - varScroll);
            for (int i = 0; i < vis; i++) {
                int vi   = varScroll + i;
                int rowY = listTop + i * VAR_ROW_H;
                if (my >= rowY && my < rowY + VAR_ROW_H) {
                    String removeStr = "[-]";
                    int removeX = c3CR - this.textRenderer.getStringWidth(removeStr) - 2;
                    if (mx >= removeX - 2) {
                        editVars.remove(vi);
                        if (selVar == vi)       { selVar = -1; varFld.setText(""); }
                        else if (selVar > vi)   { selVar--; }
                        varScroll = Math.min(varScroll, Math.max(0, editVars.size() - c3Max));
                        refreshButtons();
                    } else {
                        if (selVar == vi) { selVar = -1; varFld.setText(""); varFld.setFocused(false); }
                        else { selVar = vi; varFld.setText(editVars.get(vi)); varFld.setFocused(true); }
                        refreshButtons();
                    }
                    super.mouseClicked(mx, my, btn);
                    return;
                }
            }
        }

        super.mouseClicked(mx, my, btn);
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || lastMY >= listBottom) return;
        int dir = (wheel > 0) ? -1 : 1;
        if (lastMX < c2L) {
            srvScroll = clamp(srvScroll + dir, 0, Math.max(0, entries.size()  - c1Max));
        } else if (lastMX < c3L) {
            cmdScroll = clamp(cmdScroll + dir, 0, Math.max(0, cmdNames.size() - c2Max));
        } else {
            varScroll = clamp(varScroll + dir, 0, Math.max(0, editVars.size() - c3Max));
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == 1) {
            if (confirmDelSrv)           { confirmDelSrv = false; return; }
            if (confirmDel)              { confirmDel = false; return; }
            if (varFld.isFocused())      { varFld.setFocused(false); selVar = -1; varFld.setText(""); refreshButtons(); return; }
            if (addingNew && newCmdFld.isFocused()) { newCmdFld.setFocused(false); return; }
        }
        if (varFld.isFocused()) {
            if (key == 28) commitVariant(); else varFld.keyPressed(chr, key);
            return;
        }
        if (addingNew && newCmdFld.isFocused()) {
            if (key == 28) { newCmdFld.setFocused(false); varFld.setFocused(true); }
            else newCmdFld.keyPressed(chr, key);
            return;
        }
        super.keyPressed(chr, key);
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return "Unknown";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
