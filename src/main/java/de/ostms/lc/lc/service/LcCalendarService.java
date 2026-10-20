package de.ostms.lc.lc.service;

import de.ostms.lc.lc.domain.*;
import de.ostms.lc.lc.repository.*;
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

    private ProcurementPlanService procurement;
    private de.ostms.lc.tenant.repository.TenantRepository tenants;
    /** Optional: corporate tenants also see the target dates of missing documents. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setProcurement(ProcurementPlanService procurement, de.ostms.lc.tenant.repository.TenantRepository tenants) { this.procurement = procurement; this.tenants = tenants; }

    public record CalendarEvent(String uid, UUID lcId, String reference, String kind, String title, LocalDate date, String assignedTo, String description) {}

    /** All dated items (LC deadlines and open tasks) the user may see, in the order they were collected. */
    public List<CalendarEvent> events(String scope, String username) {
        List<CalendarEvent> out = new ArrayList<>();
        boolean corporate = procurement != null && tenants != null && tenants.findById(de.ostms.lc.tenant.domain.TenantContext.currentId()).map(de.ostms.lc.tenant.domain.Tenant::isCorporateEnabled).orElse(false);
        lettersOfCredit.findAll().stream().filter(lc -> active(lc) && visible(scope, username, lc.getAssignedTo())).forEach(lc -> {
            if (corporate && lc.isExporterSide()) procurement.forLc(lc.getId()).items().stream().filter(i -> i.risk() != ProcurementPlanService.Risk.DONE && i.dueDate() != null)
                    .forEach(i -> add(out, lc, "procurement", lc.getId()+"-procurement-"+java.util.UUID.nameUUIDFromBytes(i.requirement().getBytes(StandardCharsets.UTF_8)), i.dueDate(),
                            "Beschaffung: "+i.documentName()+(i.documentType() != null && ProcurementPlanService.isTransport(i.documentType()) ? " (Versand)" : " bereitstellen"), party(lc)+" | "+i.hint()));
            add(out, lc, "expiry", lc.getId()+"-expiry", lc.getExpiryDate(), "Ablauf", party(lc));
            add(out, lc, "shipment", lc.getId()+"-shipment", lc.getLatestShipmentDate(), "spätester Versand", party(lc));
            add(out, lc, "follow-up", lc.getId()+"-follow-up", lc.getFollowUpDate(), "Wiedervorlage", party(lc));
            deadlines.forLc(lc.getId()).stream().filter(item -> item.type().equals("PRESENTATION") || item.type().equals("MATURITY_ESTIMATE"))
                    .forEach(item -> add(out, lc, item.type().equals("PRESENTATION") ? "presentation" : "maturity", lc.getId()+"-"+item.type()+"-"+item.title().hashCode(), item.date(), item.title(), party(lc)+" | "+item.note()));
        });
        Map<UUID, LetterOfCredit> byId = new HashMap<>();
        tasks.findByCompletedFalseOrderByDueDateAscCreatedAtAsc().stream().filter(task -> task.getDueDate()!=null && visible(scope, username, task.getAssignedTo())).forEach(task -> {
            LetterOfCredit lc = byId.computeIfAbsent(task.getLetterOfCreditId(), id -> lettersOfCredit.findById(id).orElse(null));
            out.add(new CalendarEvent(task.getId()+"-task", task.getLetterOfCreditId(), lc==null ? null : lc.getReference(), "task", task.getTitle(), task.getDueDate(), task.getAssignedTo(), "Zugewiesen an: "+Objects.toString(task.getAssignedTo(), "nicht zugewiesen")));
        });
        return out;
    }

    /** Events inside [from, to], sorted by date. */
    public List<CalendarEvent> events(String scope, String username, LocalDate from, LocalDate to) {
        return events(scope, username).stream().filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to)).sorted(Comparator.comparing(CalendarEvent::date)).toList();
    }

    public byte[] export(String scope, String username) { return export(scope, username, null); }

    /** The calendar file with a reminder per event; with an LC id only that file's dates. */
    public byte[] export(String scope, String username, UUID lcId) {
        StringBuilder out = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//OSTMS//Corporate LC Manager//DE\r\nCALSCALE:GREGORIAN\r\nMETHOD:PUBLISH\r\nX-WR-CALNAME:Corporate LC Manager\r\n");
        events(scope, username).stream().filter(e -> lcId == null || lcId.equals(e.lcId())).forEach(e ->
            event(out, e.uid(), e.date(), ("task".equals(e.kind()) ? "LC-Aufgabe – " : "LC "+e.reference()+" – ")+e.title(), e.description(), e.kind()));
        out.append("END:VCALENDAR\r\n");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** When to remind before an all-day event, as iCalendar durations: early enough to act, once more shortly before. */
    static List<String> reminders(String kind) {
        return switch (kind == null ? "" : kind) {
            case "expiry" -> List.of("-P7D", "-P1D");
            case "presentation" -> List.of("-P3D", "-P1D");
            case "shipment" -> List.of("-P7D", "-P2D");
            case "maturity", "procurement" -> List.of("-P3D");
            default -> List.of("PT9H");
        };
    }

    private void add(List<CalendarEvent> out, LetterOfCredit lc, String kind, String uid, LocalDate date, String title, String description) {
        if (date != null) out.add(new CalendarEvent(uid, lc.getId(), lc.getReference(), kind, title, date, lc.getAssignedTo(), description));
    }

    private boolean active(LetterOfCredit lc) { return lc.getStatus()!=LetterOfCreditStatus.CLOSED && lc.getStatus()!=LetterOfCreditStatus.EXPIRED; }
    private boolean visible(String scope, String username, String assignedTo) { return "mine".equals(scope) ? assignedTo!=null && assignedTo.equalsIgnoreCase(username) : !"unassigned".equals(scope) || assignedTo==null; }
    private String party(LetterOfCredit lc) { return "Applicant: "+Objects.toString(lc.getApplicant(), "–")+" | Beneficiary: "+Objects.toString(lc.getBeneficiary(), "–"); }
    private void event(StringBuilder out, Object uid, LocalDate date, String summary, String description, String kind) {
        if (date==null) return;
        String stamp=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()), day=date.format(DateTimeFormatter.BASIC_ISO_DATE), end=date.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        out.append("BEGIN:VEVENT\r\nUID:").append(uid).append("@lc.example.com\r\nDTSTAMP:").append(stamp).append("\r\nDTSTART;VALUE=DATE:").append(day).append("\r\nDTEND;VALUE=DATE:").append(end).append("\r\nSUMMARY:").append(escape(summary)).append("\r\nDESCRIPTION:").append(escape(description)).append("\r\n");
        for (String trigger : reminders(kind)) out.append("BEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:").append(escape(summary)).append("\r\nTRIGGER:").append(trigger).append("\r\nEND:VALARM\r\n");
        out.append("END:VEVENT\r\n");
    }
    private String escape(String value) { return value.replace("\\","\\\\").replace(";","\\;").replace(",","\\,").replace("\r","").replace("\n","\\n"); }
}
