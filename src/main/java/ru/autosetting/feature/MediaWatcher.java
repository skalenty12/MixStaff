package ru.autosetting.feature;

import net.fabricmc.loader.api.FabricLoader;
import ru.autosetting.AutoSettingClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Слушает системный медиаплеер Windows (SMTC): Spotify, браузер, любой плеер,
 * который отдаёт информацию в систему. Управление — медиа-клавишами.
 */
public final class MediaWatcher {
    private static volatile boolean started;
    private static volatile boolean hasTrack;
    private static volatile boolean playing;
    private static volatile String title = "";
    private static volatile String artist = "";
    private static volatile int duration;
    private static volatile int basePosition;
    private static volatile long baseTime;
    private static volatile Process process;
    private static volatile BufferedWriter input;
    private static volatile Thread worker;
    private static volatile long lastTrackChange;
    private static volatile long lastLineAt;
    private static volatile boolean loggedFirstTrack;

    private MediaWatcher() {
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        worker = new Thread(MediaWatcher::loop, "MixStaff-Media");
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void stop() {
        started = false;
        Thread currentWorker = worker;
        worker = null;
        if (currentWorker != null) {
            currentWorker.interrupt();
        }
        kill();
    }

    private static void loop() {
        while (started) {
            try {
                boolean stale = process != null && process.isAlive()
                        && lastLineAt > 0 && System.currentTimeMillis() - lastLineAt > 25_000L;
                if (stale) {
                    kill();
                }
                if (process == null || !process.isAlive()) {
                    spawn();
                }
                Thread.sleep(10_000L);
            } catch (InterruptedException exception) {
                return;
            } catch (Exception exception) {
                AutoSettingClient.LOGGER.warn("Медиа-помощник не запустился: {}", exception.getMessage());
                try {
                    Thread.sleep(20_000L);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }
    }

    private static void spawn() throws Exception {
        Path script = FabricLoader.getInstance().getConfigDir().resolve("mixstaff_media.ps1");
        try (InputStream resource = MediaWatcher.class.getResourceAsStream(
                "/assets/autosetting/mixstaff_media.ps1")) {
            if (resource == null) {
                return;
            }
            Files.createDirectories(script.getParent());
            Files.copy(resource, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", script.toString());
        Process startedProcess = builder.start();
        lastLineAt = 0L;
        input = new BufferedWriter(new OutputStreamWriter(
                startedProcess.getOutputStream(), StandardCharsets.UTF_8));
        process = startedProcess;

        Thread reader = new Thread(() -> read(startedProcess), "MixStaff-Media-reader");
        reader.setDaemon(true);
        reader.start();
        Thread errorReader = new Thread(() -> readErrors(startedProcess), "MixStaff-Media-errors");
        errorReader.setDaemon(true);
        errorReader.start();
        AutoSettingClient.LOGGER.info("Медиа-помощник запущен");
    }

    private static void readErrors(Process startedProcess) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                startedProcess.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    AutoSettingClient.LOGGER.warn("Медиа-помощник: {}", line);
                }
            }
        } catch (Exception exception) {
            AutoSettingClient.LOGGER.debug("Не удалось прочитать ошибки медиа-помощника", exception);
        }
    }

    private static void kill() {
        Process current = process;
        process = null;
        input = null;
        lastLineAt = 0L;
        hasTrack = false;
        playing = false;
        title = "";
        artist = "";
        if (current != null) {
            try {
                current.destroyForcibly();
            } catch (Exception ignored) {
                // процесс уже завершился
            }
        }
    }

    private static void read(Process startedProcess) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                startedProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parse(line);
            }
        } catch (Exception exception) {
            AutoSettingClient.LOGGER.debug("Медиа-помощник завершился с ошибкой", exception);
        }
        title = "";
        artist = "";
        duration = 0;
        basePosition = 0;
        hasTrack = false;
    }

    private static void parse(String line) {
        if (line == null) {
            return;
        }
        lastLineAt = System.currentTimeMillis();
        if (line.startsWith("ERR|")) {
            AutoSettingClient.LOGGER.warn("Медиа-помощник: {}", line.substring(4));
            return;
        }
        if (line.equals("NONE")) {
            hasTrack = false;
            playing = false;
            title = "";
            artist = "";
            duration = 0;
            basePosition = 0;
            return;
        }
        if (!line.startsWith("M|")) {
            return;
        }
        String[] parts = line.split("\\|", 6);
        if (parts.length < 6) {
            return;
        }
        String status = parts[1];
        String newTitle = parts[2].trim();
        String newArtist = parts[3].trim();
        if (!newTitle.equals(title) || !newArtist.equals(artist)) {
            lastTrackChange = System.currentTimeMillis();
            LyricsService.request(newTitle, newArtist);
            if (!loggedFirstTrack) {
                loggedFirstTrack = true;
                AutoSettingClient.LOGGER.info("Медиа: {} — {}", newTitle, newArtist);
            }
        }
        title = newTitle;
        artist = newArtist;
        playing = status.equalsIgnoreCase("Playing");
        duration = parseInt(parts[4]);
        basePosition = parseInt(parts[5]);
        baseTime = System.currentTimeMillis();
        hasTrack = !newTitle.isBlank();
    }

    private static int parseInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    public static void send(String command) {
        try {
            BufferedWriter writer = input;
            if (writer != null) {
                writer.write(command);
                writer.newLine();
                writer.flush();
            }
        } catch (Exception exception) {
            AutoSettingClient.LOGGER.debug("Не удалось отправить медиа-команду", exception);
        }
    }

    public static boolean hasTrack() {
        return hasTrack;
    }

    public static boolean isPlaying() {
        return playing;
    }

    public static String title() {
        return title;
    }

    public static String artist() {
        return artist;
    }

    public static int duration() {
        return duration;
    }

    public static long trackChangedAt() {
        return lastTrackChange;
    }

    public static int position() {
        int value = basePosition;
        if (playing) {
            value += (int) ((System.currentTimeMillis() - baseTime) / 1000L);
        }
        return duration > 0 ? Math.min(value, duration) : value;
    }
}
