package ru.autosetting.feature;

import java.util.Locale;

/**
 * Пункты правил чата MixerGrief и наказания за них.
 */
public enum ChatRule {
    FLOOD("2.1", "Флуд, спам, Caps Lock", 10, 60, false, true),
    MAT("2.2", "Нецензурная лексика, оскорбления, 18+", 60, 360, false, true),
    STAFF("2.3", "Оскорбление сотрудника проекта", 5 * 24 * 60, 10 * 24 * 60, true, true),
    FAMILY("2.4", "Оскорбление родителей и родственников", 12 * 60, 24 * 60, false, true),
    DOX("2.5", "Угрозы доксом, сватом и т.п.", 3 * 60, 10 * 60, false, true),
    AD("2.6", "Упоминание и реклама других проектов", 12 * 60, 24 * 60, false, true),
    BEG("2.7", "Попрошайничество", 30, 120, false, true);

    private final String clause;
    private final String title;
    private final int minMinutes;
    private final int maxMinutes;
    private final boolean accountBlock;
    private final boolean chatPunishment;

    ChatRule(String clause, String title, int minMinutes, int maxMinutes,
             boolean accountBlock, boolean chatPunishment) {
        this.clause = clause;
        this.title = title;
        this.minMinutes = minMinutes;
        this.maxMinutes = maxMinutes;
        this.accountBlock = accountBlock;
        this.chatPunishment = chatPunishment;
    }

    public String clause() {
        return clause;
    }

    public String title() {
        return title;
    }

    public int minMinutes() {
        return minMinutes;
    }

    public int maxMinutes() {
        return maxMinutes;
    }

    public boolean accountBlock() {
        return accountBlock;
    }

    public String label() {
        return clause + " · " + title;
    }

    public String punishment() {
        if (accountBlock) {
            return "блокировка аккаунта " + minMinutes / 1440 + "–" + maxMinutes / 1440 + " дней";
        }
        return formatDuration(minMinutes) + " – " + formatDuration(maxMinutes);
    }

    public String reasonPrefix() {
        return clause + " " + title;
    }

    public static ChatRule byClause(String clause) {
        if (clause == null || clause.isBlank()) {
            return null;
        }
        String clean = clause.trim().toLowerCase(Locale.ROOT);
        for (ChatRule rule : values()) {
            if (rule.clause.equals(clean)) {
                return rule;
            }
        }
        return null;
    }

    private static String formatDuration(int minutes) {
        if (minutes < 60) {
            return minutes + " мин";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        return rest == 0 ? hours + " ч" : hours + " ч " + rest + " мин";
    }
}
