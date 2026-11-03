package ru.autosetting.feature;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expands abbreviated amounts such as {@code 1k} and {@code 1kk} in outgoing text. */
public final class Valute {
    private static final Pattern AMOUNT = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}_])([0-9]+(?:[.,][0-9]+)?)(kk|k)(?![\\p{L}\\p{N}_])");

    private Valute() {
    }

    public static String expand(String message) {
        Matcher matcher = AMOUNT.matcher(message);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            BigDecimal amount = new BigDecimal(matcher.group(1).replace(',', '.'));
            BigDecimal multiplier = matcher.group(2).equalsIgnoreCase("kk")
                    ? BigDecimal.valueOf(1_000_000L) : BigDecimal.valueOf(1_000L);
            String expanded = amount.multiply(multiplier).stripTrailingZeros().toPlainString();
            matcher.appendReplacement(result, Matcher.quoteReplacement(expanded));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
