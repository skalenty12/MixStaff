package ru.autosetting.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("autosetting");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance;

    public boolean autoMute = true;
    public boolean autoMuteDetect = true;
    public boolean autoProof = true;
    public boolean chatTime = true;
    public boolean hudHints = true;
    public boolean targetHud = true;
    public boolean autoSpeck = true;
    public boolean sounds = true;
    public boolean bigHead = false;
    public int bigHeadScale = 2;
    public int clickGuiKey = 344;
    public int wheelKey = 342;
    public int muteKeyCode = 77;
    public int specKeyCode = 74;

    /** Свободный обзор: клавиша по умолчанию Z. */
    public boolean freelook = true;
    public int freelookKey = 90;

    /** Шаблоны проверки: клавиша по умолчанию H. */
    public boolean templates = true;
    public int templatesKey = 72;

    /** Ночное зрение (fullbright). */
    public boolean fullbright = false;

    /** Автокопирование AnyDesk из чата. */
    public boolean autoCopyAnydesk = true;
    public boolean antiAfk = false;

    public float targetScale = 1.0F;
    /** Позиция центра Target HUD (0..1). */
    public float targetX = 0.5F;
    public float targetY = 0.40F;

    public boolean reportsHud = true;
    public float reportsX = 0.012F;
    public float reportsY = 0.30F;
    public float reportsScale = 1.0F;
    public java.util.List<String> reports = new java.util.ArrayList<>();

    /** Окно настроек: позиция (0..1) и масштаб. */
    public float menuX = 0.5F;
    public float menuY = 0.5F;
    public float menuScale = 1.0F;

    /** Плавающее окно главного меню. */
    public float homeMenuX = 0.5F;
    public float homeMenuY = 0.5F;
    public float homeMenuScale = 1.0F;

    /** Положение и масштаб временных HUD-уведомлений (центр, 0..1). */
    public float specHudX = 0.5F;
    public float specHudY = 0.53F;
    public float specHudScale = 1.0F;
    public float muteHudX = 0.5F;
    public float muteHudY = 0.45F;
    public float muteHudScale = 1.0F;
    public float anydeskHudX = 0.5F;
    public float anydeskHudY = 0.55F;
    public float anydeskHudScale = 1.0F;
    public float musicIslandX = 0.5F;
    public float musicIslandY = 0.03F;
    public float musicIslandScale = 1.0F;
    public int musicIslandKey = 0;
    public String musicIslandSource = "AUTO";

    /** Позиция карточки сервера в меню «Сетевая игра» (0..1). */
    public float cardX = 0.04F;
    public float cardY = 0.60F;

    public boolean musicIsland = true;
    public boolean discordRpc = true;

    private ModConfig() {
    }

    public static ModConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("autosetting.json");
    }

    public static void load() {
        ModConfig config = new ModConfig();
        Path path = path();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (Exception exception) {
                LOGGER.warn("Не удалось прочитать конфиг AutoSetting, используются значения по умолчанию", exception);
            }
        }
        if (config.bigHeadScale != 2 && config.bigHeadScale != 4 && config.bigHeadScale != 8) {
            config.bigHeadScale = 2;
        }
        if (config.reports == null) {
            config.reports = new java.util.ArrayList<>();
        }
        config.targetX = clamp01(config.targetX, 0.5F);
        config.targetY = clamp01(config.targetY, 0.40F);
        config.cardX = clamp01(config.cardX, 0.04F);
        config.cardY = clamp01(config.cardY, 0.60F);
        config.menuX = clamp01(config.menuX, 0.5F);
        config.menuY = clamp01(config.menuY, 0.5F);
        config.menuScale = clamp(config.menuScale, 0.6F, 1.6F, 1.0F);
        config.homeMenuX = clamp01(config.homeMenuX, 0.5F);
        config.homeMenuY = clamp01(config.homeMenuY, 0.5F);
        config.homeMenuScale = clamp(config.homeMenuScale, 0.6F, 1.6F, 1.0F);
        config.specHudX = clamp01(config.specHudX, 0.5F);
        config.specHudY = clamp01(config.specHudY, 0.53F);
        config.specHudScale = clamp(config.specHudScale, 0.6F, 2.0F, 1.0F);
        config.muteHudX = clamp01(config.muteHudX, 0.5F);
        config.muteHudY = clamp01(config.muteHudY, 0.45F);
        config.muteHudScale = clamp(config.muteHudScale, 0.6F, 2.0F, 1.0F);
        config.anydeskHudX = clamp01(config.anydeskHudX, 0.5F);
        config.anydeskHudY = clamp01(config.anydeskHudY, 0.55F);
        config.anydeskHudScale = clamp(config.anydeskHudScale, 0.6F, 2.0F, 1.0F);
        config.musicIslandX = clamp01(config.musicIslandX, 0.5F);
        config.musicIslandY = clamp01(config.musicIslandY, 0.03F);
        config.musicIslandScale = clamp(config.musicIslandScale, 0.6F, 2.0F, 1.0F);
        if (config.musicIslandSource == null
                || (!"AUTO".equals(config.musicIslandSource)
                && !"WINDOWS".equals(config.musicIslandSource)
                && !"WEBSOCKET".equals(config.musicIslandSource))) {
            config.musicIslandSource = "AUTO";
        }
        config.reportsScale = clamp(config.reportsScale, 0.7F, 1.6F, 1.0F);
        instance = config;
    }

    private static float clamp(float value, float min, float max, float fallback) {
        if (Float.isNaN(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value, float fallback) {
        if (Float.isNaN(value) || value < 0.0F || value > 1.0F) {
            return fallback;
        }
        return value;
    }

    public void save() {
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception exception) {
            LOGGER.warn("Не удалось сохранить конфиг AutoSetting", exception);
        }
    }
}
