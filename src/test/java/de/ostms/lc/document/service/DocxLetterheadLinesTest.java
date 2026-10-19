package de.ostms.lc.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.ostms.lc.company.domain.CompanyProfile;
import de.ostms.lc.company.service.CompanyProfileService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocxLetterheadLinesTest {
    @Test void joinsOnlyWhatExists() {
        assertThat(DocxTemplateService.joined(", ", "Street 1", "70565 Stuttgart", null, " ")).isEqualTo("Street 1, 70565 Stuttgart");
        assertThat(DocxTemplateService.joined(", ", null, "", " ")).isEmpty();
        assertThat(DocxTemplateService.oneLine("A GmbH\nStreet 1\n\n70565 Stuttgart")).isEqualTo("A GmbH, Street 1, 70565 Stuttgart");
        assertThat(DocxTemplateService.oneLine(null)).isEmpty();
    }

    @Test void aLetterWithoutCompanyDetailsHasNoDashesOrEmptySeparators() throws Exception {
        var companies = mock(CompanyProfileService.class);
        var company = new CompanyProfile();
        company.setLegalName("Seller GmbH");
        when(companies.profileForLetterhead(any(), any())).thenReturn(company);
        var lc = new LetterOfCredit();
        lc.setReference("LC-1"); lc.setApplicant("Buyer LLC\nP.O. BOX 1\nDUBAI"); lc.setCurrency("EUR"); lc.setAmount(new java.math.BigDecimal("1000")); lc.setCompanyId(1);
        byte[] bytes = new DocxTemplateService(mock(DocumentTemplateService.class), companies).renderSubmissionLetter(lc,
            new DocxTemplateService.SubmissionLetter(LocalDate.of(2026, 10, 19), "Bank AG", null, null, List.of(new DocxTemplateService.SubmissionRow("1", "Commercial Invoice", "INV-1", "05 Oct 2026", "3", "0", "–"))));
        try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String body = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
            String footer = doc.getFooterList().get(0).getText();
            assertThat(body).contains("for the account of Buyer LLC, P.O. BOX 1, DUBAI for EUR 1,000.00", "credit the proceeds on the due date.");
            assertThat(body).doesNotContain("issued by").doesNotContain("with -").doesNotContain("IBAN -").doesNotContain("Tel. -");
            assertThat(footer).doesNotContain("IBAN").doesNotContain("VAT ID").doesNotContain(", -");
        }
    }
}
