package de.ostms.lc.sanctions;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Consignee and notify party often read "APPLICANT" or "TO ORDER": a reference to a party that is screened under its own name
 * (or no name at all), not a name. Screening the word itself yields false hits such as "APPLICANT" ~ "LLC APPLICATA".
 */
final class PartyReferences {
    private static final Pattern REFERENCE = Pattern.compile(
        "^(?:THE\\s+)?(?:APPLICANT|ACCOUNTEE|ISSUING\\s+BANK|OPENING\\s+BANK|BENEFICIARY|SHIPPER|CONSIGNEE|NOTIFY\\s+PARTY|SAME\\s+AS\\s+.+|AS\\s+ABOVE|AS\\s+PER\\s+.+|TO\\s+(?:THE\\s+)?ORDER(?:\\s+.*)?|ORDER(?:\\s+OF\\s+.*)?|NOT\\s+APPLICABLE|N/?A|NONE|-+)$");

    private PartyReferences() {}

    /** True when the value only points to another party (or to nobody) and must not be screened as a name. */
    static boolean isReference(String value) {
        if (value == null) return false;
        String text = value.toUpperCase(Locale.ROOT).replaceAll("[\\s,.;:]+$", "").replaceAll("\\s+", " ").trim();
        return text.isEmpty() || REFERENCE.matcher(text).matches();
    }
}
