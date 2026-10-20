package de.ostms.lc.check.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.api.CheckResult.Severity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FindingTextTest {
    @Test void specificSentencesComeFirstAndAreasFollowTheSeverity() {
        assertThat(FindingText.english("MISSING_DOCUMENT", Severity.DISCREPANCY)).isEqualTo("A document required by the credit has not been presented.");
        assertThat(FindingText.english("BILL_OF_LADING_NOTIFY_APPLICANT_MISSING", Severity.WARNING)).isEqualTo("Bill of lading: please check against the credit.");
        assertThat(FindingText.english("INSURANCE_COVERAGE_INSUFFICIENT", Severity.DISCREPANCY)).isEqualTo("Insurance document: does not comply with the credit.");
        assertThat(FindingText.english("PACKING_LIST_REFERENCE_OK", Severity.OK)).isEqualTo("Packing list: in order.");
        assertThat(FindingText.english("SOMETHING_NEW", Severity.WARNING)).isNull();
    }

    @Test void theAdviceExistsInBothLanguagesForTheSameCodes() {
        for (String code : new String[]{"MISSING_DOCUMENT", "INVOICE_AMOUNT_EXCEEDED", "BILL_OF_LADING_REFERENCE_MISMATCH", "CLEAN_ON_BOARD", "INSURANCE_DATE_REVIEW", "NO_TEXT"}) {
            assertThat(CorrectionAdvice.forFinding(code, Severity.DISCREPANCY)).as(code).isNotBlank();
            assertThat(CorrectionAdvice.forFindingEnglish(code, Severity.DISCREPANCY)).as(code).isNotBlank().doesNotContain("ä", "ö", "ü", "ß");
        }
        assertThat(CorrectionAdvice.forFindingEnglish("INVOICE_AMOUNT_EXCEEDED", Severity.DISCREPANCY)).endsWith("ask the buyer for an amendment of the credit.");
        var result = new CheckResult(Severity.DISCREPANCY, "CURRENCY_MISMATCH", "Währung weicht ab");
        assertThat(result.messageEn()).contains("currency"); assertThat(result.adviceEn()).contains("currency");
    }

    @Test void mostFindingCodesOfTheCheckHaveAnEnglishText() throws Exception {
        String source = Files.readString(Path.of("src/main/java/de/ostms/lc/check/service/DocumentCheckService.java"));
        var codes = new TreeSet<String>();
        var m = Pattern.compile("finding\\((?:OK|WARNING|DISCREPANCY|[a-z]+\\?(?:OK|WARNING|DISCREPANCY):(?:OK|WARNING|DISCREPANCY)),\\s*\"([A-Z][A-Z0-9_]+)\"(?!\\s*\\+)").matcher(source);
        while (m.find()) codes.add(m.group(1));
        var missing = codes.stream().filter(code -> FindingText.english(code, Severity.WARNING) == null).toList();
        assertThat(codes).hasSizeGreaterThan(20);
        assertThat(missing).as("ohne englischen Text").isEmpty();
    }
}
