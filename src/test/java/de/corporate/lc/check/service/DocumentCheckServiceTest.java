package de.corporate.lc.check.service;

import de.corporate.lc.document.domain.*;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DocumentCheckServiceTest {
    @Test void detectsMissingDocumentAndExcessInvoiceAmount() {
        UUID id = UUID.randomUUID();
        LetterOfCredit lc = new LetterOfCredit();
        lc.setAmount(new BigDecimal("1000.00")); lc.setCurrency("EUR");
        lc.setExpiryDate(LocalDate.now().plusDays(30));
        lc.setRequiredDocuments(List.of("SIGNED COMMERCIAL INVOICE", "PACKING LIST"));
        LcDocument invoice = new LcDocument(); invoice.setDocumentType(DocumentType.COMMERCIAL_INVOICE);
        invoice.setOriginalFilename("invoice.pdf"); invoice.setAmount(new BigDecimal("1200.00"));
        invoice.setCurrency("EUR"); invoice.setDocumentDate(LocalDate.now());
        var lcs = mock(LetterOfCreditRepository.class); var docs = mock(LcDocumentRepository.class);
        when(lcs.findById(id)).thenReturn(Optional.of(lc));
        when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice));
        var result = new DocumentCheckService(lcs, docs).check(id);
        assertThat(result.discrepancies()).isEqualTo(2);
        assertThat(result.results()).extracting("code").contains("MISSING_DOCUMENT", "INVOICE_AMOUNT_EXCEEDED");
    }
}
