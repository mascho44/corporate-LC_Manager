package de.corporate.lc.document.service;

import de.corporate.lc.document.domain.LcDocument;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentExtractionServiceTest {
    @Test void extractsInvoiceMetadataFromTextDocument() {
        String text = """
                COMMERCIAL INVOICE
                Invoice No: INV-2026-104
                LC Reference: LC2026004711
                Grand Total: EUR 125,000.00
                """;
        LcDocument document = new LcDocument();
        document.setOriginalFilename("invoice.txt");
        document.setContentType("text/plain");
        document.setContent(text.getBytes(StandardCharsets.UTF_8));

        new DocumentExtractionService().extract(document);

        assertThat(document.getExtractionStatus()).isEqualTo("EXTRACTED");
        assertThat(document.getExtractedDocumentNumber()).isEqualTo("INV-2026-104");
        assertThat(document.getExtractedReference()).isEqualTo("LC2026004711");
        assertThat(document.getExtractedCurrency()).isEqualTo("EUR");
        assertThat(document.getExtractedAmount()).isEqualByComparingTo(new BigDecimal("125000.00"));
    }
}
