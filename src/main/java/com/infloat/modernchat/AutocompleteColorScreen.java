package com.infloat.modernchat;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * Config screen that lets the user customize the autocomplete suggestion color
 * for every server syntax file (singleplayer is excluded because it is used as a
 * read-only baseline).
 *
 * The list is fully scrollable so any number of server files is supported.
 * Click a row to select it, then type a hex color in the text field and press
 * Save (or Enter).  Changes are written directly to the syntax JSON file.</p>
 *
 */

public class AutocompleteColorScreen extends Screen {

    private final Screen parent;

    private static final class Entry {
        final CommandSyntaxDef def;

        Entry(CommandSyntaxDef def) {
            this.def = def;
        }

        String displayName() {
            if (def.name == null || def.name.isEmpty()) return "Unknown";
            return Character.toUpperCase(def.name.charAt(0)) + def.name.substring(1);
        }

        int colorInt() {
            return def.getColorInt();
        }

        String colorString() {
            return def.color != null ? def.color : "0xFFAAAAAA";
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private int selectedIndex = -1;
    private int scrollOffset  = 0;

    private static final int LIST_TOP    = 30;
    private static final int ROW_HEIGHT  = 22;
    private static final int SWATCH_SIZE = 12;
    private static final int SCROLLBAR_W = 5;

    // Computed in init() from actual screen height
    private int listBottom;
    private int maxVisible;
    private int editControlY;

    private TextFieldWidget colorField;
    private static final int ID_SAVE = 1;
    private static final int ID_DONE = 2;

    public AutocompleteColorScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        entries.clear();
        selectedIndex = -1;
        scrollOffset  = 0;

        List<CommandSyntaxDef> defs = CommandSyntaxLoader.loadAllDefs();
        for (CommandSyntaxDef def : defs) {
            entries.add(new Entry(def));
        }

        listBottom     = this.height - 58;
        maxVisible     = Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);
        editControlY   = listBottom + 6;

        colorField = new TextFieldWidget(
                0, this.textRenderer,
                this.width / 2 - 92, editControlY,
                130, 20);
        colorField.setMaxLength(12);

        this.buttons.add(new ButtonWidget(
                ID_SAVE,
                this.width / 2 + 43, editControlY,
                50, 20,
                "Save"));

        this.buttons.add(new ButtonWidget(
                ID_DONE,
                this.width / 2 - 75, this.height - 26,
                150, 20,
                "Done"));

        updateFieldFromSelection();
    }

    @Override
    public void tick() {
        colorField.tick();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.renderBackground();

        this.drawCenteredString(this.textRenderer,
                "Autocomplete Colors", this.width / 2, 10, 0xFFFFFF);

        renderList(mouseX, mouseY);
        renderEditPanel();

        colorField.render();
        super.render(mouseX, mouseY, delta);
    }

    private void renderList(int mouseX, int mouseY) {
        if (entries.isEmpty()) {
            this.drawCenteredString(this.textRenderer,
                    "No server syntax files found.",
                    this.width / 2, LIST_TOP + 10, 0x888888);
            return;
        }

        int visibleCount = Math.min(maxVisible, entries.size() - scrollOffset);
        int listRight    = this.width - SCROLLBAR_W - 4;

        for (int i = 0; i < visibleCount; i++) {
            int entryIndex = scrollOffset + i;
            Entry entry    = entries.get(entryIndex);
            int rowY       = LIST_TOP + i * ROW_HEIGHT;

            boolean isSelected = (entryIndex == selectedIndex);
            boolean isHovered  = mouseX >= 4 && mouseX <= listRight
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 1;

            if (isSelected) {
                this.fill(4, rowY, listRight, rowY + ROW_HEIGHT - 1, 0x882244AA);
            } else if (isHovered) {
                this.fill(4, rowY, listRight, rowY + ROW_HEIGHT - 1, 0x33FFFFFF);
            }

            int swatchX = 10;
            int swatchY = rowY + (ROW_HEIGHT - 1 - SWATCH_SIZE) / 2;
            this.fill(swatchX - 1, swatchY - 1,
                      swatchX + SWATCH_SIZE + 1, swatchY + SWATCH_SIZE + 1,
                      0xFFFFFFFF);
            this.fill(swatchX, swatchY,
                      swatchX + SWATCH_SIZE, swatchY + SWATCH_SIZE,
                      entry.colorInt());

            int textY     = rowY + 5;
            int cursorX   = swatchX + SWATCH_SIZE + 5;

            String name   = this.textRenderer.trimToWidth(entry.displayName(), 90);
            this.drawWithShadow(this.textRenderer, name, cursorX, textY, 0xFFFFFF);
            cursorX += this.textRenderer.getStringWidth(name) + 8;

            String hex = entry.colorString();
            this.drawWithShadow(this.textRenderer, hex, cursorX, textY, 0xAAAAAA);

            if (entry.def.ip != null && !entry.def.ip.isEmpty()) {
                String ipText  = entry.def.ip;
                int    ipWidth = this.textRenderer.getStringWidth(ipText);
                int    ipX     = listRight - ipWidth - 4;
                if (ipX > cursorX + this.textRenderer.getStringWidth(hex) + 4) {
                    this.drawWithShadow(this.textRenderer, ipText, ipX, textY, 0x777777);
                }
            }
        }

        if (entries.size() > maxVisible) {
            int sbX      = this.width - SCROLLBAR_W - 1;
            int sbHeight = listBottom - LIST_TOP;
            int thumbH   = Math.max(10, sbHeight * maxVisible / entries.size());
            int maxOff   = entries.size() - maxVisible;
            int thumbY   = LIST_TOP + (sbHeight - thumbH) * scrollOffset / Math.max(1, maxOff);
            this.fill(sbX, LIST_TOP, sbX + SCROLLBAR_W, listBottom, 0x33FFFFFF);
            this.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    private void renderEditPanel() {
        this.fill(4, listBottom, this.width - 4, listBottom + 1, 0x55FFFFFF);

        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            String name  = entries.get(selectedIndex).displayName();
            String label = "Color for: " + name;
            int labelX = this.width / 2 - 92 - this.textRenderer.getStringWidth(label) - 6;
            if (labelX < 4) labelX = 4;
            this.drawWithShadow(this.textRenderer, label, labelX, editControlY + 5, 0xFFFFFF);
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == ID_DONE) {
            this.client.setScreen(parent);
        } else if (button.id == ID_SAVE) {
            saveSelectedColor();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        colorField.mouseClicked(mouseX, mouseY, button);

        int listRight = this.width - SCROLLBAR_W - 4;
        if (mouseX >= 4 && mouseX <= listRight
                && mouseY >= LIST_TOP && mouseY < listBottom) {
            int clickedRow = (mouseY - LIST_TOP) / ROW_HEIGHT;
            int entryIndex = scrollOffset + clickedRow;
            if (entryIndex >= 0 && entryIndex < entries.size()) {
                selectedIndex = entryIndex;
                updateFieldFromSelection();
            }
        }

        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        if (entries.size() <= maxVisible) return;
        int wheel = Mouse.getEventDWheel();
        if (wheel > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (wheel < 0) {
            scrollOffset = Math.min(entries.size() - maxVisible, scrollOffset + 1);
        }
    }

    @Override
    protected void keyPressed(char chr, int keyCode) {
        if (colorField.isFocused()) {
            if (keyCode == 28) { // Enter – save and unfocus
                saveSelectedColor();
                colorField.setFocused(false);
            } else if (keyCode == 1) { // Escape – unfocus only
                colorField.setFocused(false);
            } else {
                colorField.keyPressed(chr, keyCode);
            }
            return;
        }
        super.keyPressed(chr, keyCode);
    }

    // Helpers

    private void updateFieldFromSelection() {
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            colorField.setText(entries.get(selectedIndex).colorString());
            colorField.setFocused(true);
        } else {
            colorField.setText("");
            colorField.setFocused(false);
        }
    }

    private void saveSelectedColor() {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) return;
        Entry  entry      = entries.get(selectedIndex);
        String input      = colorField.getText().trim();
        String normalized = normalizeColor(input);
        if (normalized == null) return; // invalid – don't write

        if (CommandSyntaxLoader.saveColor(entry.def.sourceFile, normalized)) {
            entry.def.color = normalized;
            // Reload autocomplete data so the new color is visible immediately.
            // AutocompleteMixin will pick up the change on the next server switch.
        }
    }

    private static String normalizeColor(String input) {
        if (input == null || input.isEmpty()) return null;
        String s = input.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        else if (s.startsWith("#"))                    s = s.substring(1);
        if (s.length() == 6) s = "FF" + s;
        if (s.length() != 8) return null;
        try {
            Long.parseLong(s, 16); // validate hex digits
            return "0x" + s.toUpperCase();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
