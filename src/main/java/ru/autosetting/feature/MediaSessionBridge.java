package ru.autosetting.feature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.autosetting.AutoSettingClient;
import ru.autosetting.config.ModConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Unifies the Windows SMTC helper and an optional localhost WebSocket fallback. */
public final class MediaSessionBridge {
    public enum Source {
        AUTO,
        WINDOWS,
        WEBSOCKET
    }

    public record Track(String title, String artist, boolean playing, int duration,
                        int position, String artwork) {
    }

    private static final URI SOCKET_URI = URI.create("ws://127.0.0.1:8765");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final ScheduledExecutorService RETRIES = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MixStaff-Media-WebSocket");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile WebSocket socket;
    private static volatile Track socketTrack;
    private static volatile long socketTrackAt;
    private static volatile boolean socketConnecting;

    private MediaSessionBridge() {
    }

    public static void start() {
        if (!ModConfig.get().musicIsland) {
            return;
        }
        Source source = source();
        if (source != Source.WEBSOCKET) {
            MediaWatcher.start();
        }
        if (source != Source.WINDOWS) {
            connectSocket();
        }
    }

    public static void stop() {
        MediaWatcher.stop();
        WebSocket current = socket;
        socket = null;
        socketTrack = null;
        socketTrackAt = 0L;
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "feature disabled");
        }
    }

    public static Source source() {
        try {
            return Source.valueOf(ModConfig.get().musicIslandSource);
        } catch (IllegalArgumentException exception) {
            return Source.AUTO;
        }
    }

    public static Track track() {
        Source source = source();
        if (source != Source.WEBSOCKET && MediaWatcher.hasTrack()) {
            return new Track(MediaWatcher.title(), MediaWatcher.artist(), MediaWatcher.isPlaying(),
                    MediaWatcher.duration(), MediaWatcher.position(), "");
        }
        Track fallback = socketTrack;
        if (source != Source.WINDOWS && fallback != null
                && System.currentTimeMillis() - socketTrackAt <= 30_000L) {
            return fallback;
        }
        return null;
    }

    public static void send(String command) {
        Source source = source();
        WebSocket current = socket;
        if (source == Source.WEBSOCKET && current != null) {
            current.sendText("{\"command\":\"" + command.toLowerCase(java.util.Locale.ROOT) + "\"}", true);
        } else if (source == Source.WINDOWS || MediaWatcher.hasTrack()) {
            MediaWatcher.send(command);
        } else if (current != null) {
            current.sendText("{\"command\":\"" + command.toLowerCase(java.util.Locale.ROOT) + "\"}", true);
        }
    }

    public static void setSource(Source source) {
        if (source == null) {
            throw new IllegalArgumentException("Media source cannot be null");
        }
        ModConfig.get().musicIslandSource = source.name();
        ModConfig.get().save();
        if (source == Source.WEBSOCKET) {
            MediaWatcher.stop();
        }
        if (source != Source.AUTO) {
            WebSocket current = socket;
            socket = null;
            socketTrack = null;
            if (current != null) {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "source changed");
            }
        }
        start();
    }

    private static void connectSocket() {
        if (!ModConfig.get().musicIsland || source() == Source.WINDOWS
                || socket != null || socketConnecting) {
            return;
        }
        socketConnecting = true;
        HTTP.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .buildAsync(SOCKET_URI, new Listener())
                .whenComplete((connected, error) -> {
                    socketConnecting = false;
                    if (error != null) {
                        scheduleReconnect();
                        return;
                    }
                    socket = connected;
                    connected.request(1);
                });
    }

    private static void scheduleReconnect() {
        RETRIES.schedule(MediaSessionBridge::connectSocket, 5, TimeUnit.SECONDS);
    }

    private static long number(JsonObject json, String... names) {
        for (String name : names) {
            JsonElement value = json.get(name);
            if (value != null && value.isJsonPrimitive()) {
                try {
                    return value.getAsLong();
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private static String string(JsonObject json, String... names) {
        for (String name : names) {
            JsonElement value = json.get(name);
            if (value != null && value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }
        return "";
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder message = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            if (!ModConfig.get().musicIsland || source() == Source.WINDOWS) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "source unavailable");
                return;
            }
            socket = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                parseMessage(message.toString());
                message.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socket = null;
            socketTrack = null;
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            socket = null;
            AutoSettingClient.LOGGER.debug("Media WebSocket unavailable: {}", error.getMessage());
            scheduleReconnect();
        }
    }

    private static void parseMessage(String raw) {
        try {
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            String title = string(json, "title", "track", "name").trim();
            if (title.isEmpty()) {
                socketTrack = null;
                return;
            }
            String artist = string(json, "artist", "author").trim();
            long duration = number(json, "durationSeconds", "duration");
            long position = number(json, "positionSeconds", "position");
            if (json.has("durationMs")) {
                duration = number(json, "durationMs") / 1000L;
            }
            if (json.has("positionMs")) {
                position = number(json, "positionMs") / 1000L;
            }
            JsonElement playingValue = json.get("playing");
            boolean playing = playingValue != null && playingValue.isJsonPrimitive()
                    && ("true".equalsIgnoreCase(playingValue.getAsString())
                    || "playing".equalsIgnoreCase(playingValue.getAsString()));
            Track previous = socketTrack;
            socketTrack = new Track(title, artist, playing,
                    (int) Math.max(0L, Math.min(Integer.MAX_VALUE, duration)),
                    (int) Math.max(0L, Math.min(Integer.MAX_VALUE, position)),
                    string(json, "artwork", "cover", "artworkUrl"));
            socketTrackAt = System.currentTimeMillis();
            if (previous == null || !previous.title().equals(title) || !previous.artist().equals(artist)) {
                LyricsService.request(title, artist);
            }
        } catch (RuntimeException exception) {
            AutoSettingClient.LOGGER.debug("Ignoring invalid media WebSocket message", exception);
        }
    }
}
