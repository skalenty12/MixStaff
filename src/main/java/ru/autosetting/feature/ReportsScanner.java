package ru.autosetting.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Считает жалобы из меню, которое открывает сервер после {@code /reports}.
 * <p>
 * В этом меню каждая жалоба — отдельная «пластинка» (предмет с именем):
 * зелёная — читы, синяя — тим, чёрная/серая — прочее. Сканер читает
 * содержимое контейнера и раскладывает пластинки по категориям.
 */
public final class ReportsScanner {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern SEPARATOR = Pattern.compile("\\s*[»>›|:—–\\-]+\\s*");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^[#№\\s]*\\d+[.)]?\\s*");
    private static final Pattern LEADING_TAGS = Pattern.compile("^(\\[[^\\]]{1,24}\\]\\s*)+");
    private static final Pattern REPORT_TITLE = Pattern.compile(
            "(?iu)(?:жалоба|репорт|report)\\s+(?:на|от)\\s+([A-Za-z0-9_]{3,16})");
    private static final Pattern REASON_LABEL = Pattern.compile(
            "(?iu)^(?:причина|reason|нарушение|описание|тип жалобы)\\s*[:：»\\-]\\s*(.+)$");

    /** Служебные кнопки меню — их не считаем жалобами. */
    private static final String[] SKIP_WORDS = {
            "назад", "вперёд", "вперед", "далее", "закрыть", "обновить", "страниц",
            "поиск", "меню", "выход", "следующ", "предыдущ", "фильтр", "информац"
    };

    private static boolean armed;
    private static long deadline;
    private static int ticks;
    private static String lastSnapshot;
    private static int stableSnapshots;
    private static List<ReportsStore.Entry> latestEntries = List.of();

    private ReportsScanner() {
    }

    /** Вызывается после отправки /reports: ждём открытия меню с пластинками. */
    public static void arm() {
        armed = true;
        deadline = System.currentTimeMillis() + 8_000L;
        ticks = 0;
        lastSnapshot = null;
        stableSnapshots = 0;
        latestEntries = List.of();
    }

    public static void tick(MinecraftClient client) {
        if (!armed) {
            return;
        }
        if (System.currentTimeMillis() > deadline) {
            ReportsStore.applyScan(latestEntries);
            armed = false;
            return;
        }
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
            ticks = 0;
            return;
        }
        // Даём серверу дослать содержимое контейнера.
        ticks++;
        if (ticks < 5 || ticks % 10 != 0) {
            return;
        }
        List<ReportsStore.Entry> entries = scan(screen.getScreenHandler());
        if (entries.isEmpty()) {
            latestEntries = List.of();
            lastSnapshot = null;
            stableSnapshots = 0;
            return;
        }
        String snapshot = signature(entries);
        latestEntries = entries;
        if (snapshot.equals(lastSnapshot)) {
            stableSnapshots++;
        } else {
            lastSnapshot = snapshot;
            stableSnapshots = 1;
        }
        if (stableSnapshots >= 3) {
            ReportsStore.applyScan(latestEntries);
            armed = false;
        }
    }

    private static List<ReportsStore.Entry> scan(ScreenHandler handler) {
        List<ReportsStore.Entry> entries = new ArrayList<>();
        int containerSlots = Math.max(0, handler.slots.size() - 36);
        String time = LocalTime.now().format(TIME);
        for (int index = 0; index < containerSlots; index++) {
            ItemStack stack = handler.slots.get(index).getStack();
            if (stack.isEmpty()) {
                continue;
            }
            Text name = stack.getName();
            String plain = name.getString().trim();
            if (plain.isBlank() || isService(plain)) {
                continue;
            }
            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            String itemPath = Registries.ITEM.getId(stack.getItem()).getPath();
            boolean coloredPlateItem = itemPath.contains("green") || itemPath.contains("lime")
                    || itemPath.contains("blue") || itemPath.contains("cyan")
                    || itemPath.contains("black") || itemPath.contains("gray")
                    || itemPath.contains("grey") || itemPath.contains("music_disc")
                    || itemPath.contains("record");
            if (stack.getCustomName() == null && lore == null && itemPath.contains("stained_glass_pane")) {
                continue;
            }
            if (stack.getCustomName() == null && lore == null
                    && !coloredPlateItem) {
                continue;
            }
            ReportsStore.Category category = categoryOfItem(stack);
            if (category == null) {
                category = categoryOf(firstColor(name));
            }
            if (category == null) {
                if (lore != null) {
                    for (Text line : lore.lines()) {
                        category = categoryOf(firstColor(line));
                        if (category != null) {
                            break;
                        }
                    }
                }
            }
            if (category == null) {
                category = ReportsStore.Category.OTHER;
            }
            String[] parts = splitName(plain);
            List<String> loreLines = new ArrayList<>();
            if (lore != null) {
                for (Text line : lore.lines()) {
                    loreLines.add(line.getString());
                }
            }
            parts = extractReport(parts, loreLines);
            if (category == ReportsStore.Category.OTHER) {
                ReportsStore.Category reasonCategory = ReportsStore.categoryOf(parts[1]);
                if (reasonCategory != ReportsStore.Category.OTHER) {
                    category = reasonCategory;
                }
            }
            for (int count = 0; count < stack.getCount(); count++) {
                entries.add(new ReportsStore.Entry(category, parts[0], parts[1], time));
            }
        }
        return entries;
    }

    private static boolean isService(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String word : SKIP_WORDS) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return lower.matches("^[\\d\\s.,:#№()\\[\\]-]+$");
    }

    /** Первый заданный цвет в тексте или его дочерних элементах. */
    private static Integer firstColor(Text text) {
        TextColor color = text.getStyle().getColor();
        if (color != null) {
            return color.getRgb();
        }
        for (Text sibling : text.getSiblings()) {
            Integer rgb = firstColor(sibling);
            if (rgb != null) {
                return rgb;
            }
        }
        return null;
    }

    /** Зелёная пластинка — читы, синяя — тим, всё тёмное — прочее. */
    private static ReportsStore.Category categoryOf(Integer rgb) {
        if (rgb == null) {
            return null;
        }
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        if (green > red + 30 && green > blue + 30) {
            return ReportsStore.Category.CHEAT;
        }
        if (blue > red + 30 && blue + 10 >= green) {
            return ReportsStore.Category.TEAM;
        }
        if (Math.max(red, Math.max(green, blue)) < 80) {
            return ReportsStore.Category.OTHER;
        }
        return null;
    }

    /** Если имя без цвета — смотрим на сам предмет: зелёная/синяя/чёрная пластинка. */
    private static ReportsStore.Category categoryOfItem(ItemStack stack) {
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        if (path.contains("green") || path.contains("lime")) {
            return ReportsStore.Category.CHEAT;
        }
        if (path.contains("blue") || path.contains("cyan")) {
            return ReportsStore.Category.TEAM;
        }
        if (path.contains("black") || path.contains("gray") || path.contains("grey")) {
            return ReportsStore.Category.OTHER;
        }
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        if (name.contains("зелён") || name.contains("зелен") || name.contains("green")) {
            return ReportsStore.Category.CHEAT;
        }
        if (name.contains("син") || name.contains("blue") || name.contains("cyan")) {
            return ReportsStore.Category.TEAM;
        }
        if (name.contains("чёрн") || name.contains("черн") || name.contains("black")) {
            return ReportsStore.Category.OTHER;
        }
        return null;
    }

    private static String signature(List<ReportsStore.Entry> entries) {
        StringBuilder builder = new StringBuilder(entries.size() * 32);
        for (ReportsStore.Entry entry : entries) {
            builder.append(entry.category()).append('|').append(entry.nick()).append('|')
                    .append(entry.reason()).append('\n');
        }
        return builder.toString();
    }

    /** Разбивает «Ник » причина» на ник и причину. */
    private static String[] splitName(String plain) {
        String clean = LEADING_TAGS.matcher(LEADING_NUMBER.matcher(plain).replaceFirst(""))
                .replaceFirst("").trim();
        String nick = "—";
        String reason = "";
        Matcher reportTitle = REPORT_TITLE.matcher(clean);
        if (reportTitle.find()) {
            nick = reportTitle.group(1);
            reason = clean.substring(reportTitle.end()).replaceFirst("^[\\s:：»>›|—–\\-]+", "").trim();
        }
        Matcher separator = SEPARATOR.matcher(clean);
        if (nick.equals("—") && separator.find() && separator.start() > 0) {
            String head = clean.substring(0, separator.start()).trim();
            String tail = clean.substring(separator.end()).trim();
            String candidate = lastNick(head);
            if (!candidate.isBlank() && !tail.isBlank()) {
                nick = candidate;
                reason = tail;
            }
        }
        if (reason.isBlank() && nick.equals("—")) {
            reason = clean;
        }
        return new String[]{nick, reason};
    }

    private static String[] extractReport(String[] parsed, List<String> loreLines) {
        String reason = parsed[1];
        for (String rawLine : loreLines) {
            String line = rawLine == null ? "" : rawLine.replaceAll("§.", "").trim();
            Matcher labeled = REASON_LABEL.matcher(line);
            if (labeled.find() && isUsefulReason(labeled.group(1))) {
                reason = labeled.group(1).trim();
                break;
            }
            if (reason.isBlank() && isUsefulReason(line) && !isMetadata(line)) {
                reason = line;
            }
        }
        if (reason.isBlank()) {
            reason = "причина не указана";
        }
        if (reason.length() > 60) {
            reason = reason.substring(0, 60) + "…";
        }
        return new String[]{parsed[0], reason};
    }

    private static boolean isUsefulReason(String line) {
        return line != null && !line.isBlank() && line.length() > 1
                && !REPORT_TITLE.matcher(line).find();
    }

    private static boolean isMetadata(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("нажмите") || lower.contains("нажми") || lower.contains("клик")
                || lower.contains("игрок:") || lower.contains("ник:") || lower.contains("никнейм")
                || lower.contains("время:") || lower.contains("дата:")
                || lower.matches("^[\\d\\s.:/,-]+$");
    }

    private static String lastNick(String head) {
        Matcher matcher = NICK.matcher(head);
        String result = "";
        while (matcher.find()) {
            result = matcher.group();
        }
        return result;
    }
}
