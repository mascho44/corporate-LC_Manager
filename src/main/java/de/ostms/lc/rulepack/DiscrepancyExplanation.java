package de.ostms.lc.rulepack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import static de.ostms.lc.rulepack.PackDefinition.*;

/** Puts a failed comparison into words, with the difference spelled out. Only simple DATE/NUMBER comparisons. */
public final class DiscrepancyExplanation {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private DiscrepancyExplanation() {}

    public static Optional<String> of(Rule rule, String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) return Optional.empty();
        try {
            String leftLabel = label(rule.left()), rightLabel = label(rule.right());
            return switch (rule.left().kind()) {
                case "DATE" -> date(rule.operator(), leftLabel, rightLabel, LocalDate.parse(left), LocalDate.parse(right));
                case "NUMBER" -> number(rule.operator(), leftLabel, rightLabel, new BigDecimal(left.trim()), new BigDecimal(right.trim()));
                default -> Optional.empty();
            };
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<String> date(Operator op, String leftLabel, String rightLabel, LocalDate left, LocalDate right) {
        long days = Math.abs(ChronoUnit.DAYS.between(left, right));
        String distance = days + (days == 1 ? " Tag" : " Tage");
        String facts = leftLabel + " " + DAY.format(left) + ", " + rightLabel + " " + DAY.format(right);
        return switch (op) {
            case LTE -> left.isAfter(right) ? Optional.of(facts + ": " + distance + " nach der zulässigen Grenze.") : Optional.empty();
            case GTE -> left.isBefore(right) ? Optional.of(facts + ": " + distance + " vor der zulässigen Grenze.") : Optional.empty();
            case EQ -> left.equals(right) ? Optional.empty() : Optional.of(facts + ": Differenz " + distance + ".");
            default -> Optional.empty();
        };
    }

    private static Optional<String> number(Operator op, String leftLabel, String rightLabel, BigDecimal left, BigDecimal right) {
        BigDecimal diff = left.subtract(right).abs();
        String facts = leftLabel + " " + format(left) + ", " + rightLabel + " " + format(right);
        return switch (op) {
            case LTE -> left.compareTo(right) > 0 ? Optional.of(facts + ": " + format(diff) + " über dem Höchstwert.") : Optional.empty();
            case GTE -> left.compareTo(right) < 0 ? Optional.of(facts + ": " + format(diff) + " unter dem Mindestwert.") : Optional.empty();
            case EQ -> left.compareTo(right) == 0 ? Optional.empty() : Optional.of(facts + ": Differenz " + format(diff) + ".");
            default -> Optional.empty();
        };
    }

    private static String format(BigDecimal value) {
        var symbols = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        return new DecimalFormat("#,##0.##", symbols).format(value.setScale(2, RoundingMode.HALF_UP));
    }

    private static String label(Field field) {
        return java.util.stream.Stream.concat(RuleFacts.definitions(true).stream(), RuleFacts.definitions(false).stream())
                .filter(d -> d.field() == field).map(RuleFacts.Definition::label).findFirst().orElse(field.name());
    }
}
