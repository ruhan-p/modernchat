package com.infloat.modernchat;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.ArrayList;
import java.util.List;

public class ModernChatConfigScreen extends Screen {

	private final Screen parent;
	private final ModernChatConfig config = ModernChatConfig.INSTANCE;

	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int ROW_HEIGHT = 24;
	// Autocomplete row is split: toggle + customize button side-by-side
	private static final int AUTOCOMPLETE_TOGGLE_W = 80;
	private static final int CUSTOMIZE_BTN_W       = 66;
	private static final int AUTOCOMPLETE_ROW_GAP  = 4;

	private TextFieldWidget historyField;
	private int historyFieldY;

	private final List<OptionEntry> options = new ArrayList<OptionEntry>();

	public ModernChatConfigScreen(Screen parent) {
		this.parent = parent;
	}

	@Override
	public void init() {
		options.clear();

		int centerX = this.width / 2;
		int startY = 40;

		int id = 0;

		// Autocomplete – toggle button (narrower) + "Customize..." button side-by-side
		options.add(new OptionEntry(id++, "Autocomplete", "Toggle Command Autocomplete",
				centerX + 5, startY, AUTOCOMPLETE_TOGGLE_W));
		int autocompleteY = startY; // saved for the Customize button below

		// Chat History
		startY += ROW_HEIGHT;
		historyFieldY = startY;
		historyField = new TextFieldWidget(id++, this.textRenderer, centerX + 5, startY, BUTTON_WIDTH, BUTTON_HEIGHT);
		historyField.setMaxLength(5);
		historyField.setText(String.valueOf(config.chatHistory));
		options.add(new OptionEntry("Chat History", "Change the number of messages saved in chat",
				centerX + 5, startY));

		// Smooth Chat Animations
		startY += ROW_HEIGHT;
		options.add(new OptionEntry(id++, "Smooth Chat Animations", "Toggle the chat animations",
				centerX + 5, startY, true));

		// Raise Chat
		startY += ROW_HEIGHT;
		options.add(new OptionEntry(id++, "Raise Chat", "Vary the chat height to avoid covering health/armor bars",
				centerX + 5, startY, true));

		// Compact Chat Spam
		startY += ROW_HEIGHT;
		options.add(new OptionEntry(id++, "Compact Chat Spam", "Replace chat spam with a counter",
				centerX + 5, startY, true));

		// Maintain Chat History
		startY += ROW_HEIGHT;
		options.add(new OptionEntry(id++, "Maintain Chat History", "Preserve chat history",
				centerX + 5, startY, true));

		// Replace Angle Brackets
		startY += ROW_HEIGHT;
		options.add(new OptionEntry(id++, "Replace Angle Brackets", "Replace angle brackets with a colon (:)",
				centerX + 5, startY, true));

		// Preserve Chat Input
		startY += ROW_HEIGHT;
		options.add(new OptionEntry(id++, "Preserve Chat Input", "Restore typed text when reopening the chat",
				centerX + 5, startY, true));

		// Add toggle buttons
		for (OptionEntry entry : options) {
			if (entry.button != null) {
				this.buttons.add(entry.button);
			}
		}

		// "Customize..." button – shares the Autocomplete row, placed to the right of the toggle
		this.buttons.add(new ButtonWidget(101,
				centerX + 5 + AUTOCOMPLETE_TOGGLE_W + AUTOCOMPLETE_ROW_GAP,
				autocompleteY,
				CUSTOMIZE_BTN_W, BUTTON_HEIGHT,
				"Customize..."));

		// Done button
		startY += ROW_HEIGHT + 10;
		this.buttons.add(new ButtonWidget(100, centerX - 100, startY, 200, BUTTON_HEIGHT, "Done"));
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (button.id == 100) {
			applyHistoryField();
			config.save();
			this.client.setScreen(parent);
			return;
		}

		if (button.id == 101) {
			applyHistoryField();
			config.save();
			this.client.setScreen(new AutocompleteCustomizeScreen(this));
			return;
		}

		for (OptionEntry entry : options) {
			if (entry.button != null && entry.button.id == button.id) {
				entry.toggle();
				return;
			}
		}
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		this.renderBackground();
		this.drawCenteredString(this.textRenderer, "Modern Chat Settings", this.width / 2, 15, 0xFFFFFF);

		int centerX = this.width / 2;

		for (OptionEntry entry : options) {
			this.drawWithShadow(this.textRenderer, entry.label, centerX - 155, entry.y + 6, 0xFFFFFF);
		}

		historyField.render();

		super.render(mouseX, mouseY, delta);

		// Draw tooltips after everything else
		for (OptionEntry entry : options) {
			int buttonX = entry.x;
			int buttonY = entry.y;
			if (mouseX >= buttonX && mouseX <= buttonX + BUTTON_WIDTH
					&& mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT) {
				this.renderTooltip(entry.description, mouseX, mouseY);
				break;
			}
		}
	}

	@Override
	public void tick() {
		historyField.tick();
	}

	@Override
	protected void keyPressed(char chr, int keyCode) {
		if (historyField.isFocused()) {
			if (chr >= '0' && chr <= '9' || keyCode == 14 || keyCode == 203 || keyCode == 205 || keyCode == 211) {
				historyField.keyPressed(chr, keyCode);
			}
			if (keyCode == 28) {
				applyHistoryField();
				historyField.setFocused(false);
			}
			if (keyCode == 1) {
				historyField.setFocused(false);
			}
			return;
		}
		super.keyPressed(chr, keyCode);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		historyField.mouseClicked(mouseX, mouseY, mouseButton);
		super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	private void applyHistoryField() {
		String text = historyField.getText().trim();
		if (!text.isEmpty()) {
			try {
				int value = Integer.parseInt(text);
				config.chatHistory = ModernChatConfig.clampHistory(value);
			} catch (NumberFormatException ignored) {
			}
		}
		historyField.setText(String.valueOf(config.chatHistory));
	}

	private boolean getConfigValue(String label) {
		if ("Autocomplete".equals(label)) return config.autocomplete;
		if ("Smooth Chat Animations".equals(label)) return config.smoothAnimations;
		if ("Raise Chat".equals(label)) return config.raiseChat;
		if ("Compact Chat Spam".equals(label)) return config.compactChatSpam;
		if ("Maintain Chat History".equals(label)) return config.maintainChatHistory;
		if ("Replace Angle Brackets".equals(label)) return config.replaceAngleBrackets;
		if ("Preserve Chat Input".equals(label)) return config.preserveChatInput;
		return false;
	}

	private void setConfigValue(String label, boolean value) {
		if ("Autocomplete".equals(label)) config.autocomplete = value;
		else if ("Smooth Chat Animations".equals(label)) config.smoothAnimations = value;
		else if ("Raise Chat".equals(label)) config.raiseChat = value;
		else if ("Compact Chat Spam".equals(label)) config.compactChatSpam = value;
		else if ("Maintain Chat History".equals(label)) config.maintainChatHistory = value;
		else if ("Replace Angle Brackets".equals(label)) config.replaceAngleBrackets = value;
		else if ("Preserve Chat Input".equals(label)) config.preserveChatInput = value;
	}

	private String getToggleText(boolean value) {
		return value ? "\u00a7aON" : "\u00a7cOFF";
	}

	private class OptionEntry {
		final String label;
		final String description;
		final int x;
		final int y;
		final ButtonWidget button;
		final boolean isToggle;

		OptionEntry(int id, String label, String description, int x, int y, boolean isToggle) {
			this.label = label;
			this.description = description;
			this.x = x;
			this.y = y;
			this.isToggle = true;
			this.button = new ButtonWidget(id, x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
					getToggleText(getConfigValue(label)));
		}

		/** Toggle button with a custom width (for split-row layouts). */
		OptionEntry(int id, String label, String description, int x, int y, int buttonWidth) {
			this.label = label;
			this.description = description;
			this.x = x;
			this.y = y;
			this.isToggle = true;
			this.button = new ButtonWidget(id, x, y, buttonWidth, BUTTON_HEIGHT,
					getToggleText(getConfigValue(label)));
		}

		OptionEntry(String label, String description, int x, int y) {
			this.label = label;
			this.description = description;
			this.x = x;
			this.y = y;
			this.isToggle = false;
			this.button = null;
		}

		void toggle() {
			if (!isToggle) return;
			boolean current = getConfigValue(label);
			setConfigValue(label, !current);
			button.message = getToggleText(!current);
		}
	}
}
