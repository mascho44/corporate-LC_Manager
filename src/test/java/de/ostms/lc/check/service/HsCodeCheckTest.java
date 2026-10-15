package de.ostms.lc.check.service;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.document.domain.LcDocument;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class HsCodeCheckTest {
    private static LcDocument doc(String name,String text){var d=new LcDocument();d.setOriginalFilename(name);d.setExtractedText(text);return d;}

    @Test void recognisesOnlyLabelledCodesInCommonSpellings() {
        assertThat(HsCodeCheck.codes("H.S. CODE: 8471.30.00 and invoice no 12345678")).containsExactly("84713000");
        assertThat(HsCodeCheck.codes("HS-Code 847130 / 8473 30")).containsExactly("847130","847330");
        assertThat(HsCodeCheck.codes("Zolltarifnummer: 9403 60 10")).containsExactly("94036010");
        assertThat(HsCodeCheck.codes("Commodity code 8542.31; TARIC 8542310010")).containsExactly("854231","8542310010");
        assertThat(HsCodeCheck.codes("Invoice No. 2026/0815, total 12.500,00")).isEmpty();
    }

    @Test void comparesWithTheLcAndBetweenDocumentsAndFlagsSensitiveGoods() {
        var ok=HsCodeCheck.evaluate(":45A:500 UNITS OFFICE CHAIRS HS CODE 9401.30",List.of(doc("invoice.pdf","HS Code: 9401.30.10")));
        assertThat(ok).extracting(CheckResult::code).containsExactly("HS_CODE_OK");
        var wrong=HsCodeCheck.evaluate(":45A:OFFICE CHAIRS HS CODE 9401.30",List.of(doc("invoice.pdf","HS Code: 9403.60")));
        assertThat(wrong).extracting(CheckResult::code).containsExactly("HS_CODE_MISMATCH");
        var docs=HsCodeCheck.evaluate("",List.of(doc("invoice.pdf","HS code 9401.30"),doc("packing.pdf","HS code 7308.90")));
        assertThat(docs).extracting(CheckResult::code).contains("HS_CODE_INCONSISTENT");
        var sensitive=HsCodeCheck.evaluate("",List.of(doc("invoice.pdf","HS CODE: 8542.31.00")));
        assertThat(sensitive).extracting(CheckResult::code).contains("HS_CODE_EXPORT_CONTROL");
        assertThat(HsCodeCheck.evaluate("NO CODE",List.of(doc("a.pdf","nothing")))).isEmpty();
    }
}
