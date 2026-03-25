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
 * Handles all network I/O for community server syntax fetching.
 * All methods run on background threads; callers must not block the game thread.
 */
public class SyntaxFetcher {

    public static final String CONTENTS_API_URL = "https://api.github.com/repos/ruhan-p/modernchat/contents/server_syntaxes";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS    = 10000;

    private static final Gson GSON = new GsonBuilder().create();

    private static final File SYNTAX_DIR = new File("config/modernchat/commands");

    /** Simple callback for async results. Called from a background thread. */
    public interface Callback<T> {
        void onResult(T value, String errorOrNull);
    }

    private static class GitHubFileEntry {
        String name;
        String type;
        String download_url;
    }

    /**
     * Scans the server_syntaxes GitHub directory and returns one ServerSyntaxEntry per
     * .json file found. Name and IP are read from each file's top-level fields.
     * Callback is invoked on the background thread - update volatile screen state, not UI directly.
     */
    public static void fetchDirectory(final Callback<List<ServerSyntaxEntry>> callback) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String json = fetchString(CONTENTS_API_URL);
                    GitHubFileEntry[] files = GSON.fromJson(json, GitHubFileEntry[].class);
                    List<ServerSyntaxEntry> list = new ArrayList<ServerSyntaxEntry>();
                    if (files != null) {
                        for (GitHubFileEntry file : files) {
                            if (!"file".equals(file.type) || !file.name.endsWith(".json")) continue;
                            String key = file.name.substring(0, file.name.length() - 5);
                            String syntaxJson = fetchString(file.download_url);
                            CommandSyntaxDef def = GSON.fromJson(syntaxJson, CommandSyntaxDef.class);
                            ServerSyntaxEntry entry = new ServerSyntaxEntry();
                            entry.key  = key;
                            entry.name = (def != null && def.name != null) ? def.name : key;
                            entry.ip   = (def != null) ? def.ip : null;
                            entry.url  = file.download_url;
                            list.add(entry);
                        }
                    }
                    callback.onResult(list, null);
                } catch (Exception e) {
                    callback.onResult(null, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
            }
        }, "modernchat-syntax-fetch").start();
    }

    /**
     * Downloads a single server syntax JSON and saves it to the commands config directory.
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
                    if (def == null) throw new IOException("Invalid server syntax JSON");

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
        }, "modernchat-syntax-download-" + entry.key).start();
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
