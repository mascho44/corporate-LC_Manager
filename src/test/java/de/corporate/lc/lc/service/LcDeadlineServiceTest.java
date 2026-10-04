package de.corporate.lc.lc.service;

import de.corporate.lc.document.domain.DocumentType;
import de.corporate.lc.document.domain.LcDocument;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LcDeadlineServiceTest {
    @Test void derivesPresentationAndExplicitShipmentTenorFromDatedBillOfLading() {
        UUID id = UUID.randomUUID(); LetterOfCredit lc = new LetterOfCredit();
        lc.setRawMessage(":48:21 DAYS AFTER SHIPMENT\n:42C:60 DAYS AFTER B/L DATE");
        LcDocument bill = new LcDocument(); bill.setDocumentType(DocumentType.BILL_OF_LADING); bill.setOriginalFilename("bl.pdf"); bill.setDocumentDate(LocalDate.of(2026, 10, 1));
        var lcs = mock(LetterOfCreditRepository.class); var docs = mock(LcDocumentRepository.class);
        when(lcs.findById(id)).thenReturn(Optional.of(lc)); when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(bill));
        var result = new LcDeadlineService(lcs, docs).forLc(id);
        assertThat(result).filteredOn(item -> item.type().equals("PRESENTATION")).singleElement().satisfies(item -> assertThat(item.date()).isEqualTo(LocalDate.of(2026, 10, 22)));
        assertThat(result).filteredOn(item -> item.type().equals("MATURITY_ESTIMATE")).singleElement().satisfies(item -> {
            assertThat(item.date()).isEqualTo(LocalDate.of(2026, 11, 30)); assertThat(item.confirmedBasis()).isFalse(); assertThat(item.note()).contains("fachlich prüfen");
        });
    }

    @Test void doesNotInventDatesFromUnrecognizedMaturityTerms() {
        UUID id = UUID.randomUUID(); LetterOfCredit lc = new LetterOfCredit(); lc.setRawMessage(":42C:AT SIGHT");
        var lcs = mock(LetterOfCreditRepository.class); var docs = mock(LcDocumentRepository.class);
        when(lcs.findById(id)).thenReturn(Optional.of(lc)); when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of());
        assertThat(new LcDeadlineService(lcs, docs).forLc(id)).isEmpty();
    }
}
