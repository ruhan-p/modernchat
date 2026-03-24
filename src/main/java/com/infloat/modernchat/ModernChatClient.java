package com.infloat.modernchat;

import net.fabricmc.api.ClientModInitializer;
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.input.Keyboard;

public class ModernChatClient implements ClientModInitializer {

	public static KeyBinding configKeyBinding;

	@Override
	public void onInitializeClient() {
		ModernChatConfig.INSTANCE.load();

		configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.modernchat.config",
				Keyboard.KEY_P,
				"key.categories.modernchat"
		));
	}
}
