package com.infloat.modernchat;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Intermediate screen shown when the user clicks "Customize..." in the main
 * config screen. Has three options: Colors, Friends, and Syntaxes.
 */
public class AutocompleteCustomizeScreen extends Screen {

    private final Screen parent;

    private static final int ID_COLORS   = 1;
    private static final int ID_FRIENDS  = 2;
    private static final int ID_DONE     = 3;
    private static final int ID_SYNTAXES = 4;

    public AutocompleteCustomizeScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.buttons.add(new ButtonWidget(ID_COLORS,
                centerX - 100, centerY - 38, 200, 20, "Colors"));

        this.buttons.add(new ButtonWidget(ID_FRIENDS,
                centerX - 100, centerY - 10, 200, 20, "Friends"));

        this.buttons.add(new ButtonWidget(ID_SYNTAXES,
                centerX - 100, centerY + 18, 200, 20, "Syntaxes"));

        this.buttons.add(new ButtonWidget(ID_DONE,
                centerX - 75, centerY + 54, 150, 20, "Done"));
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, "Customize Autocomplete", this.width / 2, 15, 0xFFFFFF);
        super.render(mouseX, mouseY, delta);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == ID_COLORS) {
            this.client.setScreen(new AutocompleteColorScreen(this));
        } else if (button.id == ID_FRIENDS) {
            this.client.setScreen(new AutocompleteFriendsScreen(this));
        } else if (button.id == ID_SYNTAXES) {
            this.client.setScreen(new AutocompleteSyntaxScreen(this));
        } else if (button.id == ID_DONE) {
            this.client.setScreen(parent);
        }
    }
}
