package com.infloat.modernchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class ModernChatConfig {
	public static final ModernChatConfig INSTANCE = new ModernChatConfig();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final File CONFIG_FILE = new File("config", "modernchat.json");

	public boolean autocomplete = true;
	public int chatHistory = 16384;
	public boolean smoothAnimations = true;
	public boolean raiseChat = true;
	public boolean compactChatSpam = true;
	public boolean maintainChatHistory = true;
	public boolean replaceAngleBrackets = false;

	public void load() {
		if (!CONFIG_FILE.exists()) {
			save();
			return;
		}

		try {
			Reader reader = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8);
			try {
				ModernChatConfig loaded = GSON.fromJson(reader, ModernChatConfig.class);
				if (loaded != null) {
					this.autocomplete = loaded.autocomplete;
					this.chatHistory = clampHistory(loaded.chatHistory);
					this.smoothAnimations = loaded.smoothAnimations;
					this.raiseChat = loaded.raiseChat;
					this.compactChatSpam = loaded.compactChatSpam;
					this.maintainChatHistory = loaded.maintainChatHistory;
					this.replaceAngleBrackets = loaded.replaceAngleBrackets;
				}
			} finally {
				reader.close();
			}
		} catch (Exception e) {
			System.err.println("[ModernChat] Failed to load config: " + e.getMessage());
			save();
		}
	}

	public void save() {
		CONFIG_FILE.getParentFile().mkdirs();
		try {
			Writer writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8);
			try {
				GSON.toJson(this, writer);
			} finally {
				writer.close();
			}
		} catch (Exception e) {
			System.err.println("[ModernChat] Failed to save config: " + e.getMessage());
		}
	}

	public static int clampHistory(int value) {
		return Math.max(10, Math.min(16384, value));
	}
}