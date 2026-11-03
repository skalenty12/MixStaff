package ru.autosetting.feature;

import ru.autosetting.AutoSettingClient;
import ru.autosetting.config.ModConfig;
import ru.autosetting.util.PlayerNames;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Список жалоб для reports-list. Каждая запись хранится строкой
 * {@code КАТЕГОРИЯ|ник|причина|время}.
 * <p>
 * Список наполняется командой {@code .report Ник причина}, а также
 * автоматически после {@code /reports}: мод несколько секунд слушает чат
 * и собирает строки вида {@code Ник » причина}.
 */
public final class ReportsStore {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_REPORTS = 128;
    private static final long COLLECT_MS = 8_000L;

    private static final Pattern CAPTURE = Pattern.compile(
            "^(?:\\s*[#№]?\\s*\\d+[.)]?\\s*)?(?:\\[[^\\]]*\\]\\s*)*"
                    + "([A-Za-z0-9_]{3,16})\\s*[»>›:—–|\\-]+\\s*(.+)$");

    private static final String[] CHEAT_WORDS = {
            "чит", "cheat", "killaura", "kill aura", "ka ", "aim", "fly", "xray", "esp",
            "autoclick", "auto click", "macro", "reach", "speed", "nuker", "скрипт", "hack",
            "античит", "бот"
    };
    private static final String[] TEAM_WORDS = {
            "тим", "team", "союз", "альянс", "тима", "груп", "clan", "клан", "друз"
    };

    private static long collectUntil;
    private static boolean dirty;

    private ReportsStore() {
    }

    public enum Category {
        CHEAT("Чит", 0xFF34D399),
        TEAM("Тим", 0xFF3B9EFF),
        OTHER("Прочее", 0xFF7E828F);

        public final String label;
        public final int color;

        Category(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    public record Entry(Category category, String nick, String reason, String time) {
    }

    /** Начинает сбор жалоб после того, как модератор прописал /reports. */
    public static void beginCollect() {
        ModConfig config = ModConfig.get();
        config.reports.clear();
        collectUntil = System.currentTimeMillis() + COLLECT_MS;
        dirty = true;
        config.save();
    }

    public static boolean isCollecting() {
        return System.currentTimeMillis() <= collectUntil;
    }

    /**
     * Заменяет список жалоб результатом сканирования открытого меню /reports.
     * Пластинки из GUI — основной источник, поэтому сбор из чата сразу
     * останавливается.
     */
    public static void applyScan(List<Entry> scanned) {
        ModConfig config = ModConfig.get();
        config.reports.clear();
        for (int index = scanned.size() - 1; index >= 0; index--) {
            config.reports.add(encode(scanned.get(index)));
        }
        collectUntil = 0;
        dirty = true;
    }

    /** Вызывается на каждую строку чата: пока идёт сбор — добавляет жалобу. */
    public static void capture(String plain) {
        if (!isCollecting() || plain == null || plain.isBlank()) {
            return;
        }
        String clean = plain.replaceAll("§.", "").trim();
        Matcher matcher = CAPTURE.matcher(clean);
        String nick;
        String reason;
        if (matcher.matches()) {
            nick = matcher.group(1);
            reason = matcher.group(2).trim();
        } else {
            // Запасной разбор: ищем в строке ник из таб-листа и берём
            // всё после него как причину («1) Nick — cheat» и подобное).
            String candidate = PlayerNames.resolve(clean, null);
            if (candidate == null) {
                return;
            }
            int index = clean.toLowerCase(Locale.ROOT)
                    .indexOf(candidate.toLowerCase(Locale.ROOT));
            if (index < 0) {
                return;
            }
            nick = candidate;
            reason = clean.substring(index + candidate.length())
                    .replaceAll("^[\\s\\[\\]()#№\\d.,:»>›►▸➥≫|—–\\-]+", "")
                    .trim();
        }
        if (reason.length() < 2) {
            return;
        }
        if (reason.length() > 60) {
            reason = reason.substring(0, 60) + "…";
        }
        addRaw(categoryOf(reason), nick, reason);
    }

    public static void add(String nick, String reason) {
        String cleanReason = reason == null ? "" : reason.trim().replaceAll("\\s+", " ");
        if (cleanReason.isBlank()) {
            cleanReason = "без причины";
        }
        if (cleanReason.length() > 60) {
            cleanReason = cleanReason.substring(0, 60) + "…";
        }
        addRaw(categoryOf(cleanReason), nick, cleanReason);
    }

    private static void addRaw(Category category, String nick, String reason) {
        ModConfig config = ModConfig.get();
        for (String raw : config.reports) {
            Entry entry = decode(raw);
            if (entry != null && entry.nick().equalsIgnoreCase(nick)
                    && entry.reason().equalsIgnoreCase(reason)) {
                return;
            }
        }
        config.reports.add(0, encode(new Entry(category, nick, reason, LocalTime.now().format(TIME))));
        while (config.reports.size() > MAX_REPORTS) {
            config.reports.remove(config.reports.size() - 1);
        }
        dirty = true;
        AutoSettingClient.LOGGER.debug("reports: {} {} {}", category, nick, reason);
    }

    /** Вызывается каждый тик: сохраняет накопленное и закрывает сбор. */
    public static void tick() {
        if (dirty && !isCollecting()) {
            dirty = false;
            ModConfig.get().save();
        }
    }

    public static List<Entry> entries() {
        List<Entry> result = new ArrayList<>();
        for (String raw : ModConfig.get().reports) {
            Entry entry = decode(raw);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    public static int count(Category category) {
        int total = 0;
        for (Entry entry : entries()) {
            if (entry.category() == category) {
                total++;
            }
        }
        return total;
    }

    public static List<Entry> byCategory(Category category) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries()) {
            if (entry.category() == category) {
                result.add(entry);
            }
        }
        return result;
    }

    public static int total() {
        return ModConfig.get().reports.size();
    }

    public static int removeNick(String nick) {
        ModConfig config = ModConfig.get();
        int before = config.reports.size();
        config.reports.removeIf(raw -> {
            Entry entry = decode(raw);
            return entry != null && entry.nick().equalsIgnoreCase(nick);
        });
        if (config.reports.size() != before) {
            config.save();
        }
        return before - config.reports.size();
    }

    public static void clear() {
        ModConfig config = ModConfig.get();
        config.reports.clear();
        config.save();
    }

    public static Category categoryOf(String reason) {
        String lower = reason.toLowerCase(Locale.ROOT);
        for (String word : CHEAT_WORDS) {
            if (lower.contains(word)) {
                return Category.CHEAT;
            }
        }
        for (String word : TEAM_WORDS) {
            if (lower.contains(word)) {
                return Category.TEAM;
            }
        }
        return Category.OTHER;
    }

    private static String encode(Entry entry) {
        return entry.category().name() + "|" + entry.nick() + "|" + entry.reason() + "|" + entry.time();
    }

    private static Entry decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("\\|", 4);
        if (parts.length == 4) {
            try {
                return new Entry(Category.valueOf(parts[0]), parts[1], parts[2], parts[3]);
            } catch (IllegalArgumentException ignored) {
                // старая запись без категории — обработаем ниже
            }
        }
        // Совместимость со старым форматом «ник — причина · 18:04».
        String text = raw;
        String time = "";
        int dot = text.lastIndexOf(" · ");
        if (dot > 0) {
            time = text.substring(dot + 3).trim();
            text = text.substring(0, dot).trim();
        }
        String[] parts2 = text.split(" — ", 2);
        if (parts2.length == 2) {
            return new Entry(categoryOf(parts2[1]), parts2[0].trim(), parts2[1].trim(), time);
        }
        return null;
    }
}
