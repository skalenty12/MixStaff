package ru.autosetting.feature;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import ru.autosetting.AutoSettingClient;

import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Discord Rich Presence через локальный IPC-пайп Discord (без внешних библиотек).
 * Показывает «Играет в MixerGrief», ник, сервер и время в игре.
 */
public final class DiscordRpc {
    /** Application ID из Discord Developer Portal. */
    private static final String APP_ID = "1566329932114563072";
    /** Ключ ассета иконки в Rich Presence Art Assets. */
    private static final String ICON_ASSET = "mixstaff";
    private static final Gson GSON = new Gson();
    private static final long SESSION_START = System.currentTimeMillis() / 1000L;

    private static volatile boolean started;
    private static volatile boolean ready;
    private static RandomAccessFile handle;
    private static FileOutputStream output;
    private static DataInputStream input;

    private DiscordRpc() {
    }

    public static synchronized void init() {
        if (started) {
            return;
        }
        started = true;
        Thread thread = new Thread(DiscordRpc::loop, "MixStaff-DiscordRPC");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loop() {
        while (true) {
            try {
                if (output == null) {
                    connect();
                }
                update();
                Thread.sleep(5_000L);
            } catch (InterruptedException exception) {
                return;
            } catch (Exception exception) {
                closePipe();
                try {
                    Thread.sleep(30_000L);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }
    }

    private static void connect() throws Exception {
        Exception last = null;
        for (int index = 0; index < 10; index++) {
            try {
                RandomAccessFile candidate = new RandomAccessFile(
                        "\\\\.\\pipe\\discord-ipc-" + index, "rw");
                FileDescriptor descriptor = candidate.getFD();
                output = new FileOutputStream(descriptor);
                input = new DataInputStream(new FileInputStream(descriptor));
                handle = candidate;
                break;
            } catch (Exception exception) {
                last = exception;
            }
        }
        if (output == null) {
            throw last == null ? new IllegalStateException("Discord IPC не найден") : last;
        }

        JsonObject handshake = new JsonObject();
        handshake.addProperty("v", 1);
        handshake.addProperty("client_id", APP_ID);
        send(0, handshake);
        startReader();
        AutoSettingClient.LOGGER.info("Discord RPC подключён");
    }

    private static void startReader() {
        DataInputStream reader = input;
        Thread thread = new Thread(() -> {
            try {
                while (reader != null && reader == input) {
                    byte[] header = new byte[8];
                    reader.readFully(header);
                    int length = (header[4] & 0xFF) | (header[5] & 0xFF) << 8
                            | (header[6] & 0xFF) << 16 | (header[7] & 0xFF) << 24;
                    if (length < 0 || length > 1 << 20) {
                        break;
                    }
                    reader.readFully(new byte[length]);
                    ready = true;
                }
            } catch (Exception ignored) {
                // соединение закрыто — основной поток переподключится
            }
        }, "MixStaff-DiscordRPC-reader");
        thread.setDaemon(true);
        thread.start();
    }

    private static void update() throws Exception {
        MinecraftClient client = MinecraftClient.getInstance();
        String nick = "Scally_Milano";
        if (client.getSession() != null && client.getSession().getUsername() != null) {
            nick = client.getSession().getUsername();
        }
        String server = "Меню";
        if (client.getCurrentServerEntry() != null) {
            ServerInfo info = client.getCurrentServerEntry();
            server = info.address == null || info.address.isBlank() ? info.name : info.address;
        } else if (client.world != null) {
            server = "Одиночная игра";
        }
        if (server.length() > 60) {
            server = server.substring(0, 60);
        }

        JsonObject activity = new JsonObject();
        activity.addProperty("details", "Играет в MixerGrief");
        activity.addProperty("state", nick + " · " + server);
        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", SESSION_START);
        activity.add("timestamps", timestamps);
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", ICON_ASSET);
        assets.addProperty("large_text", "MixerGrief");
        assets.addProperty("small_image", ICON_ASSET);
        assets.addProperty("small_text", "Scally_Milano");
        activity.add("assets", assets);

        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);

        JsonObject payload = new JsonObject();
        payload.addProperty("cmd", "SET_ACTIVITY");
        payload.add("args", args);
        payload.addProperty("nonce", UUID.randomUUID().toString());
        send(1, payload);
    }

    private static synchronized void send(int opcode, JsonObject payload) throws Exception {
        FileOutputStream stream = output;
        if (stream == null) {
            return;
        }
        byte[] json = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[8 + json.length];
        frame[0] = (byte) opcode;
        frame[1] = 0;
        frame[2] = 0;
        frame[3] = 0;
        frame[4] = (byte) json.length;
        frame[5] = (byte) (json.length >>> 8);
        frame[6] = (byte) (json.length >>> 16);
        frame[7] = (byte) (json.length >>> 24);
        System.arraycopy(json, 0, frame, 8, json.length);
        stream.write(frame);
        stream.flush();
    }

    private static void closePipe() {
        ready = false;
        try {
            if (input != null) {
                input.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (output != null) {
                output.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (handle != null) {
                handle.close();
            }
        } catch (Exception ignored) {
        }
        input = null;
        output = null;
        handle = null;
    }

    public static boolean isReady() {
        return ready;
    }
}
