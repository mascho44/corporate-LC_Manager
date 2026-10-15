package de.ostms.lc.swift;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.*;

/**
 * Turns a printed or OCR-read SWIFT message (bank portal print view, PDF, scan) back into ":tag:value" lines.
 * Understood: ":40A:value", table rows "40A  Form of Documentary Credit" with the value below, "F40A: Form of …" / "40A: Form of …: value",
 * and typical OCR confusions in the tag (O for 0, I/l for 1, S for 5, B for 8 in digit positions).
 */
@Component
public class PrintedSwiftNormalizer {
    private static final String TAG = "([0-9OoIlSB]{2})\\s?([A-Z])?";
    private static final Pattern COLON_FIELD = Pattern.compile("^.*?:" + TAG + ":\\s*(.*)$");
    private static final Pattern PREFIXED_FIELD = Pattern.compile("^\\s*F?" + TAG + "\\s*:\\s*(.*)$");
    private static final Pattern TABLE_FIELD = Pattern.compile("^\\s*" + TAG + "\\s+(.+?)\\s*$");
    private static final Set<String> KNOWN_FIELDS = Set.of(
            "20", "21", "23", "26E", "27", "30", "31C", "31D", "31E", "32B", "33B", "34B", "39A", "39B", "39C",
            "40A", "40E", "41A", "41D", "42A", "42C", "42D", "42M", "42P", "43P", "43T",
            "44A", "44B", "44C", "44D", "44E", "44F", "45A", "45B", "46A", "46B", "47A", "47B", "48", "49", "49G", "49H",
            "50", "51A", "51D", "52A", "52D", "53A", "53D", "54A", "54D", "56A", "56D",
            "57A", "57D", "58A", "58D", "59", "71B", "71D", "72", "72Z", "77C", "77U", "78", "79"
    );
    /** Printed field names (English, as in bank print views); a line holding only the name is a label, not a value. */
    private static final Map<String, List<String>> LABELS = Map.ofEntries(
            Map.entry("27", List.of("sequence of total")), Map.entry("40A", List.of("form of documentary credit")),
            Map.entry("20", List.of("documentary credit number", "sender's reference", "senders reference", "transaction reference number")),
            Map.entry("21", List.of("related reference", "receiver's reference")), Map.entry("23", List.of("reference to pre-advice")),
            Map.entry("26E", List.of("number of amendment")), Map.entry("30", List.of("date of amendment")),
            Map.entry("31C", List.of("date of issue")), Map.entry("31D", List.of("date and place of expiry")),
            Map.entry("32B", List.of("currency code, amount", "currency code amount", "increase of documentary credit amount")),
            Map.entry("33B", List.of("decrease of documentary credit amount")), Map.entry("34B", List.of("new documentary credit amount after amendment")),
            Map.entry("39A", List.of("percentage credit amount tolerance")), Map.entry("39C", List.of("additional amounts covered")),
            Map.entry("40E", List.of("applicable rules")), Map.entry("41A", List.of("available with ... by ...", "available with by", "available with")),
            Map.entry("41D", List.of("available with ... by ...", "available with by", "available with")),
            Map.entry("42C", List.of("drafts at")), Map.entry("42A", List.of("drawee")), Map.entry("42D", List.of("drawee")),
            Map.entry("42M", List.of("mixed payment details")), Map.entry("42P", List.of("negotiation/deferred payment details", "deferred payment details")),
            Map.entry("43P", List.of("partial shipments")), Map.entry("43T", List.of("transhipment", "transshipment")),
            Map.entry("44A", List.of("place of taking in charge/dispatch from .../place of receipt", "place of taking in charge", "place of receipt")),
            Map.entry("44B", List.of("place of final destination/for transportation to .../place of delivery", "place of final destination", "place of delivery")),
            Map.entry("44C", List.of("latest date of shipment")), Map.entry("44D", List.of("shipment period")),
            Map.entry("44E", List.of("port of loading/airport of departure", "port of loading")), Map.entry("44F", List.of("port of discharge/airport of destination", "port of discharge")),
            Map.entry("45A", List.of("description of goods and/or services", "description of goods")), Map.entry("45B", List.of("description of goods and/or services")),
            Map.entry("46A", List.of("documents required")), Map.entry("46B", List.of("documents required")),
            Map.entry("47A", List.of("additional conditions")), Map.entry("47B", List.of("additional conditions")),
            Map.entry("48", List.of("period for presentation in days", "period for presentation")), Map.entry("49", List.of("confirmation instructions")),
            Map.entry("49G", List.of("special payment conditions for beneficiary")), Map.entry("49H", List.of("special payment conditions for bank only")),
            Map.entry("50", List.of("applicant")), Map.entry("51A", List.of("applicant bank")), Map.entry("51D", List.of("applicant bank")),
            Map.entry("52A", List.of("issuing bank")), Map.entry("52D", List.of("issuing bank")),
            Map.entry("53A", List.of("reimbursing bank")), Map.entry("53D", List.of("reimbursing bank")),
            Map.entry("57A", List.of("'advise through' bank", "advise through bank")), Map.entry("57D", List.of("'advise through' bank", "advise through bank")),
            Map.entry("58A", List.of("requested confirmation party")), Map.entry("58D", List.of("requested confirmation party")),
            Map.entry("59", List.of("beneficiary")), Map.entry("71B", List.of("charges")), Map.entry("71D", List.of("charges")),
            Map.entry("72", List.of("sender to receiver information")), Map.entry("72Z", List.of("sender to receiver information")),
            Map.entry("78", List.of("instructions to the paying/accepting/negotiating bank", "instructions to the paying bank")),
            Map.entry("79", List.of("narrative"))
    );

    public String normalize(String text) {
        if (text == null) return "";
        List<String> out = new ArrayList<>();
        String code = null;
        StringBuilder value = new StringBuilder();
        for (String line : text.replace('\f', '\n').split("\\R")) {
            String[] field = field(line);
            if (field != null) {
                if (code != null) add(out, code, value);
                code = field[0];
                value = new StringBuilder(field[1]);
            } else if (code != null && !line.isBlank() && !ignored(line)) {
                String continuation = line.strip();
                if (!continuation.isBlank()) {
                    if (!value.isEmpty()) value.append('\n');
                    value.append(continuation);
                }
            }
        }
        if (code != null) add(out, code, value);
        return String.join("\n", out);
    }

    /** {tag, value on the same line} for a line that opens a field, or null. */
    static String[] field(String line) {
        Matcher colon = COLON_FIELD.matcher(line);
        if (colon.matches()) {
            String tag = tag(colon.group(1), colon.group(2));
            if (tag != null) return new String[]{tag, withoutLabel(tag, colon.group(3).trim())};
        }
        Matcher prefixed = PREFIXED_FIELD.matcher(line);
        if (prefixed.matches()) {
            String tag = tag(prefixed.group(1), prefixed.group(2));
            if (tag != null) return new String[]{tag, withoutLabel(tag, prefixed.group(3).trim())};
        }
        Matcher table = TABLE_FIELD.matcher(line);
        if (table.matches()) {
            String tag = tag(table.group(1), table.group(2));
            // The remainder of a table row is the printed field label, unless it is a value after the label ("32B Currency Code, Amount EUR 1.000,00").
            if (tag != null) return new String[]{tag, withoutLabel(tag, table.group(3).trim(), true)};
        }
        return null;
    }

    /** A known tag from OCR-tolerant digits and an optional letter; unknown combinations are not a field. */
    static String tag(String digits, String letter) {
        String d = digits.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1').replace('S', '5').replace('B', '8');
        if (!d.matches("\\d{2}")) return null;
        String candidate = d + (letter == null ? "" : letter);
        return KNOWN_FIELDS.contains(candidate) ? candidate : null;
    }

    private static String withoutLabel(String tag, String rest) { return withoutLabel(tag, rest, false); }
    /** Removes a printed field label at the start; in a table row, text that is not a known label is the label itself. */
    private static String withoutLabel(String tag, String rest, boolean tableRow) {
        String lower = rest.toLowerCase(Locale.ROOT);
        for (String label : LABELS.getOrDefault(tag, List.of())) {
            if (lower.startsWith(label)) return rest.substring(label.length()).replaceFirst("^[\\s:.-]+", "").trim();
        }
        return tableRow ? "" : rest;
    }

    private boolean ignored(String line) {
        return line.matches("(?i)^\\s*(Seite|Page|QUOTE:|UNQUOTE|Trailer|Checksum|Fieldtag\\s+Fieldname|Fielddetails|Message Details|Header).*" );
    }

    private void add(List<String> out, String code, StringBuilder value) {
        out.add(":" + code + ":" + value.toString().trim());
    }
}
