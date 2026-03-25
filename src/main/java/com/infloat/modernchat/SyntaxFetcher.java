package com.infloat.modernchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all network I/O for community preset fetching.
 * All methods run on background threads; callers must not block the game thread.
 */
public class SyntaxFetcher {

    public static final String INDEX_URL = "https://raw.githubusercontent.com/ruhan-p/modernchat/main/presets/index.json";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS    = 10000;

    private static final Gson GSON = new GsonBuilder().create();

    private static final File SYNTAX_DIR = new File("config/modernchat/commands");

    /** Simple callback for async results. Called from a background thread. */
    public interface Callback<T> {
        void onResult(T value, String errorOrNull);
    }

    private static class IndexWrapper {
        List<ServerSyntaxEntry> presets;
    }

    /**
     * Fetches the preset index from GitHub on a background thread.
     * Callback is invoked on the background thread - update volatile screen state, not UI directly.
     */
    public static void fetchIndex(final Callback<List<ServerSyntaxEntry>> callback) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String json = fetchString(INDEX_URL);
                    IndexWrapper wrapper = GSON.fromJson(json, IndexWrapper.class);
                    List<ServerSyntaxEntry> list = (wrapper != null && wrapper.presets != null)
                            ? wrapper.presets : new ArrayList<ServerSyntaxEntry>();
                    callback.onResult(list, null);
                } catch (Exception e) {
                    callback.onResult(null, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
            }
        }, "modernchat-preset-fetch").start();
    }

    /**
     * Downloads a single preset JSON and saves it to the commands config directory.
     * Sets CommandSyntaxLoader#syntaxDirty on success.
     * Callback is invoked on the background thread.
     */
    public static void downloadAndSave(final ServerSyntaxEntry entry, final Callback<Void> callback) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String json = fetchString(entry.url);

                    // Validate before writing
                    CommandSyntaxDef def = GSON.fromJson(json, CommandSyntaxDef.class);
                    if (def == null) throw new IOException("Invalid preset JSON");

                    SYNTAX_DIR.mkdirs();
                    File dest = new File(SYNTAX_DIR, entry.key + ".json");

                    OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(dest), StandardCharsets.UTF_8);
                    try {
                        w.write(json);
                    } finally {
                        w.close();
                    }

                    CommandSyntaxLoader.syntaxDirty = true;
                    callback.onResult(null, null);
                } catch (Exception e) {
                    callback.onResult(null, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
            }
        }, "modernchat-preset-download-" + entry.key).start();
    }

    /** Opens a connection to urlStr, asserts HTTP 200, and returns the response body as UTF-8. */
    private static String fetchString(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        int status = conn.getResponseCode();
        if (status != 200) {
            conn.disconnect();
            throw new IOException("HTTP " + status);
        }
        InputStream in = conn.getInputStream();
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
            return buf.toString("UTF-8");
        } finally {
            in.close();
            conn.disconnect();
        }
    }
}
