package de.corporate.lc.document.service;

import de.corporate.lc.document.api.GeneratedDocumentRequest;
import de.corporate.lc.document.domain.DocumentType;
import de.corporate.lc.lc.domain.LetterOfCredit;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DocxTemplateServiceTest {
    @Test
    void rendersEditableInvoiceFromLcData() throws Exception {
        LetterOfCredit lc = new LetterOfCredit();
        lc.setReference("LC-4711");
        lc.setApplicant("Buyer GmbH");
        lc.setBeneficiary("Seller GmbH");
        lc.setCurrency("EUR");
        lc.setAmount(new BigDecimal("1250.00"));
        GeneratedDocumentRequest request = new GeneratedDocumentRequest(DocumentType.COMMERCIAL_INVOICE,
                "INV-4711", LocalDate.of(2026, 9, 28), "Goods", "10", null, null, null, "Test note");

        DocumentTemplateService templates=mock(DocumentTemplateService.class);
        org.mockito.Mockito.when(templates.content(DocumentType.COMMERCIAL_INVOICE)).thenReturn(java.util.Optional.empty());
        byte[] result = new DocxTemplateService(templates).render(lc, request);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result))) {
            String text = document.getParagraphs().stream().map(paragraph -> paragraph.getText()).reduce("", (a, b) -> a + "\n" + b);
            assertThat(text).contains("COMMERCIAL INVOICE", "LC-4711", "Seller GmbH", "EUR 1250.00");
        }
    }
}
