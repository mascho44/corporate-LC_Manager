package de.corporate.lc.check.service;

import de.corporate.lc.check.api.CheckResult;
import java.util.*;
import static de.corporate.lc.check.api.CheckResult.Severity.WARNING;

/** Deterministic format-neutral contract, suitable for subsequent service extraction. */
public final class RulePackEngine {
    public record Fact(String documentName, String type, String currency, String evidence) {}
    public record Input(String applicableRules, String lcCurrency, List<Fact> documents) {
        public Input { documents = List.copyOf(documents); }
    }
    private RulePackEngine() {}
    public static List<CheckResult> evaluate(Input input) {
        List<CheckResult> results = new ArrayList<>();
        // Exact supported profile only: OTHER / EUCP / unknown exceptions must not be guessed.
        boolean applicable = Set.of("UCP LATEST VERSION", "UCP 600", "UCP600")
            .contains(Objects.toString(input.applicableRules(), "").trim().toUpperCase(Locale.ROOT));
        for (Fact fact : input.documents()) {
            if (!"commercial-invoice".equals(fact.type())) continue;
            if (!applicable) {
                results.add(new CheckResult(WARNING, "RULE_PACK_NOT_APPLICABLE",
                    "UCP-Einbeziehung ist unbekannt oder nicht im unterstützten Profil. Fachlich prüfen.",
                    "Anwendbare Regeln: " + Objects.toString(input.applicableRules(), "nicht erfasst"),
                    fact.documentName(), "Keine automatische UCP-Prüfung ausgeführt."));
                continue;
            }
            boolean missing = input.lcCurrency() == null || input.lcCurrency().isBlank()
                || fact.currency() == null || fact.currency().isBlank();
            boolean mismatch = !missing && !input.lcCurrency().equalsIgnoreCase(fact.currency());
            results.add(new CheckResult(WARNING, "UCP18_INVOICE_CURRENCY",
                missing ? "Rechnungswährung nicht prüfbar: Währungsangaben fehlen."
                    : mismatch ? "Mögliche Abweichung der Rechnungswährung; UCP-Teilprüfung fachlich bestätigen."
                    : "Rechnungswährung stimmt laut Metadaten überein; UCP-Teilprüfung fachlich bestätigen.",
                "LC-Währung: " + Objects.toString(input.lcCurrency(), "nicht erfasst"),
                fact.documentName(), "Erfasste Rechnungswährung: "
                    + Objects.toString(fact.currency(), "nicht erfasst") + " · "
                    + Objects.toString(fact.evidence(), "Keine Originalfundstelle")));
        }
        return List.copyOf(results);
    }
}
