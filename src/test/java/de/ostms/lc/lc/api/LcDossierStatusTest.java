package de.ostms.lc.lc.api;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.api.CheckResult.Severity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LcDossierStatusTest {
    @Test
    void countsOpenAndBlockingFindings() {
        var accepted = new CheckResult(Severity.DISCREPANCY, "INVOICE_AMOUNT_EXCEEDED", "m", null, "a.pdf", null, "ACCEPTED", null, "u", null);
        var status = LcDossierStatus.of(UUID.randomUUID(), "RED", 2, 2, 2, 1, List.of(
                new CheckResult(Severity.DISCREPANCY, "INVOICE_AMOUNT_EXCEEDED", "m"),
                new CheckResult(Severity.WARNING, "SIGNATURE_REQUIREMENT_REVIEW", "m"),
                new CheckResult(Severity.WARNING, "GOODS_DESCRIPTION", "m"),
                new CheckResult(Severity.WARNING, "REVIEW_STALE", "m"),
                new CheckResult(Severity.OK, "SHIPMENT_DATE_OK", "m"),
                accepted));
        assertEquals(3, status.open());
        assertEquals(2, status.blocking());
    }
}
