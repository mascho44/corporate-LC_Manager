package de.ostms.lc.monitoring.service;

import de.ostms.lc.check.api.ReviewSummary;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.domain.DocumentDraft;
import de.ostms.lc.document.domain.DocumentDraftStatus;
import de.ostms.lc.document.repository.DocumentDraftRepository;
import de.ostms.lc.imports.repository.SwiftImportRecordRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.LcDeadlineService;
import de.ostms.lc.messaging.repository.OutboxMessageRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OperationsMonitoringServiceTest {
    @Test void summarizesOperationalSignalsAndExplicitlyMarksUnimplementedSources() {
        LetterOfCredit underExamination = new LetterOfCredit(); underExamination.setStatus(LetterOfCreditStatus.DOCUMENTS_PRESENTED);
        LetterOfCredit active = new LetterOfCredit(); active.setStatus(LetterOfCreditStatus.ACTIVE);
        LetterOfCredit waiting = new LetterOfCredit(); waiting.setStatus(LetterOfCreditStatus.WAITING_FOR_CUSTOMER);
        LetterOfCredit closed = new LetterOfCredit(); closed.setStatus(LetterOfCreditStatus.CLOSED);
        var lcs = mock(LetterOfCreditRepository.class); var checks = mock(DocumentCheckService.class);
        var deadlines = mock(LcDeadlineService.class); var drafts = mock(DocumentDraftRepository.class);
        var imports = mock(SwiftImportRecordRepository.class); var outbox = mock(OutboxMessageRepository.class);
        when(lcs.findAll()).thenReturn(List.of(underExamination, active, waiting, closed));
        when(checks.check(any())).thenReturn(new ReviewSummary("RED", 2, 1, 0, List.of()));
        var soon = new LcDeadlineService.LcDeadlineView("EXPIRY", "Expiry", LocalDate.now().plusDays(2), ":31D:", "", true);
        when(deadlines.forLc(any())).thenReturn(List.of(soon));
        var submitted = new DocumentDraft(); submitted.setStatus(DocumentDraftStatus.SUBMITTED);
        var reviewed = new DocumentDraft(); reviewed.setStatus(DocumentDraftStatus.REVIEWED);
        var draft = new DocumentDraft(); draft.setStatus(DocumentDraftStatus.DRAFT);
        when(drafts.findAll()).thenReturn(List.of(submitted, reviewed, draft));
        when(imports.countByStatus("REJECTED")).thenReturn(4L);
        when(outbox.countByStatus("DEAD_LETTER")).thenReturn(5L);

        var summary = new OperationsMonitoringService(lcs, checks, deadlines, drafts, imports, outbox).snapshot();
        assertThat(summary.openExaminations().value()).isEqualTo(1);
        assertThat(summary.discrepancies().value()).isEqualTo(6);
        assertThat(summary.upcomingDeadlines().value()).isEqualTo(3);
        assertThat(summary.approvalQueue().value()).isEqualTo(2);
        assertThat(summary.swiftErrors().value()).isEqualTo(4);
        assertThat(summary.failedBackgroundJobs().value()).isEqualTo(5);
        assertThat(summary.waitingForCustomer().available()).isTrue();assertThat(summary.waitingForCustomer().value()).isEqualTo(1);
        assertThat(summary.ebicsErrors().available()).isFalse();
        verify(checks, times(2)).check(any());
    }
}
