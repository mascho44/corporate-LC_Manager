package de.corporate.lc.check.service;

import de.corporate.lc.check.api.RuleDefinition;
import java.util.List;

/** Public metadata only: no licensed source text is bundled with a pack. */
public final class RulePacks {
    public static final String VERSION = "2026-10-05.2";
    public record Pack(String id, String version, String status, String sourceEdition,
                       List<RuleDefinition> rules) {}
    public static final RuleDefinition INVOICE_CURRENCY = new RuleDefinition(
        "UCP18_INVOICE_CURRENCY", "1.0", "Rechnungswährung",
        "UCP 600, Art. 1 und 18(a)(iii)",
        "Vergleicht eine erfasste Rechnungswährung mit der Währung der gültigen LC-Fassung.",
        "Teilprüfung mit Metadaten; setzt UCP-Einbeziehung voraus. LC-Ausnahmen, OCR und Währungszuordnung fachlich bestätigen. Keine vollständige Konformitätsentscheidung.",
        "https://library.iccwbo.org/tfb/tfb-iccrules.htm");
    private RulePacks() {}
    public static List<Pack> all() {
        return List.of(new Pack("commercial-invoice", "1.0", "PARTIAL_PRECHECK",
                "UCP 600 / ISBP 821 (2023)", List.of(INVOICE_CURRENCY)),
            planned("bill-of-lading"), planned("air-waybill"), planned("insurance-document"),
            planned("packing-list"), planned("certificate-of-origin"));
    }
    private static Pack planned(String id) {
        return new Pack(id, "0.0", "PLANNED", "UCP 600 / ISBP 821 (2023)", List.of());
    }
}
