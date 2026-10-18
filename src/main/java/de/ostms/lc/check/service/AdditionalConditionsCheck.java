package de.ostms.lc.check.service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Splits the additional conditions (:47A:) into their numbered items and recognises the few that can be checked mechanically:
 * "all documents must show this credit number" and "all documents must be issued in English". Everything else stays a manual item.
 */
public final class AdditionalConditionsCheck {
    public enum Kind { CREDIT_NUMBER_ON_ALL_DOCUMENTS, ENGLISH_ON_ALL_DOCUMENTS, OTHER }
    public record Item(String text, Kind kind) {}

    private static final Pattern NUMBERED = Pattern.compile("(?:^|\\s)(?:\\d{1,2}[.)]\\s+|\\+\\s*)");
    private static final Pattern ALL_DOCUMENTS = Pattern.compile("\\b(?:ALL|EACH|EVERY|ANY)\\s+(?:THE\\s+)?(?:PRESENTED\\s+)?DOCUMENTS?\\b");
    private static final Pattern CREDIT_NUMBER = Pattern.compile("\\b(?:SHOW|BEAR|QUOTE|MENTION|STATE|INDICATE|CARRY|CONTAIN|REFER)\\w*\\b.{0,40}?\\b(?:DOCUMENTARY\\s+CREDIT|L/?C|CREDIT)\\s+(?:NUMBER|NO\\.?|REFERENCE)\\b");
    private static final Pattern ENGLISH = Pattern.compile("\\b(?:ISSUED|PREPARED|MADE\\s+OUT|DRAWN\\s+UP|PRESENTED|WRITTEN|COMPLETED)\\s+IN\\s+(?:THE\\s+)?ENGLISH\\b");

    private AdditionalConditionsCheck() {}

    public static List<Item> parse(String conditions) {
        if (conditions == null || conditions.isBlank()) return List.of();
        String text = conditions.replaceAll("\\s+", " ").trim();
        var markers = new ArrayList<int[]>();
        var matcher = NUMBERED.matcher(text);
        while (matcher.find()) markers.add(new int[]{matcher.start(), matcher.end()});
        var items = new ArrayList<Item>();
        if (markers.isEmpty()) {
            items.add(new Item(text, kind(text)));
            return items;
        }
        for (int i = 0; i < markers.size(); i++) {
            int to = i + 1 < markers.size() ? markers.get(i + 1)[0] : text.length();
            String part = text.substring(markers.get(i)[1], to).trim();
            if (!part.isEmpty()) items.add(new Item(part, kind(part)));
        }
        return items;
    }

    static Kind kind(String item) {
        String upper = item.toUpperCase(Locale.ROOT);
        if (ALL_DOCUMENTS.matcher(upper).find() && CREDIT_NUMBER.matcher(upper).find()) return Kind.CREDIT_NUMBER_ON_ALL_DOCUMENTS;
        if (ALL_DOCUMENTS.matcher(upper).find() && ENGLISH.matcher(upper).find()) return Kind.ENGLISH_ON_ALL_DOCUMENTS;
        return Kind.OTHER;
    }

    /** True if the text contains the credit number, ignoring case, spaces, hyphens and other separators. */
    public static boolean showsReference(String documentText, String reference) {
        if (documentText == null || reference == null) return false;
        String wanted = reference.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (wanted.length() < 4) return false;
        return documentText.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT).contains(wanted);
    }

    public enum Language { ENGLISH, OTHER, UNSURE }
    public record Guess(Language language, String detected, int englishWords, int otherWords) {}

    private static final Set<String> EN = Set.of("THE", "AND", "OF", "TO", "FOR", "WITH", "BY", "ON", "IS", "ARE", "THIS", "THAT", "FROM", "AT", "BE", "AS", "WE", "OUR", "YOUR", "SHALL", "NOT", "WILL", "HAS", "HAVE", "DATE", "AMOUNT", "TOTAL", "GOODS", "PAYMENT", "PLEASE");
    private static final Map<String, Set<String>> OTHER = Map.of(
            "Deutsch", Set.of("UND", "DER", "DIE", "DAS", "VON", "MIT", "FÜR", "FUER", "NICHT", "EINE", "EIN", "IST", "SIND", "WIR", "IHRE", "DEN", "DEM", "ZUR", "ZUM", "AUF", "BETRAG", "RECHNUNG", "DATUM", "ZAHLUNG", "WARE", "LIEFERUNG", "BITTE"),
            "Französisch", Set.of("ET", "LE", "LA", "LES", "DES", "DU", "POUR", "AVEC", "PAS", "UNE", "UN", "EST", "SONT", "NOUS", "VOTRE", "FACTURE", "MONTANT", "DATE", "PAIEMENT", "MARCHANDISES"),
            "Spanisch", Set.of("Y", "EL", "LA", "LOS", "LAS", "DE", "DEL", "PARA", "CON", "NO", "UNA", "UN", "ES", "SON", "FACTURA", "IMPORTE", "FECHA", "PAGO", "MERCANCÍAS"),
            "Italienisch", Set.of("E", "IL", "LO", "LA", "GLI", "DI", "DEL", "PER", "CON", "NON", "UNA", "UN", "FATTURA", "IMPORTO", "DATA", "PAGAMENTO", "MERCI"));

    /** Rough language check by common function words; says UNSURE when the text is short or mixed. */
    public static Guess guess(String documentText) {
        if (documentText == null) return new Guess(Language.UNSURE, null, 0, 0);
        var words = documentText.toUpperCase(Locale.ROOT).split("[^\\p{L}]+");
        int english = 0, total = 0;
        var other = new LinkedHashMap<String, Integer>();
        for (String w : words) {
            if (w.length() < 1) continue;
            total++;
            if (EN.contains(w)) english++;
            for (var language : OTHER.entrySet()) if (language.getValue().contains(w) && w.length() > 1) other.merge(language.getKey(), 1, Integer::sum);
        }
        var best = other.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        int otherWords = best == null ? 0 : best.getValue();
        if (total < 25 || english + otherWords < 6) return new Guess(Language.UNSURE, null, english, otherWords);
        if (english >= otherWords * 2) return new Guess(Language.ENGLISH, "Englisch", english, otherWords);
        if (otherWords >= english * 2) return new Guess(Language.OTHER, best.getKey(), english, otherWords);
        return new Guess(Language.UNSURE, null, english, otherWords);
    }
}
