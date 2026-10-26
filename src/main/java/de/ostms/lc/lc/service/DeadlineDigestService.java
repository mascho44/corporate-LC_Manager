package de.ostms.lc.lc.service;

import de.ostms.lc.document.domain.DocumentDraft;
import de.ostms.lc.document.domain.DocumentDraftStatus;
import de.ostms.lc.document.repository.DocumentDraftRepository;
import de.ostms.lc.lc.domain.DeadlineDigestLog;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.DeadlineDigestLogRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.tenant.repository.TenantRepository;
import de.ostms.lc.user.domain.AppUser;
import de.ostms.lc.user.domain.UserPermission;
import tools.jackson.databind.ObjectMapper;
import de.ostms.lc.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * One mail per day, tenant and recipient with the deadlines of the files assigned to that person: overdue ones (up to {@value #OVERDUE_DAYS} days back) and
 * those of the next {@value #AHEAD_DAYS} days, plus the document drafts that wait for this person's check or approval. Only people who switched it on in their profile receive it, and only when something is due. Needs working SMTP.
 */
@Service
public class DeadlineDigestService {
    private static final Logger log = LoggerFactory.getLogger(DeadlineDigestService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    static final int AHEAD_DAYS = 3;
    static final int OVERDUE_DAYS = 30;

    public record Line(String title, LocalDate date, long daysLeft) {}
    public record Entry(String reference, List<Line> lines) {}
    /** A document draft that waits for a check (approve = false) or an approval (approve = true) by the recipient. */
    public record Pending(String reference, String documentNumber, boolean approve) {}

    private final LetterOfCreditRepository lettersOfCredit;
    private final LcDeadlineService deadlines;
    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final DeadlineDigestLogRepository digests;
    private final DocumentDraftRepository drafts;
    private final ObjectMapper json;
    private final JavaMailSender mail;
    private final boolean enabled;
    private final String from;
    private final String baseUrl;

    public DeadlineDigestService(LetterOfCreditRepository lettersOfCredit, LcDeadlineService deadlines, TenantRepository tenants, AppUserRepository users,
                                 DeadlineDigestLogRepository digests, DocumentDraftRepository drafts, ObjectMapper json, JavaMailSender mail,
                                 @Value("${app.mail.enabled:false}") boolean enabled, @Value("${app.mail.from:}") String from,
                                 @Value("${app.security.public-base-url:}") String baseUrl) {
        this.lettersOfCredit = lettersOfCredit; this.deadlines = deadlines; this.tenants = tenants; this.users = users; this.digests = digests; this.drafts = drafts; this.json = json; this.mail = mail;
        this.enabled = enabled; this.from = from == null ? "" : from; this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    /** The deadlines worth a mention on `today`: overdue (not older than {@value #OVERDUE_DAYS} days) and due within the next {@value #AHEAD_DAYS} days, earliest first. */
    static List<Line> select(List<LcDeadlineService.LcDeadlineView> all, LocalDate today) {
        return all.stream()
                .filter(d -> d.date() != null)
                .map(d -> new Line(d.title(), d.date(), ChronoUnit.DAYS.between(today, d.date())))
                .filter(l -> l.daysLeft() <= AHEAD_DAYS && l.daysLeft() >= -OVERDUE_DAYS)
                .sorted(Comparator.comparing(Line::date).thenComparing(Line::title, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static String when(long daysLeft, boolean english) {
        if (daysLeft < 0) return english ? "overdue by " + -daysLeft + (daysLeft == -1 ? " day" : " days") : "seit " + -daysLeft + (daysLeft == -1 ? " Tag" : " Tagen") + " überfällig";
        if (daysLeft == 0) return english ? "due today" : "heute fällig";
        return english ? "in " + daysLeft + (daysLeft == 1 ? " day" : " days") : "in " + daysLeft + (daysLeft == 1 ? " Tag" : " Tagen");
    }

    /** Drafts waiting for this person: submitted ones they may check (not their own), checked ones they may approve (not their own, not the checker, not yet approved by them). */
    static List<Pending> pending(List<DocumentDraft> drafts, String username, boolean mayReview, boolean mayApprove, java.util.function.Function<UUID, String> reference, ObjectMapper json) {
        List<Pending> out = new ArrayList<>();
        for (DocumentDraft d : drafts) {
            String ref = reference.apply(d.getLcId());
            if (ref == null || username == null) continue;
            if (d.getStatus() == DocumentDraftStatus.SUBMITTED && mayReview && !username.equalsIgnoreCase(d.getCreatedBy()))
                out.add(new Pending(ref, d.getDocumentNumber(), false));
            else if (d.getStatus() == DocumentDraftStatus.REVIEWED && mayApprove && !username.equalsIgnoreCase(d.getCreatedBy()) && !username.equalsIgnoreCase(d.getCheckedBy()) && !approvedBy(d, username, json))
                out.add(new Pending(ref, d.getDocumentNumber(), true));
        }
        out.sort(Comparator.comparing(Pending::reference).thenComparing(p -> Objects.toString(p.documentNumber(), "")));
        return out;
    }

    private static boolean approvedBy(DocumentDraft draft, String username, ObjectMapper json) {
        try {
            List<String> approvals = json.readValue(draft.getApprovalsJson(), json.getTypeFactory().constructCollectionType(List.class, String.class));
            return approvals.stream().anyMatch(username::equalsIgnoreCase);
        } catch (RuntimeException broken) { return false; }
    }

    static String subject(List<Entry> entries, boolean english) { return subject(entries, List.of(), english); }

    static String subject(List<Entry> entries, List<Pending> pending, boolean english) {
        long count = entries.stream().mapToLong(e -> e.lines().size()).sum();
        if (count == 0) return english ? "Corporate LC Manager – " + pending.size() + (pending.size() == 1 ? " document waiting for you" : " documents waiting for you")
                : "Corporate LC Manager – " + pending.size() + (pending.size() == 1 ? " Dokument wartet auf Sie" : " Dokumente warten auf Sie");
        return english ? "Corporate LC Manager – " + count + (count == 1 ? " deadline" : " deadlines")
                : "Corporate LC Manager – " + count + (count == 1 ? " Frist" : " Fristen");
    }

    static String body(List<Entry> entries, boolean english, String baseUrl) { return body(entries, List.of(), english, baseUrl); }

    static String body(List<Entry> entries, List<Pending> pending, boolean english, String baseUrl) {
        StringBuilder text = new StringBuilder();
        if (!entries.isEmpty()) text.append(english ? "These deadlines of your files are due or overdue:\n" : "Diese Fristen Ihrer Akten stehen an oder sind überschritten:\n");
        for (Entry entry : entries) {
            text.append("\n").append(entry.reference()).append('\n');
            for (Line line : entry.lines())
                text.append("  - ").append(line.title()).append(", ").append(DATE.format(line.date())).append(" (").append(when(line.daysLeft(), english)).append(")\n");
        }
        if (!pending.isEmpty()) {
            if (!entries.isEmpty()) text.append('\n');
            text.append(english ? "Documents waiting for your check or approval:\n" : "Dokumente, die auf Ihre Prüfung oder Freigabe warten:\n");
            for (Pending p : pending)
                text.append("  - ").append(p.reference()).append(": ").append(Objects.toString(p.documentNumber(), "–")).append(p.approve() ? (english ? " (approval)" : " (Freigabe)") : (english ? " (check)" : " (Prüfung)")).append('\n');
        }
        if (!baseUrl.isBlank()) text.append('\n').append(english ? "Open the application: " : "Zur Anwendung: ").append(baseUrl).append('\n');
        text.append('\n').append(english ? "You receive this summary at most once a day and only when something is due. You can switch it off in your profile.\n"
                : "Diese Zusammenfassung kommt höchstens einmal täglich und nur, wenn etwas ansteht. Sie können sie in Ihrem Profil ausschalten.\n");
        return text.toString();
    }

    /** Sends today's digest of the current tenant once; returns the number of mails sent. */
    @Transactional
    public int sendDaily(LocalDate today) {
        if (!enabled || from.isBlank()) return 0;
        var tenant = tenants.findById(de.ostms.lc.tenant.domain.TenantContext.currentId()).orElse(null);
        if (tenant == null || digests.sentOn(today)) return 0;

        Map<String, AppUser> recipients = new HashMap<>();
        for (AppUser user : users.findAssignableMembershipUsers())
            if (user.isDeadlineDigest() && user.getEmail() != null && !user.getEmail().isBlank()) recipients.put(user.getUsername().toLowerCase(Locale.ROOT), user);
        if (recipients.isEmpty()) return 0;

        Map<String, List<Entry>> perUser = new TreeMap<>();
        for (LetterOfCredit lc : lettersOfCredit.findAll()) {
            if (lc.getStatus() == LetterOfCreditStatus.CLOSED || lc.getStatus() == LetterOfCreditStatus.EXPIRED) continue;
            String assignee = lc.getAssignedTo() == null ? "" : lc.getAssignedTo().toLowerCase(Locale.ROOT);
            if (!recipients.containsKey(assignee)) continue;
            List<Line> lines = select(deadlines.forLc(lc.getId()), today);
            if (!lines.isEmpty()) perUser.computeIfAbsent(assignee, k -> new ArrayList<>()).add(new Entry(lc.getReference(), lines));
        }
        Map<UUID, String> references = new HashMap<>();
        for (LetterOfCredit lc : lettersOfCredit.findAll())
            if (lc.getStatus() != LetterOfCreditStatus.CLOSED && lc.getStatus() != LetterOfCreditStatus.EXPIRED) references.put(lc.getId(), lc.getReference());
        List<DocumentDraft> waiting = drafts.findAll().stream().filter(d -> d.getStatus() == DocumentDraftStatus.SUBMITTED || d.getStatus() == DocumentDraftStatus.REVIEWED).toList();
        Map<String, List<Pending>> pendingPerUser = new TreeMap<>();
        if (!waiting.isEmpty())
            for (var candidate : recipients.entrySet()) {
                var permissions = candidate.getValue().effectivePermissions();
                var list = pending(waiting, candidate.getValue().getUsername(), permissions.contains(UserPermission.DOCUMENT_REVIEW), permissions.contains(UserPermission.DOCUMENT_APPROVE), references::get, json);
                if (!list.isEmpty()) pendingPerUser.put(candidate.getKey(), list);
            }
        Set<String> toNotify = new TreeSet<>(perUser.keySet());
        toNotify.addAll(pendingPerUser.keySet());
        if (toNotify.isEmpty()) return 0;
        var record = new DeadlineDigestLog(today);
        record.setRecipients(toNotify.size());
        digests.saveAndFlush(record);
        int sent = 0;
        for (String key : toNotify) {
            AppUser user = recipients.get(key);
            List<Entry> entries = perUser.getOrDefault(key, List.of());
            List<Pending> waitingForUser = pendingPerUser.getOrDefault(key, List.of());
            boolean english = "en".equalsIgnoreCase(user.getPreferredLanguage());
            try {
                var message = new SimpleMailMessage();
                message.setFrom(from); message.setTo(user.getEmail().strip());
                message.setSubject(subject(entries, waitingForUser, english)); message.setText(body(entries, waitingForUser, english, baseUrl));
                mail.send(message); sent++;
            } catch (RuntimeException failure) { log.warn("Deadline digest not sent: {}", failure.getClass().getSimpleName()); }
        }
        return sent;
    }
}
