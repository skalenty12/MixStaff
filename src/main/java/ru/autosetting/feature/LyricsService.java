package ru.autosetting.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.autosetting.AutoSettingClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Загружает синхронизированный текст песни с lrclib.net и отдаёт текущую строку. */
public final class LyricsService {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MixStaff-Lyrics");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, List<Line>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> REQUESTED = new ConcurrentHashMap<>();

    private LyricsService() {
    }

    public static void request(String title, String artist) {
        if (title == null || title.isBlank()) {
            return;
        }
        String key = key(title, artist);
        if (CACHE.containsKey(key) || REQUESTED.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        EXECUTOR.submit(() -> fetch(key, title, artist));
    }

    private static void fetch(String key, String title, String artist) {
        try {
            String url = "https://lrclib.net/api/get?track_name=" + encode(title)
                    + "&artist_name=" + encode(artist == null ? "" : artist);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "MixStaff/1.0 (Minecraft Fabric mod)")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                CACHE.put(key, List.of());
                return;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String synced = json.has("syncedLyrics") && !json.get("syncedLyrics").isJsonNull()
                    ? json.get("syncedLyrics").getAsString() : null;
            CACHE.put(key, synced == null ? List.of() : parseLrc(synced));
        } catch (Exception exception) {
            AutoSettingClient.LOGGER.debug("Не удалось загрузить текст песни", exception);
            CACHE.put(key, List.of());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String key(String title, String artist) {
        return (title + "|" + (artist == null ? "" : artist)).toLowerCase(Locale.ROOT);
    }

    private static List<Line> parseLrc(String lrc) {
        List<Line> lines = new ArrayList<>();
        for (String raw : lrc.split("\\r?\\n")) {
            String text = raw;
            int close = text.lastIndexOf(']');
            if (!text.startsWith("[") || close <= 0) {
                continue;
            }
            String stamp = text.substring(1, close);
            String content = text.substring(close + 1).trim();
            if (content.isEmpty()) {
                continue;
            }
            String[] timeParts = stamp.split(":");
            if (timeParts.length != 2) {
                continue;
            }
            try {
                int minutes = Integer.parseInt(timeParts[0].trim());
                double seconds = Double.parseDouble(timeParts[1].trim());
                lines.add(new Line((long) ((minutes * 60 + seconds) * 1000.0D), content));
            } catch (NumberFormatException ignored) {
                // строка без времени (метаданные) — пропускаем
            }
        }
        lines.sort((a, b) -> Long.compare(a.millis, b.millis));
        return lines;
    }

    /** Текущая строка текста под позицию трека (секунды). */
    public static String currentLine(String title, String artist, int positionSeconds) {
        List<Line> lines = CACHE.get(key(title, artist));
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        long position = positionSeconds * 1000L;
        String result = null;
        for (Line line : lines) {
            if (line.millis > position + 350L) {
                break;
            }
            result = line.text;
        }
        return result;
    }

    public static boolean hasLyrics(String title, String artist) {
        List<Line> lines = CACHE.get(key(title, artist));
        return lines != null && !lines.isEmpty();
    }

    private record Line(long millis, String text) {
    }
}
