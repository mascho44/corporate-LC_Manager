package de.ostms.lc.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.api.ReviewSummary;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.api.DocumentSetRequest;
import de.ostms.lc.document.api.DocumentView;
import de.ostms.lc.document.api.GeneratedDocumentItemRequest;
import de.ostms.lc.document.api.GeneratedDocumentRequest;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentSetServiceTest {
    private final UUID lcId = UUID.randomUUID();
    private LetterOfCreditRepository lcs;
    private GeneratedDocumentService generated;
    private DocumentCheckService checks;
    private DocumentSetService service;
    private LetterOfCredit lc;
    private de.ostms.lc.company.service.CompanyProfileService companies;

    @BeforeEach void setUp() throws Exception {
        lcs = mock(LetterOfCreditRepository.class);
        generated = mock(GeneratedDocumentService.class);
        checks = mock(DocumentCheckService.class);
        var documents = mock(LcDocumentRepository.class);
        when(documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId)).thenReturn(List.of());
        companies = mock(de.ostms.lc.company.service.CompanyProfileService.class);
        when(companies.hasLetterhead(any())).thenReturn(true);
        service = new DocumentSetService(lcs, documents, generated, checks, companies);
        lc = new LetterOfCredit();
        lc.setReference("TEST-LC-1"); lc.setCurrency("EUR"); lc.setAmount(new BigDecimal("125000.00")); lc.setExpiryDate(LocalDate.of(2026, 11, 30));
        lc.setRequiredDocuments(List.of("SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS", "PACKING LIST IN 2 COPIES", "CERTIFICATE OF ORIGIN ISSUED BY CHAMBER OF COMMERCE"));
        when(lcs.findById(lcId)).thenReturn(Optional.of(lc));
        when(checks.maximumCreditAmount(lc)).thenReturn(new BigDecimal("131250.00"));
        when(checks.precheck(lcId)).thenReturn(new ReviewSummary("OK", 0, 0, 0, List.of(), "AUTOMATIC"));
        when(generated.createDocx(eq(lcId), any())).thenAnswer(call -> {
            GeneratedDocumentRequest r = call.getArgument(1);
            return new DocumentView(UUID.randomUUID(), r.type(), r.type().getDisplayName(), r.type().name().toLowerCase() + "-" + r.documentNumber() + ".docx", "application/x", 1, r.documentDate(), null, null, null, null, "GENERATED", null, r.documentNumber(), null, null, null);
        });
    }

    private GeneratedDocumentItemRequest line(String quantity, String price, String amount) {
        return new GeneratedDocumentItemRequest(null, "Machine", new BigDecimal(quantity), "pcs", price == null ? null : new BigDecimal(price), amount == null ? null : new BigDecimal(amount), 1, new BigDecimal("100"), new BigDecimal("110"));
    }

    @Test void proposalNamesTheDocumentsOfTheCreditAndFallsBackToInvoiceAndPackingList() {
        var proposal = service.proposal(lcId);
        assertThat(proposal.types()).containsExactly(DocumentType.COMMERCIAL_INVOICE, DocumentType.PACKING_LIST, DocumentType.CERTIFICATE_OF_ORIGIN);
        assertThat(proposal.invoiceNumber()).matches("INV-\\d{4}-001");
        assertThat(proposal.maximumInvoiceTotal()).isEqualByComparingTo("131250.00");
        assertThat(proposal.items()).hasSize(1);
        assertThat(proposal.items().get(0).amount()).isEqualByComparingTo("125000.00");
        lc.setRequiredDocuments(List.of("SOMETHING ELSE"));
        assertThat(service.proposal(lcId).types()).containsExactly(DocumentType.COMMERCIAL_INVOICE, DocumentType.PACKING_LIST);
    }

    @Test void createsAllSelectedDocumentsWithConsistentNumbersDatesAndTotals() throws Exception {
        var request = new DocumentSetRequest(LocalDate.of(2026, 10, 5), "INV-2026-0815", List.of(line("2", "58000", null), line("2", "4500", "9000.00")), null, null, null, null, "Marks: BM-1",
            EnumSet.of(DocumentType.COMMERCIAL_INVOICE, DocumentType.PACKING_LIST, DocumentType.CERTIFICATE_OF_ORIGIN));
        var result = service.create(lcId, request);

        assertThat(result.total()).isEqualByComparingTo("125000.00");
        assertThat(result.warnings()).isEmpty();
        var captor = ArgumentCaptor.forClass(GeneratedDocumentRequest.class);
        verify(generated, times(3)).createDocx(eq(lcId), captor.capture());
        var made = captor.getAllValues();
        assertThat(made).extracting(GeneratedDocumentRequest::type).containsExactly(DocumentType.COMMERCIAL_INVOICE, DocumentType.PACKING_LIST, DocumentType.CERTIFICATE_OF_ORIGIN);
        assertThat(made).extracting(GeneratedDocumentRequest::documentNumber).containsExactly("INV-2026-0815", "PL-2026-0815", "COO-2026-0815");
        assertThat(made).allSatisfy(r -> assertThat(r.documentDate()).isEqualTo(LocalDate.of(2026, 10, 5)));
        assertThat(made.get(1).packages()).isEqualTo(2);
        assertThat(made.get(1).netWeight()).isEqualByComparingTo("200");
        assertThat(made.get(1).grossWeight()).isEqualByComparingTo("220");
        assertThat(made.get(0).items().get(0).amount()).isEqualByComparingTo("116000.00");
        assertThat(made.get(0).quantity()).isEqualTo("4 pcs");
        assertThat(made.get(0).notes()).isEqualTo("Marks: BM-1");
    }

    @Test void warnsWhenTheInvoiceExceedsTheCreditAndTheDateIsAfterExpiry() throws Exception {
        var request = new DocumentSetRequest(LocalDate.of(2026, 12, 15), "INV-1", List.of(line("1", null, "131500.00")), null, null, null, null, null, EnumSet.of(DocumentType.COMMERCIAL_INVOICE));
        var result = service.create(lcId, request);
        assertThat(result.warnings()).anyMatch(w -> w.contains("übersteigt den zulässigen Höchstbetrag") && w.contains("250,00"));
        assertThat(result.warnings()).anyMatch(w -> w.contains("nach dem Verfall"));
    }

    @Test void warnsAboutMissingPackingDataAndMissingAmounts() throws Exception {
        var bare = new GeneratedDocumentItemRequest(null, "Goods", new BigDecimal("1"), "pcs", null, null, null, null, null);
        var result = service.create(lcId, new DocumentSetRequest(LocalDate.of(2026, 10, 5), "INV-2", List.of(bare), null, null, null, null, null, EnumSet.of(DocumentType.COMMERCIAL_INVOICE, DocumentType.PACKING_LIST)));
        assertThat(result.warnings()).anyMatch(w -> w.contains("Beträge in den Positionen")).anyMatch(w -> w.contains("Packstücke")).anyMatch(w -> w.contains("Bruttogewicht"));
        assertThat(result.total()).isNull();
    }

    @Test void reportsOnlyFindingsOfTheNewDocuments() throws Exception {
        var summary = new ReviewSummary("DISCREPANCY", 1, 1, 0, List.of(
            new CheckResult(CheckResult.Severity.DISCREPANCY, "INVOICE_AMOUNT_EXCEEDED", "zu hoch", null, "commercial_invoice-INV-9.docx", null),
            new CheckResult(CheckResult.Severity.OK, "OK_CODE", "gut", null, "commercial_invoice-INV-9.docx", null),
            new CheckResult(CheckResult.Severity.WARNING, "OTHER", "fremd", null, "alt.pdf", null)), "AUTOMATIC");
        when(checks.precheck(lcId)).thenReturn(summary);
        var result = service.create(lcId, new DocumentSetRequest(LocalDate.of(2026, 10, 5), "INV-9", List.of(line("1", null, "100")), null, null, null, null, null, EnumSet.of(DocumentType.COMMERCIAL_INVOICE)));
        assertThat(result.findings()).extracting(DocumentSetService.Finding::code).containsExactly("INVOICE_AMOUNT_EXCEEDED");
    }

    @Test void needsAtLeastOneKnownType() {
        assertThatThrownBy(() -> service.create(lcId, new DocumentSetRequest(LocalDate.of(2026, 10, 5), "INV-3", List.of(line("1", null, "1")), null, null, null, null, null, EnumSet.of(DocumentType.OTHER))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void derivesTheSuffixOfNumbers() {
        assertThat(DocumentSetService.suffix("INV-2026-0815")).isEqualTo("2026-0815");
        assertThat(DocumentSetService.suffix("0815")).isEqualTo("0815");
        assertThat(DocumentSetService.suffix("AB/12")).isEqualTo("12");
        assertThat(DocumentSetService.suffix("RECHNUNG-7")).isEqualTo("RECHNUNG-7");
    }

    @Test void theProposalSaysWhetherALetterheadExists() {
        assertThat(service.proposal(lcId).companyConfigured()).isTrue();
        when(companies.hasLetterhead(any())).thenReturn(false);
        assertThat(service.proposal(lcId).companyConfigured()).isFalse();
    }
}
