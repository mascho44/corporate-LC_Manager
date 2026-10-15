package de.ostms.lc.check.service;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.lc.domain.LetterOfCredit;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Draft of a notice of discrepancies from the confirmed discrepancies of a file: an MT734 (advice of refusal) in SWIFT character set X and a letter to the bank.
 * A draft for the user to complete and send; nothing is sent from here.
 */
public final class DiscrepancyNotice {
    public record Draft(int discrepancies, String mt734, String letter) {}
    static final int LINE = 65, MAX_LINES = 70;
    private DiscrepancyNotice() {}

    /** Confirmed discrepancies (a person confirmed the discrepancy), in the order of the review. */
    static List<CheckResult> confirmed(List<CheckResult> results) {
        return results.stream().filter(r -> "CONFIRMED_DISCREPANCY".equals(r.reviewDecision())).toList();
    }

    public static Draft create(LetterOfCredit lc, List<CheckResult> results, LocalDate today) {
        var items = confirmed(results);
        var lines = new ArrayList<String>();
        int number = 1;
        for (var r : items) {
            String text = (r.documentName() == null || r.documentName().isBlank() ? "" : r.documentName() + ": ") + r.message()
                + (r.lcCondition() == null || r.lcCondition().isBlank() ? "" : " (LC: " + r.lcCondition() + ")");
            lines.addAll(wrap((number++) + ". " + swiftX(text), LINE));
        }
        if (lines.size() > MAX_LINES) { lines = new ArrayList<>(lines.subList(0, MAX_LINES - 1)); lines.add("FURTHER DISCREPANCIES SEE SEPARATE MESSAGE"); }
        String amount = lc.getAmount() == null ? "" : lc.getAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        var mt = new StringBuilder();
        mt.append(":20:").append(swiftX(value(lc.getOwnBankReference(), lc.getReference()))).append('\n');
        mt.append(":21:").append(swiftX(value(lc.getForeignBankReference(), "NONREF"))).append('\n');
        mt.append(":32A:").append(today.format(DateTimeFormatter.ofPattern("yyMMdd"))).append(value(lc.getCurrency(), "XXX")).append(amount).append('\n');
        mt.append(":77J:").append(lines.isEmpty() ? "" : String.join("\n", lines)).append('\n');
        mt.append(":77B:/HOLD/ DOCUMENTS PENDING YOUR INSTRUCTIONS");
        var letter = new StringBuilder();
        letter.append("Betreff: Unstimmigkeiten zum Akkreditiv ").append(value(lc.getReference(), "")).append('\n');
        if (lc.getForeignBankReference() != null && !lc.getForeignBankReference().isBlank()) letter.append("Ihre Referenz: ").append(lc.getForeignBankReference()).append('\n');
        letter.append("\nSehr geehrte Damen und Herren,\n\nbei der Prüfung der vorgelegten Dokumente haben wir folgende Unstimmigkeiten festgestellt:\n\n");
        number = 1;
        for (var r : items) letter.append(number++).append(". ").append(r.documentName() == null || r.documentName().isBlank() ? "" : r.documentName() + ": ").append(r.message())
            .append(r.reviewComment() == null || r.reviewComment().isBlank() ? "" : " – " + r.reviewComment()).append('\n');
        letter.append("\nWir halten die Dokumente zu Ihrer Verfügung und bitten um Ihre Weisung.\n\nMit freundlichen Grüßen\n");
        return new Draft(items.size(), mt.toString(), letter.toString());
    }

    private static String value(String v, String fallback) { return v == null || v.isBlank() ? fallback : v.strip(); }

    /** SWIFT character set X: upper case, umlauts transliterated, other characters replaced by a blank. */
    static String swiftX(String text) {
        String v = text.replace("Ä", "AE").replace("Ö", "OE").replace("Ü", "UE").replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace("„", "'").replace("“", "'").replace("”", "'").replace("–", "-").replace("…", "...");
        v = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
        return v.replaceAll("[^A-Z0-9/\\-?:().,'+ \n]", " ").replaceAll(" {2,}", " ").trim();
    }

    /** Words onto lines of at most `width` characters; a word longer than a line is cut. */
    static List<String> wrap(String text, int width) {
        var out = new ArrayList<String>(); var line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            while (word.length() > width) { if (line.length() > 0) { out.add(line.toString()); line.setLength(0); } out.add(word.substring(0, width)); word = word.substring(width); }
            if (line.length() > 0 && line.length() + 1 + word.length() > width) { out.add(line.toString()); line.setLength(0); }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
