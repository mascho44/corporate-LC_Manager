package de.ostms.lc.lc.service;

import de.ostms.lc.lc.domain.DeadlineDigestLog;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.DeadlineDigestLogRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.tenant.repository.TenantRepository;
import de.ostms.lc.user.domain.AppUser;
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
 * those of the next {@value #AHEAD_DAYS} days. Only people who switched it on in their profile receive it, and only when something is due. Needs working SMTP.
 */
@Service
public class DeadlineDigestService {
    private static final Logger log = LoggerFactory.getLogger(DeadlineDigestService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    static final int AHEAD_DAYS = 3;
    static final int OVERDUE_DAYS = 30;

    public record Line(String title, LocalDate date, long daysLeft) {}
    public record Entry(String reference, List<Line> lines) {}

    private final LetterOfCreditRepository lettersOfCredit;
    private final LcDeadlineService deadlines;
    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final DeadlineDigestLogRepository digests;
    private final JavaMailSender mail;
    private final boolean enabled;
    private final String from;
    private final String baseUrl;

    public DeadlineDigestService(LetterOfCreditRepository lettersOfCredit, LcDeadlineService deadlines, TenantRepository tenants, AppUserRepository users,
                                 DeadlineDigestLogRepository digests, JavaMailSender mail,
                                 @Value("${app.mail.enabled:false}") boolean enabled, @Value("${app.mail.from:}") String from,
                                 @Value("${app.security.public-base-url:}") String baseUrl) {
        this.lettersOfCredit = lettersOfCredit; this.deadlines = deadlines; this.tenants = tenants; this.users = users; this.digests = digests; this.mail = mail;
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

    static String subject(List<Entry> entries, boolean english) {
        long count = entries.stream().mapToLong(e -> e.lines().size()).sum();
        return english ? "Corporate LC Manager – " + count + (count == 1 ? " deadline" : " deadlines")
                : "Corporate LC Manager – " + count + (count == 1 ? " Frist" : " Fristen");
    }

    static String body(List<Entry> entries, boolean english, String baseUrl) {
        StringBuilder text = new StringBuilder(english ? "These deadlines of your files are due or overdue:\n" : "Diese Fristen Ihrer Akten stehen an oder sind überschritten:\n");
        for (Entry entry : entries) {
            text.append("\n").append(entry.reference()).append('\n');
            for (Line line : entry.lines())
                text.append("  - ").append(line.title()).append(", ").append(DATE.format(line.date())).append(" (").append(when(line.daysLeft(), english)).append(")\n");
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
        if (perUser.isEmpty()) return 0;
        var record = new DeadlineDigestLog(today);
        record.setRecipients(perUser.size());
        digests.saveAndFlush(record);
        int sent = 0;
        for (var recipient : perUser.entrySet()) {
            AppUser user = recipients.get(recipient.getKey());
            boolean english = "en".equalsIgnoreCase(user.getPreferredLanguage());
            try {
                var message = new SimpleMailMessage();
                message.setFrom(from); message.setTo(user.getEmail().strip());
                message.setSubject(subject(recipient.getValue(), english)); message.setText(body(recipient.getValue(), english, baseUrl));
                mail.send(message); sent++;
            } catch (RuntimeException failure) { log.warn("Deadline digest not sent: {}", failure.getClass().getSimpleName()); }
        }
        return sent;
    }
}
