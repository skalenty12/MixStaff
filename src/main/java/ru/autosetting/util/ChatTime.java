package ru.autosetting.util;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** ChatTime: добавляет [ЧЧ:ММ:СС] перед каждым сообщением чата. */
public final class ChatTime {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ChatTime() {
    }

    public static Text prefix(Text message) {
        return Text.literal("[" + LocalTime.now().format(FORMAT) + "] ")
                .formatted(Formatting.DARK_GRAY)
                .append(message);
    }
}
