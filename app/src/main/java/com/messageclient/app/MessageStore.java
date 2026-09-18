package com.messageclient.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class MessageStore {
    private static final String PREFS = "message_client_store";
    private static final String KEY = "templates";
    private MessageStore() {}

    public static final class MessageTemplate {
        public String id;
        public String title;
        public String category;
        public String text;
        public boolean favorite;
        public int useCount;
        public long lastUsed;

        public MessageTemplate(String id, String title, String category, String text,
                               boolean favorite, int useCount, long lastUsed) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.text = text;
            this.favorite = favorite;
            this.useCount = useCount;
            this.lastUsed = lastUsed;
        }
    }

    public static List<MessageTemplate> load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, null);
        if (raw == null || raw.trim().isEmpty()) {
            List<MessageTemplate> defaults = defaults();
            save(context, defaults);
            return defaults;
        }
        List<MessageTemplate> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                result.add(new MessageTemplate(
                        o.optString("id", UUID.randomUUID().toString()),
                        o.optString("title", "Message client"),
                        o.optString("category", ""),
                        o.optString("text", ""),
                        o.optBoolean("favorite", false),
                        o.optInt("useCount", 0),
                        o.optLong("lastUsed", 0L)
                ));
            }
        } catch (Exception e) {
            result = defaults();
            save(context, result);
        }
        return result;
    }

    public static void save(Context context, List<MessageTemplate> list) {
        JSONArray array = toJsonArray(list);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, array.toString()).apply();
    }

    public static String createBackup(List<MessageTemplate> list) {
        try {
            JSONObject root = new JSONObject();
            root.put("format", "message-client-backup");
            root.put("version", 1);
            root.put("exportedAt", System.currentTimeMillis());
            root.put("templates", toJsonArray(list));
            return root.toString(2);
        } catch (Exception e) {
            return "{\"format\":\"message-client-backup\",\"version\":1,\"templates\":[]}";
        }
    }

    public static List<MessageTemplate> parseBackup(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Fichier vide");
        }

        String trimmed = raw.trim();
        JSONArray array;

        if (trimmed.startsWith("[")) {
            array = new JSONArray(trimmed);
        } else {
            JSONObject root = new JSONObject(trimmed);
            if (!"message-client-backup".equals(root.optString("format"))) {
                throw new IllegalArgumentException("Ce fichier n'est pas une sauvegarde Message Client");
            }
            array = root.optJSONArray("templates");
            if (array == null) {
                throw new IllegalArgumentException("Sauvegarde invalide");
            }
        }

        List<MessageTemplate> result = fromJsonArray(array);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Aucun message dans cette sauvegarde");
        }
        return result;
    }

    private static JSONArray toJsonArray(List<MessageTemplate> list) {
        JSONArray array = new JSONArray();
        try {
            for (MessageTemplate m : list) {
                JSONObject o = new JSONObject();
                o.put("id", m.id);
                o.put("title", m.title);
                o.put("category", m.category);
                o.put("text", m.text);
                o.put("favorite", m.favorite);
                o.put("useCount", m.useCount);
                o.put("lastUsed", m.lastUsed);
                array.put(o);
            }
        } catch (Exception ignored) {}
        return array;
    }

    private static List<MessageTemplate> fromJsonArray(JSONArray array) throws Exception {
        List<MessageTemplate> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            String text = o.optString("text", "");
            if (text.trim().isEmpty()) continue;
            result.add(new MessageTemplate(
                    o.optString("id", UUID.randomUUID().toString()),
                    o.optString("title", "Message client"),
                    o.optString("category", ""),
                    text,
                    o.optBoolean("favorite", false),
                    o.optInt("useCount", 0),
                    o.optLong("lastUsed", 0L)
            ));
        }
        return result;
    }

    public static void markUsed(Context context, String id) {
        List<MessageTemplate> list = load(context);
        for (MessageTemplate m : list) {
            if (m.id.equals(id)) {
                m.useCount++;
                m.lastUsed = System.currentTimeMillis();
                break;
            }
        }
        save(context, list);
    }

    public static List<MessageTemplate> sorted(List<MessageTemplate> source) {
        List<MessageTemplate> copy = new ArrayList<>(source);
        Collections.sort(copy, new Comparator<MessageTemplate>() {
            @Override public int compare(MessageTemplate a, MessageTemplate b) {
                if (a.favorite != b.favorite) return a.favorite ? -1 : 1;
                if (a.useCount != b.useCount) return Integer.compare(b.useCount, a.useCount);
                if (a.lastUsed != b.lastUsed) return Long.compare(b.lastUsed, a.lastUsed);
                return a.title.compareToIgnoreCase(b.title);
            }
        });
        return copy;
    }

    private static List<MessageTemplate> defaults() {
        List<MessageTemplate> list = new ArrayList<>();
        String text = "Bonjour,\n\n" +
                "Je me permets de revenir vers vous après plusieurs tentatives pour vous joindre par téléphone, SMS et mail.\n\n" +
                "Pouvez-vous simplement me confirmer si votre projet de construction est toujours d’actualité ?\n\n" +
                "Si ce n’est plus le cas ou si vous souhaitez le mettre en pause, n’hésitez pas à me le préciser. Cela me permettra de mettre à jour votre dossier et de ne plus vous relancer inutilement.\n\n" +
                "Merci par avance pour votre retour, même très bref.\n\n" +
                "Manuel Botelho\nMaisons CPR";
        list.add(new MessageTemplate(
                UUID.randomUUID().toString(),
                "Relance projet construction",
                "Relance",
                text,
                true, 0, 0L));
        return list;
    }
}
