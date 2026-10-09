package de.ostms.lc.swift;

import java.util.Map;
import java.util.regex.Pattern;

/** Resolves only explicit, uniquely labelled address blocks; never treats all conditions as an address. */
public final class BeneficiaryReferenceResolver {
    private static final Pattern REFERENCE = Pattern.compile("(?i)\\b(?:SEE|REFER(?:\\s+TO)?|SIEHE|VIDE)\\s+(?:(?:FIELD|FLD|TAG|FELD)\\s*:?\\s*)?(\\d{2}[A-Z]?)\\b");
    private static final Pattern HEADER = Pattern.compile("(?i)^\\s*(?:[+*-]|\\d+[.)])?\\s*(?:(?:FULL\\s+)?(?:ADDRESS|NAME(?:\\s+AND\\s+ADDRESS)?)\\s+OF\\s+(?:THE\\s+)?BENEFICIARY|BENEFICIARY(?:'S)?(?:\\s+FULL)?(?:\\s+(?:NAME(?:\\s*(?:AND|&|/)\\s*ADDRESS)?|ADDRESS|DETAILS))?|BEGÜNSTIGTER|BEGÜNSTIGTENADRESSE)\\s*[:\\-]\\s*(.*)$");

    private BeneficiaryReferenceResolver() {}

    public record Resolution(String value, String source, boolean resolved) {}

    public static Resolution resolve(Map<String, String> fields) {
        String original = fields.get("59");
        if (original == null) return new Resolution(null, null, false);
        var reference = REFERENCE.matcher(original);
        if (!reference.find()) return new Resolution(original, null, false);
        String source = reference.group(1).toUpperCase(java.util.Locale.ROOT);
        if (reference.find() || source.equals("59")) return new Resolution(original, source, false);
        String text = fields.get(source);
        if (text == null) return new Resolution(original, source, false);
        StringBuilder address = new StringBuilder();
        boolean collecting = false;
        int matches = 0;
        for (String line : text.split("\\R")) {
            var header = HEADER.matcher(line);
            if (header.matches()) {
                matches++;
                collecting = true;
                if (!header.group(1).isBlank()) address.append(header.group(1).trim()).append('\n');
            } else if (collecting) {
                // A new paragraph, numbered condition or labelled section ends the address.
                if (line.isBlank() || line.matches("\\s*(?:[+*]|\\d+[.)]).*") || line.contains(":")) collecting = false;
                else address.append(line.trim()).append('\n');
            }
        }
        String value = address.toString().trim();
        if (matches != 1 || value.isBlank() || REFERENCE.matcher(value).find()) return new Resolution(original, source, false);
        return new Resolution(value, source, true);
    }
}
