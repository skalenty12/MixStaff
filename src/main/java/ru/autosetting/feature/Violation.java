package ru.autosetting.feature;

/** Найденное нарушение: пункт правил и пояснение для причины мута. */
public record Violation(ChatRule rule, String detail) {
}
