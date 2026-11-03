package ru.autosetting.feature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aвтопоиск нарушений правил чата MixerGrief (2.1–2.7).
 * <p>
 * Детектор только предлагает мут: окончательное решение всегда за модератором.
 * Проверки сделаны консервативными, чтобы не спамить ложными срабатываниями.
 */
public final class ChatViolationDetector {
    private static final long FLOOD_WINDOW_MS = 10_000L;
    private static final int FLOOD_LIMIT = 4;
    private static final int MAX_HISTORY = 20;

    private static final Pattern LINK = Pattern.compile(
            "(?i)(?:discord\\.gg/|dsc\\.gg/|discord\\.com/invite/|t\\.me/|vk\\.com/|youtube\\.com/|youtu\\.be/|twitch\\.tv/|https?://)");
    private static final Pattern DOMAIN = Pattern.compile(
            "(?i)(?<![a-z0-9-])(?:[a-z0-9-]+\\.)+(?:ru|com|net|org|su|io|me|gg|xyz|shop|site|online)(?![a-z0-9])");
    private static final Pattern REPEATED_CHARS = Pattern.compile("(.)\\1{4,}");
    private static final Pattern SYMBOL_NOISE = Pattern.compile("^[^\\p{L}\\p{N}]+$");

    /** Слова-исключения из правил: за них мут не предлагаем. */
    private static final String[] ALLOWED = {
            "рак", "противный", "оболтус", "балбес", "подсос", "гей", "школьник", "школата",
            "крит", "клоун", "езка", "уяка", "крыса", "нуб", "бот", "нулина", "пипидастер", "лох"
    };

    /** Корни мата в кириллице (после нормализации). */
    private static final String[] MAT_ROOTS_CYR = {
            "хуй", "хуе", "хуя", "хуи", "пизд", "бляд", "блят", "заеб", "наеб", "выеб", "уеб",
            "отъеб", "долбаеб", "долбоеб", "ебал", "ебет", "ебуч", "ебу", "сука", "сучк",
            "мудак", "мудил", "пидор", "пидар", "гандон", "шлюх", "дроч", "залуп", "манда",
            "вагин", "порн", "минет", "оргазм", "дебил", "даун", "уебан", "хуесос"
    };

    /** Те же корни в транслите/латинице. */
    private static final String[] MAT_ROOTS_LAT = {
            "huy", "hui", "pizd", "blyad", "blyat", "zaeb", "naeb", "ebal", "ebat", "ebet",
            "suka", "suchk", "mudak", "pidor", "pidar", "gandon", "shlyuh", "dolbaeb", "ueb"
    };

    private static final Map<String, Deque<Entry>> HISTORY = new HashMap<>();

    private ChatViolationDetector() {
    }

    public static List<Violation> analyze(String nick, String body) {
        List<Violation> found = new ArrayList<>(3);
        if (nick == null || nick.isBlank() || body == null || body.isBlank()) {
            return found;
        }
        if (HISTORY.size() > 500) {
            HISTORY.clear();
        }

        String lower = body.toLowerCase(Locale.ROOT);
        String cyr = toCyrillic(lower);
        String lat = toLatin(lower);
        boolean mat = containsMat(cyr, lat);

        checkFlood(nick, lower, found);
        checkCaps(body, found);
        checkNoise(lower, found);

        if (mat) {
            found.add(new Violation(ChatRule.MAT, "нецензурная лексика / оскорбление"));
        }

        if (mentionsFamily(cyr) || cyr.contains("твоюмать") || cyr.contains("твоюмаму")
                || cyr.contains("вашумать")) {
            // Оскорбление родственников — приоритетнее общего мата.
            found.removeIf(violation -> violation.rule() == ChatRule.MAT);
            found.add(new Violation(ChatRule.FAMILY, "оскорбление родителей/родственников"));
        }

        if (containsAny(cyr, "докс", "деанон", "сватом", "сват ", "ддос", "ddos")
                || containsAny(lower, "найду тебя", "знаю где ты", "скину адрес", "твой адрес",
                "твои данные", "слив инфы", "вызову мусоров")) {
            found.add(new Violation(ChatRule.DOX, "угроза доксом/сватом"));
        }

        if (looksLikeAd(lower, cyr)) {
            found.add(new Violation(ChatRule.AD, "реклама/упоминание стороннего проекта"));
        }

        if (looksLikeBegging(lower)) {
            found.add(new Violation(ChatRule.BEG, "попрошайничество"));
        }

        found.sort(Comparator.comparingInt((Violation violation) -> violation.rule().maxMinutes()).reversed());
        return found.size() > 3 ? found.subList(0, 3) : found;
    }

    private static void checkFlood(String nick, String normalized, List<Violation> found) {
        long now = System.currentTimeMillis();
        Deque<Entry> history = HISTORY.computeIfAbsent(nick.toLowerCase(Locale.ROOT),
                key -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(new Entry(now, normalized));
            while (!history.isEmpty() && now - history.peekFirst().at() > FLOOD_WINDOW_MS) {
                history.removeFirst();
            }
            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }

            if (history.size() >= FLOOD_LIMIT) {
                found.add(new Violation(ChatRule.FLOOD,
                        "флуд: " + history.size() + " сообщения за 30 секунд"));
            } else {
                long same = history.stream().filter(entry -> entry.text().equals(normalized)).count();
                if (same >= FLOOD_LIMIT) {
                    found.add(new Violation(ChatRule.FLOOD, "спам: 3 одинаковых сообщения подряд"));
                }
            }
        }
    }

    private static void checkCaps(String body, List<Violation> found) {
        int letters = 0;
        int upper = 0;
        for (int index = 0; index < body.length(); index++) {
            char symbol = body.charAt(index);
            if (Character.isLetter(symbol)) {
                letters++;
                if (Character.isUpperCase(symbol)) {
                    upper++;
                }
            }
        }
        if (letters >= 12 && upper * 100 / letters > 65) {
            found.add(new Violation(ChatRule.FLOOD,
                    "Caps Lock: " + (upper * 100 / letters) + "% заглавных букв"));
        }
    }

    private static void checkNoise(String body, List<Violation> found) {
        if (body.length() >= 10 && SYMBOL_NOISE.matcher(body).matches()) {
            found.add(new Violation(ChatRule.FLOOD, "бессмысленный набор символов"));
            return;
        }
        if (REPEATED_CHARS.matcher(body).find()) {
            found.add(new Violation(ChatRule.FLOOD, "повтор одного и того же символа"));
            return;
        }
        for (String token : body.split("\\s+")) {
            if (token.length() >= 10 && token.chars().noneMatch(ChatViolationDetector::isVowel)) {
                found.add(new Violation(ChatRule.FLOOD, "бессмысленный набор букв: " + token));
                return;
            }
        }
    }

    private static boolean isVowel(int symbol) {
        return "аеёиоуыэюяaeiouy".indexOf(Character.toLowerCase(symbol)) >= 0;
    }

    private static boolean containsMat(String cyr, String lat) {
        String safeCyr = stripAllowed(cyr);
        for (String root : MAT_ROOTS_CYR) {
            if (safeCyr.contains(root)) {
                return true;
            }
        }
        for (String root : MAT_ROOTS_LAT) {
            if (lat.contains(root)) {
                return true;
            }
        }
        return false;
    }

    private static String stripAllowed(String text) {
        String result = text;
        for (String allowed : ALLOWED) {
            result = result.replace(allowed, "");
        }
        return result;
    }

    private static boolean mentionsFamily(String cyr) {
        if (!containsAny(cyr, "твоюмать", "твоюмаму", "твоюмамку", "вашумать", "твояродительница")) {
            // «мама/мать/родители» само по себе — не нарушение, нужен оскорбительный контекст.
            boolean family = containsAny(cyr, "мать", "мам", "матер", "родител", "батя", "отец", "сестр", "брат");
            boolean offensive = containsAny(cyr, "твою", "твоя", "вашу", "твоего", "твоей");
            return family && offensive && containsMat(cyr, "");
        }
        return true;
    }

    private static boolean looksLikeAd(String lower, String cyr) {
        if (lower.contains("hypixel") || lower.contains("mixergrief")) {
            return false;
        }
        if (LINK.matcher(lower).find()) {
            return true;
        }
        Matcher domain = DOMAIN.matcher(lower);
        while (domain.find()) {
            String match = domain.group();
            if (!match.contains("funtime") && !match.contains("minecraft")) {
                return true;
            }
        }
        return containsAny(cyr, "мойсервер", "нашсервер", "заходинасервер", "залетайнасервер",
                "переходинасвой", "переходитенасвой", "пиарюсервер");
    }

    private static boolean looksLikeBegging(String lower) {
        boolean ask = containsAny(lower, "дай", "дайте", "дашь", "дадите", "кинь", "киньте",
                "скинь", "скиньте", "подари", "подарите", "подкинь", "подкиньте", "продай дешевле",
                "отдай", "отдайте", "одолжи", "займи")
                || containsAny(lower, "дай ", "дайте ", "кинь ", "скинь ", "подари ", "отдай ");
        if (!ask) {
            return false;
        }
        return containsAny(lower, "деньг", "денег", "денюж", "бабк", "монет", "рубл", "алмаз", "незерит",
                "валют", "шалкер", "элитр", "талисман", "дешёв", "дешев");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Приводит текст к кириллице и убирает всё лишнее: так ловится мат с заменой символов,
     * цифрами и латинскими двойниками букв.
     */
    private static String toCyrillic(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            char mapped = switch (symbol) {
                case 'a', '@', '4' -> 'а';
                case 'b', '6' -> 'б';
                case 'c', '$' -> 'с';
                case 'e', '3' -> 'е';
                case 'h' -> 'н';
                case 'k' -> 'к';
                case 'm' -> 'м';
                case 'o', '0' -> 'о';
                case 'p' -> 'р';
                case 't', '7' -> 'т';
                case 'x' -> 'х';
                case 'y' -> 'у';
                case 'ё' -> 'е';
                default -> symbol;
            };
            if (mapped >= 'а' && mapped <= 'я') {
                builder.append(mapped);
            }
        }
        return builder.toString();
    }

    private static String toLatin(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            char mapped = switch (symbol) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4' -> 'a';
                case '6' -> 'b';
                case '$' -> 's';
                case '@' -> 'a';
                default -> symbol;
            };
            if (mapped >= 'a' && mapped <= 'z') {
                builder.append(mapped);
            }
        }
        return builder.toString();
    }

    private record Entry(long at, String text) {
    }
}
