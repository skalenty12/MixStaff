package ru.autosetting.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Шрифт интерфейса мода. */
public final class Fonts {
    public static final Identifier UI = Identifier.of("autosetting", "vag_world");

    private Fonts() {
    }

    public static MutableText t(String text) {
        return Text.literal(text).setStyle(Style.EMPTY.withFont(UI));
    }

    public static MutableText t(String text, Formatting... formatting) {
        return t(text).formatted(formatting);
    }
}
