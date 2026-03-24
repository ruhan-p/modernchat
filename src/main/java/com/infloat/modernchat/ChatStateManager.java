package com.infloat.modernchat;

public class ChatStateManager {
    public static final ChatStateManager INSTANCE = new ChatStateManager();

    private String savedInput = null;

    public void updateState(String text) {
        this.savedInput = text;
    }

    public void resetState() {
        this.savedInput = null;
    }

    public boolean restoreState() {
        return this.savedInput != null && !this.savedInput.isEmpty();
    }

    public String getState() {
        return this.savedInput;
    }
}
