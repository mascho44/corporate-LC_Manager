package de.ostms.lc.swift;

import java.util.Map;
import java.util.regex.Pattern;

/** Resolves only explicit, uniquely labelled address blocks; never treats all conditions as an address. */
public final class BeneficiaryReferenceResolver {
    private static final Pattern REFERENCE = Pattern.compile("(?i)\\b(?:SEE|REFER(?:\\s{1,5}+TO)?|SIEHE|VIDE)\\s{1,5}+(?:(?:FIELD|FLD|TAG|FELD)\\s{0,5}+:?\\s{0,5}+)?(\\d{2}[A-Z]?)\\b");
    private static String headerFor(String role,String german){
        return "(?i)^\\s{0,5}+(?:[+*-]|\\d+[.)])?\\s{0,5}+(?:(?:FULL\\s{1,5}+)?(?:ADDRESS|NAME(?:\\s{1,5}+AND\\s{1,5}+ADDRESS)?)\\s{1,5}+OF\\s{1,5}+(?:THE\\s{1,5}+)?"+role
            +"|"+role+"(?:'S)?(?:\\s{1,5}+FULL)?(?:\\s{1,5}+(?:NAME(?:\\s{0,5}+(?:AND|&|/)\\s{0,5}+ADDRESS)?|ADDRESS|DETAILS))?|"+german+")\\s{0,5}+[:\\-]\\s{0,5}+(.*)$";
    }
    private static final Pattern HEADER = Pattern.compile(headerFor("BENEFICIARY","BEGÜNSTIGTER|BEGÜNSTIGTENADRESSE"));
    private static final Pattern APPLICANT_HEADER = Pattern.compile(headerFor("APPLICANT","AUFTRAGGEBER|ANTRAGSTELLER|AUFTRAGGEBERADRESSE"));

    private BeneficiaryReferenceResolver() {}

    public record Resolution(String value, String source, boolean resolved) {}

    public static Resolution resolve(Map<String, String> fields) {
        return resolve(fields, "59", HEADER);
    }

    /** Field 50: the applicant's address may likewise be given in a labelled block of another field, typically 47A. */
    public static Resolution resolveApplicant(Map<String, String> fields) {
        return resolve(fields, "50", APPLICANT_HEADER);
    }

    private static Resolution resolve(Map<String, String> fields, String ownField, Pattern headerPattern) {
        String original = fields.get(ownField);
        if (original == null) return new Resolution(null, null, false);
        var reference = REFERENCE.matcher(original);
        if (!reference.find()) return new Resolution(original, null, false);
        String source = reference.group(1).toUpperCase(java.util.Locale.ROOT);
        if (reference.find() || source.equals(ownField)) return new Resolution(original, source, false);
        String text = fields.get(source);
        if (text == null) return new Resolution(original, source, false);
        StringBuilder address = new StringBuilder();
        boolean collecting = false;
        int matches = 0;
        for (String line : text.split("\\R")) {
            var header = headerPattern.matcher(line);
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
