package dev.blurredclient.mod.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.blurredclient.mod.BlurredMod;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synced lyrics for the song you're playing, from LRCLIB (lrclib.net), a
 * free public lyrics database that needs no account. Only the artist, title
 * and length of the current song are sent, and only while Lyrics is on.
 * Each song is looked up once and kept for the session.
 */
public final class Lyrics {
    private Lyrics() {}

    /** One timed line. {@code at} is seconds from the start of the song. */
    public record Line(double at, String text) {}

    private static final List<Line> NONE = Collections.emptyList();
    private static final Map<String, List<Line>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();
    private static final Pattern TAG = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    /** Lines for this song, or an empty list while loading or if there are none. */
    public static List<Line> forSong(String artist, String title, double length) {
        if (title == null || title.isBlank()) {
            return NONE;
        }
        String key = (artist + "\u0000" + title).toLowerCase();
        List<Line> got = CACHE.get(key);
        if (got != null) {
            return got;
        }
        if (PENDING.putIfAbsent(key, Boolean.TRUE) == null) {
            fetch(key, artist, title, length);
        }
        return NONE;
    }

    /** Index of the line playing at {@code position} seconds, or -1 before the first. */
    public static int lineAt(List<Line> lines, double position) {
        int found = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).at() <= position) {
                found = i;
            } else {
                break;
            }
        }
        return found;
    }

    private static void fetch(String key, String artist, String title, double length) {
        String q = "artist_name=" + enc(artist) + "&track_name=" + enc(cleanTitle(title));
        if (length > 0) {
            q += "&duration=" + Math.round(length);
        }
        request("https://lrclib.net/api/get?" + q).thenCompose(body -> {
            List<Line> lines = body == null ? NONE : parseGet(body);
            // A real match has many timed lines; a handful is a placeholder entry.
            if (lines.size() >= 3) {
                return java.util.concurrent.CompletableFuture.completedFuture(lines);
            }
            // Not an exact match: search by artist and title instead.
            return request("https://lrclib.net/api/search?q=" + enc(artist + " " + cleanTitle(title)))
                    .thenApply(b -> b == null ? NONE : parseSearch(b));
        }).whenComplete((lines, err) -> {
            CACHE.put(key, lines == null ? NONE : lines);
            PENDING.remove(key);
            if (err != null) {
                BlurredMod.LOGGER.debug("Lyrics lookup failed", err);
            }
        });
    }

    private static java.util.concurrent.CompletableFuture<String> request(String url) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Blurred Client (Minecraft mod; lyrics display)")
                .GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> r.statusCode() == 200 ? r.body() : null)
                .exceptionally(e -> null);
    }

    private static List<Line> parseGet(String body) {
        try {
            JsonElement e = JsonParser.parseString(body);
            if (e.isJsonObject()) {
                return lrc(e.getAsJsonObject());
            }
        } catch (RuntimeException ignored) {
        }
        return NONE;
    }

    private static List<Line> parseSearch(String body) {
        try {
            JsonElement e = JsonParser.parseString(body);
            if (e.isJsonArray()) {
                JsonArray arr = e.getAsJsonArray();
                for (JsonElement item : arr) {
                    if (item.isJsonObject()) {
                        List<Line> lines = lrc(item.getAsJsonObject());
                        if (lines.size() >= 3) {
                            return lines;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return NONE;
    }

    /** Parse LRC text ("[01:23.45] line") into timed lines. */
    private static List<Line> lrc(JsonObject o) {
        JsonElement synced = o.get("syncedLyrics");
        if (synced == null || synced.isJsonNull()) {
            return NONE;
        }
        List<Line> out = new ArrayList<>();
        for (String raw : synced.getAsString().split("\n")) {
            Matcher m = TAG.matcher(raw);
            int end = 0;
            List<Double> times = new ArrayList<>();
            while (m.find() && m.start() == end) {
                times.add(Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2)));
                end = m.end();
            }
            String text = raw.substring(end).trim();
            for (double t : times) {
                out.add(new Line(t, text));
            }
        }
        out.sort((a, b) -> Double.compare(a.at(), b.at()));
        return out;
    }

    /** "Song (feat. X) - Remastered 2011" -> "Song". */
    private static String cleanTitle(String title) {
        String t = title.replaceAll("\\s*[(\\[](feat|ft|with)\\.?[^)\\]]*[)\\]]", "");
        int dash = t.indexOf(" - ");
        if (dash > 0 && t.toLowerCase().substring(dash).matches(".*(remaster|live|version|edit|mix).*")) {
            t = t.substring(0, dash);
        }
        return t.trim();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
