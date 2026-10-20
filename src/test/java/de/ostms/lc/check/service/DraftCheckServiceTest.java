package de.ostms.lc.check.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.api.CheckResult.Severity;
import de.ostms.lc.check.api.ReviewSummary;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.document.service.DocumentExtractionService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DraftCheckServiceTest {
    private final LetterOfCreditRepository lcs = mock(LetterOfCreditRepository.class);
    private final LcDocumentRepository documents = mock(LcDocumentRepository.class);
    private final DocumentExtractionService extraction = mock(DocumentExtractionService.class);
    private final DocumentCheckService checks = mock(DocumentCheckService.class);
    private final DraftCheckService service = new DraftCheckService(lcs, documents, extraction, checks);
    private final UUID lcId = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    @Test void theDraftReplacesTheSameTypeAndOnlyItsFindingsComeBack() {
        var lc = new LetterOfCredit(); lc.setReference("LC-1");
        when(lcs.findById(lcId)).thenReturn(Optional.of(lc));
        var oldBl = new LcDocument(); oldBl.setDocumentType(DocumentType.BILL_OF_LADING); oldBl.setOriginalFilename("bl-alt.pdf");
        var invoice = new LcDocument(); invoice.setDocumentType(DocumentType.COMMERCIAL_INVOICE); invoice.setOriginalFilename("inv.pdf");
        when(documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId)).thenReturn(List.of(oldBl, invoice));
        doAnswer(call -> { LcDocument d = call.getArgument(0); d.setExtractedText("BILL OF LADING LC-1"); d.setExtractedReference("LC-1"); return null; }).when(extraction).extract(any());
        String name = "Entwurf: bl.pdf";
        when(checks.simulate(eq(lcId), eq(lc), any())).thenReturn(new ReviewSummary("RED", 1, 1, 0, List.of(
            new CheckResult(Severity.DISCREPANCY, "BILL_OF_LADING_NOTIFY_APPLICANT_MISSING", "Notify fehlt", "NOTIFY APPLICANT", name, "x"),
            new CheckResult(Severity.WARNING, "INCOTERM_NOT_FOUND", "Incoterm fehlt", null, "inv.pdf", null),
            new CheckResult(Severity.OK, "BILL_OF_LADING_REFERENCE_OK", "ok", null, name, null)), "AUTOMATIC"));

        var result = service.check(lcId, new byte[]{1, 2}, "bl.pdf", "application/pdf", DocumentType.BILL_OF_LADING);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(checks).simulate(eq(lcId), eq(lc), captor.capture());
        List<LcDocument> checked = captor.getValue();
        assertThat(checked).extracting(LcDocument::getOriginalFilename).containsExactly("inv.pdf", name);
        assertThat(result.textFound()).isTrue();
        assertThat(result.discrepancies()).isEqualTo(1);
        assertThat(result.findings()).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo("BILL_OF_LADING_NOTIFY_APPLICANT_MISSING");
            assertThat(f.advice()).isNotBlank();
        });
        verify(documents, never()).save(any());
    }

    @Test void emptyOrUntypedDraftsAreRefused() {
        assertThatThrownBy(() -> service.check(lcId, new byte[0], "x.pdf", null, DocumentType.BILL_OF_LADING)).hasMessageContaining("Datei");
        assertThatThrownBy(() -> service.check(lcId, new byte[]{1}, "x.pdf", null, null)).hasMessageContaining("Dokumentart");
    }
}
