package de.ostms.lc.lc.service;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
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

    private List<LcDeadlineService.LcDeadlineView> estimates(LetterOfCredit lc, LcDocument... documents) {
        UUID id = UUID.randomUUID();
        var lcs = mock(LetterOfCreditRepository.class); var docs = mock(LcDocumentRepository.class);
        when(lcs.findById(id)).thenReturn(Optional.of(lc)); when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(documents));
        return new LcDeadlineService(lcs, docs).forLc(id).stream().filter(item -> item.type().equals("MATURITY_ESTIMATE")).toList();
    }
    private static LetterOfCredit lcWith(String clause, String presentationDate) {
        LetterOfCredit lc = new LetterOfCredit(); lc.setRawMessage(":42C:" + clause);
        if (presentationDate != null) {
            var facts = new java.util.EnumMap<de.ostms.lc.rulepack.PackDefinition.Field, String>(de.ostms.lc.rulepack.PackDefinition.Field.class);
            facts.put(de.ostms.lc.rulepack.PackDefinition.Field.LC_PRESENTATION_DATE, presentationDate);
            lc.setRuleFactsJson(de.ostms.lc.rulepack.RuleFacts.encode(facts, false));
        }
        return lc;
    }
    private static LcDocument document(DocumentType type, String name, LocalDate date) { LcDocument d = new LcDocument(); d.setDocumentType(type); d.setOriginalFilename(name); d.setDocumentDate(date); return d; }

    @Test void atSightPaymentIsEstimatedFromThePresentationDateWithTheExaminationTime() {
        // Presented Fri 2026-10-16: five banking days later is Fri 2026-10-23.
        var result = estimates(lcWith("AT SIGHT", "2026-10-16"));
        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("Späteste Zahlung bei Sicht"); assertThat(item.date()).isEqualTo(LocalDate.of(2026, 10, 23));
            assertThat(item.confirmedBasis()).isFalse(); assertThat(item.note()).contains("UCP 600").contains("2026-10-16");
        });
        assertThat(estimates(lcWith("AT SIGHT", null))).isEmpty();
    }

    @Test void termsAfterSightAndPresentationRunFromThePresentationDate() {
        assertThat(estimates(lcWith("90 DAYS AFTER SIGHT", "2026-10-01"))).singleElement().satisfies(item -> assertThat(item.date()).isEqualTo(LocalDate.of(2026, 12, 30)));
        assertThat(estimates(lcWith("30 DAYS AFTER PRESENTATION OF DOCUMENTS", "2026-10-01")).get(0).date()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(estimates(lcWith("90 DAYS AFTER SIGHT", null))).isEmpty();
    }

    @Test void termsAfterTheInvoiceDateUseTheInvoiceAndBankingDaysSkipWeekends() {
        var invoice = document(DocumentType.COMMERCIAL_INVOICE, "re.pdf", LocalDate.of(2026, 10, 1));
        var result = estimates(lcWith("30 DAYS AFTER INVOICE DATE", null), invoice);
        assertThat(result).singleElement().satisfies(item -> { assertThat(item.date()).isEqualTo(LocalDate.of(2026, 10, 31)); assertThat(item.title()).contains("re.pdf"); assertThat(item.note()).contains("Rechnungsdatum"); });
        // 5 banking days after Wed 2026-10-21: Thu 22, Fri 23, Mon 26, Tue 27, Wed 28.
        var banking = estimates(lcWith("5 BANKING DAYS AFTER INVOICE DATE", null), document(DocumentType.COMMERCIAL_INVOICE, "re.pdf", LocalDate.of(2026, 10, 21)));
        assertThat(banking).singleElement().satisfies(item -> { assertThat(item.date()).isEqualTo(LocalDate.of(2026, 10, 28)); assertThat(item.note()).contains("Bankarbeitstage"); });
        // a transport document does not date an invoice term, and an invoice does not date a B/L term
        assertThat(estimates(lcWith("30 DAYS AFTER INVOICE DATE", null), document(DocumentType.BILL_OF_LADING, "bl.pdf", LocalDate.of(2026, 10, 1)))).isEmpty();
        assertThat(estimates(lcWith("60 DAYS AFTER B/L DATE", null), invoice)).isEmpty();
    }

    @Test void theBillOfLadingTermStillWorksInGermanAndEnglishWording() {
        var bill = document(DocumentType.BILL_OF_LADING, "bl.pdf", LocalDate.of(2026, 10, 1));
        assertThat(estimates(lcWith("60 TAGE NACH B/L-DATUM", null), bill)).singleElement().satisfies(item -> assertThat(item.date()).isEqualTo(LocalDate.of(2026, 11, 30)));
        assertThat(estimates(lcWith("60 DAYS AFTER SOMETHING ELSE", null), bill)).isEmpty(); // unknown anchor: nothing is guessed
        assertThat(estimates(lcWith("60 DAYS FROM DATE OF SHIPMENT", null), bill)).singleElement().satisfies(item -> assertThat(item.date()).isEqualTo(LocalDate.of(2026, 11, 30)));
    }
}
