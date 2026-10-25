package de.ostms.lc.rulepack;

/** Welche Regelquelle bei der Dokumentenprüfung gilt. */
public enum RuleSourceMode {
    /** Nur die im LC Manager eingebauten Prüfungen. */
    EMBEDDED,
    /** Nur die aktiven Rule Packs; eingebaute Vergleichsprüfungen, die ein Pack abdeckt, entfallen. */
    IMPORTED,
    /** Eingebaute Prüfungen und Rule Packs nebeneinander (bisheriges Verhalten, Standard). */
    BOTH;

    public static RuleSourceMode parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Unbekannte Regelquelle: " + value);
        }
    }
}
