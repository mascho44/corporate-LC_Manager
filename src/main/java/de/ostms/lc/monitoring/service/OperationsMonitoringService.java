package de.ostms.lc.monitoring.service;

import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.domain.DocumentDraftStatus;
import de.ostms.lc.document.repository.DocumentDraftRepository;
import de.ostms.lc.imports.repository.SwiftImportRecordRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.LcDeadlineService;
import de.ostms.lc.messaging.repository.OutboxMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OperationsMonitoringService {
    private final LetterOfCreditRepository lettersOfCredit;
    private final DocumentCheckService checks;
    private final LcDeadlineService deadlines;
    private final DocumentDraftRepository drafts;
    private final SwiftImportRecordRepository imports;
    private final OutboxMessageRepository outbox;

    public OperationsMonitoringService(LetterOfCreditRepository lettersOfCredit, DocumentCheckService checks,
                                       LcDeadlineService deadlines, DocumentDraftRepository drafts,
                                       SwiftImportRecordRepository imports, OutboxMessageRepository outbox) {
        this.lettersOfCredit = lettersOfCredit;
        this.checks = checks;
        this.deadlines = deadlines;
        this.drafts = drafts;
        this.imports = imports;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public OperationsMonitoringSummary snapshot() {
        List<LetterOfCredit> active = lettersOfCredit.findAll().stream().filter(this::active).toList();
        long openExaminations = active.stream().filter(lc -> lc.getStatus() == LetterOfCreditStatus.DOCUMENTS_PRESENTED).count();
        long discrepancies = active.stream().mapToLong(lc -> checks.check(lc.getId()).discrepancies()).sum();
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(30);
        long upcomingDeadlines = active.stream().flatMap(lc -> deadlines.forLc(lc.getId()).stream())
                .filter(deadline -> !deadline.date().isBefore(today) && !deadline.date().isAfter(horizon)).count();
        long approvalQueue = drafts.findAll().stream()
                .filter(draft -> draft.getStatus() == DocumentDraftStatus.SUBMITTED || draft.getStatus() == DocumentDraftStatus.REVIEWED).count();
        return new OperationsMonitoringSummary(LocalDateTime.now(),
                metric(openExaminations, "Akkreditive mit präsentierten Dokumenten."),
                unavailable("Im LC-Statusmodell gibt es noch keinen Status für ausstehende Kundenrückmeldungen."),
                metric(discrepancies, "Offene automatische Dokumentenabweichungen über aktive Akten."),
                metric(upcomingDeadlines, "Fällige Termine innerhalb der nächsten 30 Tage."),
                metric(approvalQueue, "Dokumententwürfe zur Prüfung oder Freigabe."),
                unavailable("EBICS-Anbindung ist noch nicht implementiert."),
                metric(imports.countByStatus("REJECTED"), "Abgewiesene SWIFT-Importe in der Importhistorie."),
                metric(outbox.countByStatus("DEAD_LETTER"), "Dauerhaft fehlgeschlagene Outbox-Nachrichten."));
    }

    private boolean active(LetterOfCredit lc) { return lc.getStatus() != LetterOfCreditStatus.CLOSED && lc.getStatus() != LetterOfCreditStatus.EXPIRED; }
    private OperationsMonitoringSummary.Metric metric(long value, String description) { return new OperationsMonitoringSummary.Metric(value, true, description); }
    private OperationsMonitoringSummary.Metric unavailable(String description) { return new OperationsMonitoringSummary.Metric(null, false, description); }
}
