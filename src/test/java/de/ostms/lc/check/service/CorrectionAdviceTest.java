package de.ostms.lc.check.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.api.CheckResult.Severity;
import org.junit.jupiter.api.Test;

class CorrectionAdviceTest {
    @Test void openFindingsGetAStepAndFineOnesDoNot() {
        assertThat(CorrectionAdvice.forFinding("MISSING_DOCUMENT", Severity.DISCREPANCY)).contains("hochladen");
        assertThat(CorrectionAdvice.forFinding("INVOICE_AMOUNT_EXCEEDED", Severity.DISCREPANCY)).contains("Rechnung neu ausstellen").contains("Änderung");
        assertThat(CorrectionAdvice.forFinding("BILL_OF_LADING_REFERENCE_MISMATCH", Severity.DISCREPANCY)).contains("Akkreditivnummer");
        assertThat(CorrectionAdvice.forFinding("INVOICE_AMOUNT_OK", Severity.OK)).isNull();
        assertThat(CorrectionAdvice.forFinding("DOCUMENT_PRESENT", Severity.OK)).isNull();
        assertThat(CorrectionAdvice.forFinding("SOMETHING_NEW", Severity.DISCREPANCY)).isNull();
        assertThat(CorrectionAdvice.forFinding(null, Severity.DISCREPANCY)).isNull();
    }

    @Test void theFindingCarriesItsAdvice() {
        assertThat(new CheckResult(Severity.DISCREPANCY, "CURRENCY_MISMATCH", "x").advice()).contains("Währung");
        assertThat(new CheckResult(Severity.OK, "CURRENCY_MISMATCH", "x").advice()).isNull();
    }
}
