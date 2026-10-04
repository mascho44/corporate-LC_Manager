package de.corporate.lc.lc.service;

import de.corporate.lc.lc.domain.*;
import de.corporate.lc.lc.repository.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class LcCalendarService {
    private final LetterOfCreditRepository lettersOfCredit;
    private final LcTaskRepository tasks;
    private final LcDeadlineService deadlines;

    public LcCalendarService(LetterOfCreditRepository lettersOfCredit, LcTaskRepository tasks, LcDeadlineService deadlines) {
        this.lettersOfCredit = lettersOfCredit; this.tasks = tasks; this.deadlines = deadlines;
    }

    public byte[] export(String scope, String username) {
        StringBuilder out = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//OSTMS//Corporate LC Manager//DE\r\nCALSCALE:GREGORIAN\r\nMETHOD:PUBLISH\r\nX-WR-CALNAME:Corporate LC Manager\r\n");
        lettersOfCredit.findAll().stream().filter(lc -> active(lc) && visible(scope, username, lc.getAssignedTo())).forEach(lc -> {
            event(out, lc.getId()+"-expiry", lc.getExpiryDate(), "LC "+lc.getReference()+" – Ablauf", party(lc));
            event(out, lc.getId()+"-shipment", lc.getLatestShipmentDate(), "LC "+lc.getReference()+" – spätester Versand", party(lc));
            event(out, lc.getId()+"-follow-up", lc.getFollowUpDate(), "LC "+lc.getReference()+" – Wiedervorlage", party(lc));
            deadlines.forLc(lc.getId()).stream().filter(item -> item.type().equals("PRESENTATION") || item.type().equals("MATURITY_ESTIMATE"))
                    .forEach(item -> event(out, lc.getId()+"-"+item.type()+"-"+item.title().hashCode(), item.date(), "LC "+lc.getReference()+" – "+item.title(), party(lc)+" | "+item.note()));
        });
        tasks.findByCompletedFalseOrderByDueDateAscCreatedAtAsc().stream().filter(task -> visible(scope, username, task.getAssignedTo())).forEach(task ->
                event(out, task.getId()+"-task", task.getDueDate(), "LC-Aufgabe – "+task.getTitle(), "Zugewiesen an: "+Objects.toString(task.getAssignedTo(), "nicht zugewiesen")));
        out.append("END:VCALENDAR\r\n");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private boolean active(LetterOfCredit lc) { return lc.getStatus()!=LetterOfCreditStatus.CLOSED && lc.getStatus()!=LetterOfCreditStatus.EXPIRED; }
    private boolean visible(String scope, String username, String assignedTo) { return "mine".equals(scope) ? assignedTo!=null && assignedTo.equalsIgnoreCase(username) : !"unassigned".equals(scope) || assignedTo==null; }
    private String party(LetterOfCredit lc) { return "Applicant: "+Objects.toString(lc.getApplicant(), "–")+" | Beneficiary: "+Objects.toString(lc.getBeneficiary(), "–"); }
    private void event(StringBuilder out, Object uid, LocalDate date, String summary, String description) {
        if (date==null) return;
        String stamp=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()), day=date.format(DateTimeFormatter.BASIC_ISO_DATE), end=date.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        out.append("BEGIN:VEVENT\r\nUID:").append(uid).append("@lc.example.com\r\nDTSTAMP:").append(stamp).append("\r\nDTSTART;VALUE=DATE:").append(day).append("\r\nDTEND;VALUE=DATE:").append(end).append("\r\nSUMMARY:").append(escape(summary)).append("\r\nDESCRIPTION:").append(escape(description)).append("\r\nEND:VEVENT\r\n");
    }
    private String escape(String value) { return value.replace("\\","\\\\").replace(";","\\;").replace(",","\\,").replace("\r","").replace("\n","\\n"); }
}
