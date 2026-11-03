package ru.autosetting.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Диагностика чата: пишет в latest.log, чтобы можно было понять,
 * в каком формате сервер присылает сообщения.
 */
public final class AutoSettingLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("autosetting");
    private static final int MAX_DUMPED_MESSAGES = 300;

    private static int dumped;

    private AutoSettingLog() {
    }

    public static void chat(String plain, String nick) {
        if (dumped >= MAX_DUMPED_MESSAGES) {
            return;
        }
        dumped++;
        LOGGER.info("[AutoSpec] chat #{} nick={} raw='{}'", dumped, nick, plain);
    }

    public static void debug(String message) {
        LOGGER.info("[AutoSpec] {}", message);
    }
}
