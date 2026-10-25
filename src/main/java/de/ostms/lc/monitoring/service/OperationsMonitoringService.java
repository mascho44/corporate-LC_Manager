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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private de.ostms.lc.ebics.EbicsConnectionRepository ebics;

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
        long waitingForCustomer = active.stream().filter(lc -> lc.getStatus() == LetterOfCreditStatus.WAITING_FOR_CUSTOMER).count();
        long discrepancies = active.stream().mapToLong(lc -> checks.check(lc.getId()).discrepancies()).sum();
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(30);
        long upcomingDeadlines = active.stream().flatMap(lc -> deadlines.forLc(lc.getId()).stream())
                .filter(deadline -> !deadline.date().isBefore(today) && !deadline.date().isAfter(horizon)).count();
        long approvalQueue = drafts.findAll().stream()
                .filter(draft -> draft.getStatus() == DocumentDraftStatus.SUBMITTED || draft.getStatus() == DocumentDraftStatus.REVIEWED).count();
        return new OperationsMonitoringSummary(LocalDateTime.now(),
                metric(openExaminations, "Akkreditive mit präsentierten Dokumenten."),
                metric(waitingForCustomer, "Akkreditive im Status „Wartet auf Kunde“ (ausstehende Rückmeldung)."),
                metric(discrepancies, "Offene automatische Dokumentenabweichungen über aktive Akten."),
                metric(upcomingDeadlines, "Fällige Termine innerhalb der nächsten 30 Tage."),
                metric(approvalQueue, "Dokumententwürfe zur Prüfung oder Freigabe."),
                ebicsMetric(),
                metric(imports.countByStatus("REJECTED"), "Abgewiesene SWIFT-Importe in der Importhistorie."),
                metric(outbox.countByStatus("DEAD_LETTER"), "Dauerhaft fehlgeschlagene Outbox-Nachrichten."));
    }

    /** Problems of the tenant's EBICS connection: a failed key setup and a failed last (automatic or manual) fetch each count once. */
    OperationsMonitoringSummary.Metric ebicsMetric() {
        var connection = ebics == null ? java.util.Optional.<de.ostms.lc.ebics.EbicsConnection>empty() : ebics.findCurrent();
        if (connection.isEmpty()) return unavailable("Keine EBICS-Verbindung eingerichtet.");
        var c = connection.get();
        long problems = 0;
        var notes = new java.util.ArrayList<String>();
        if (c.getStatus() == de.ostms.lc.ebics.EbicsStatus.ERROR) { problems++; notes.add("Schlüsseleinrichtung fehlgeschlagen"); }
        if (c.getLastFetchResult() != null && c.getLastFetchResult().contains("Fehler")) { problems++; notes.add("letzter Abruf mit Fehler"); }
        String status = switch (c.getStatus()) { case ACTIVE -> "aktiv"; case KEYS_SENT -> "wartet auf Freigabe der Bank"; case NEW -> "Einrichtung offen"; case ERROR -> "Fehler"; };
        String last = c.getLastFetchAt() == null ? "noch kein Abruf" : "letzter Abruf " + c.getLastFetchAt().toString().replace('T', ' ').substring(0, 16);
        return metric(problems, "EBICS-Verbindung " + status + ", " + last + (notes.isEmpty() ? "." : ": " + String.join("; ", notes) + "."));
    }

    private boolean active(LetterOfCredit lc) { return lc.getStatus() != LetterOfCreditStatus.CLOSED && lc.getStatus() != LetterOfCreditStatus.EXPIRED; }
    private OperationsMonitoringSummary.Metric metric(long value, String description) { return new OperationsMonitoringSummary.Metric(value, true, description); }
    private OperationsMonitoringSummary.Metric unavailable(String description) { return new OperationsMonitoringSummary.Metric(null, false, description); }
}
