package de.ostms.lc.rulepack;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.lc.domain.LetterOfCredit;
import java.util.*;
import java.util.regex.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/**
 * Reads what the credit demands per document type from the items of field 46A ("+SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS"):
 * signature, number of originals and issuer. Values are only proposed when an item names them unambiguously.
 */
public final class Mt700Requirements {
    public record Suggestion(DocumentType documentType, Field field, String value, String source) {}

    private static final Pattern FRACTION = Pattern.compile("\\b(\\d)\\s*/\\s*(\\d)\\s*(?:SET|ORIGINALS?|ORIG)\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b(\\d|ONE|TWO|THREE|FOUR|FIVE)\\s*(?:\\(\\d\\)\\s*)?ORIGINALS?\\b");
    private static final Pattern ISSUED_BY = Pattern.compile("\\bISSUED\\s+BY\\s+([A-Z][A-Z &.\\-]{3,60}?)(?=[.,;\\n\\r]|\\s+(?:AND|SHOWING|STATING|EVIDENCING|CERTIFYING|IN)\\b|$)");
    /** Order matters: an item that mentions several documents belongs to the first one named here. */
    private static final LinkedHashMap<DocumentType, Pattern> TYPES = new LinkedHashMap<>();
    static {
        TYPES.put(DocumentType.BILL_OF_LADING, Pattern.compile("BILLS?\\s+OF\\s+LADING|\\bB/L\\b|SEA\\s+WAYBILL"));
        TYPES.put(DocumentType.AIR_WAYBILL, Pattern.compile("AIR\\s*WAY\\s*BILL"));
        TYPES.put(DocumentType.ROAD_CONSIGNMENT_NOTE, Pattern.compile("\\bCMR\\b|ROAD\\s+CONSIGNMENT"));
        TYPES.put(DocumentType.CERTIFICATE_OF_ORIGIN, Pattern.compile("CERTIFICATE\\s+OF\\s+ORIGIN"));
        TYPES.put(DocumentType.INSURANCE_CERTIFICATE, Pattern.compile("INSURANCE\\s+(?:POLICY|CERTIFICATE)"));
        TYPES.put(DocumentType.INSPECTION_CERTIFICATE, Pattern.compile("INSPECTION\\s+CERTIFICATE"));
        TYPES.put(DocumentType.PACKING_LIST, Pattern.compile("PACKING\\s+LIST"));
        TYPES.put(DocumentType.COMMERCIAL_INVOICE, Pattern.compile("COMMERCIAL\\s+INVOICE|\\bINVOICE\\b"));
    }

    private Mt700Requirements() {}

    public static List<Suggestion> detect(LetterOfCredit lc) {
        String field46a = Mt700Facts.tags(lc).get("46A");
        if (field46a == null || field46a.isBlank()) return List.of();
        var out = new ArrayList<Suggestion>();
        for (String item : items(field46a)) {
            String upper = item.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
            DocumentType type = typeOf(upper);
            if (type == null) continue;
            if (Pattern.compile("\\bSIGNED\\b").matcher(upper).find())
                out.add(new Suggestion(type, Field.LC_SIGNATURE_REQUIRED, "true", "Feld :46A:"));
            var counts = new LinkedHashSet<Integer>();
            var fraction = FRACTION.matcher(upper);
            while (fraction.find()) counts.add(Integer.parseInt(fraction.group(2)));
            var number = NUMBER.matcher(upper);
            while (number.find()) counts.add(toNumber(number.group(1)));
            if (counts.size() == 1) out.add(new Suggestion(type, Field.LC_REQUIRED_ORIGINAL_COUNT, String.valueOf(counts.iterator().next()), "Feld :46A:"));
            var issuers = new LinkedHashSet<String>();
            var issued = ISSUED_BY.matcher(upper);
            while (issued.find()) issuers.add(issued.group(1).trim());
            if (issuers.size() == 1) out.add(new Suggestion(type, Field.LC_REQUIRED_ISSUER, issuers.iterator().next(), "Feld :46A:"));
        }
        return dedupe(out);
    }

    /** Items start with "+" (or a numbered line); a text without markers counts as one item. */
    private static List<String> items(String field46a) {
        var items = new ArrayList<String>();
        var current = new StringBuilder();
        for (String line : field46a.split("\\r?\\n")) {
            String t = line.trim();
            boolean start = t.startsWith("+") || t.matches("^\\d{1,2}[.)]\\s.*");
            if (start && current.length() > 0) { items.add(current.toString()); current.setLength(0); }
            current.append(' ').append(start ? t.replaceFirst("^(?:\\+|\\d{1,2}[.)])\\s*", "") : t);
        }
        if (current.length() > 0) items.add(current.toString());
        return items;
    }

    private static DocumentType typeOf(String upper) {
        for (var e : TYPES.entrySet()) if (e.getValue().matcher(upper).find()) return e.getKey();
        return null;
    }

    private static int toNumber(String v) {
        return switch (v) { case "ONE" -> 1; case "TWO" -> 2; case "THREE" -> 3; case "FOUR" -> 4; case "FIVE" -> 5; default -> Integer.parseInt(v); };
    }

    /** The same document type named in several items with different values is ambiguous: propose nothing for that field. */
    private static List<Suggestion> dedupe(List<Suggestion> all) {
        var grouped = new LinkedHashMap<String, List<Suggestion>>();
        for (var s : all) grouped.computeIfAbsent(s.documentType() + "|" + s.field(), k -> new ArrayList<>()).add(s);
        var out = new ArrayList<Suggestion>();
        for (var list : grouped.values()) {
            if (list.stream().map(Suggestion::value).distinct().count() == 1) out.add(list.get(0));
        }
        return out;
    }
}
