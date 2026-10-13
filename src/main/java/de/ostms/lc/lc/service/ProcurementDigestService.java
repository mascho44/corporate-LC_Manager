package de.ostms.lc.lc.service;

import de.ostms.lc.lc.domain.AutomationRule;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.domain.ProcurementDigestLog;
import de.ostms.lc.lc.repository.AutomationRuleRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.repository.ProcurementDigestLogRepository;
import de.ostms.lc.tenant.domain.TenantContext;
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
import java.util.*;

/**
 * One mail per day and recipient with the documents that are overdue, critical or due soon. Recipient is the person the LC is assigned to; an LC without
 * assignee goes to the members of the team of the PROCUREMENT automation rule. Needs the same rule as the procurement tasks and working SMTP.
 */
@Service
public class ProcurementDigestService {
    private static final Logger log = LoggerFactory.getLogger(ProcurementDigestService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    public record Line(ProcurementPlanService.Risk risk, String document, LocalDate due) {}
    public record Entry(String reference, LocalDate shipment, Integer daysToShipment, List<Line> lines) {}

    private final AutomationRuleRepository rules;
    private final LetterOfCreditRepository lettersOfCredit;
    private final TeamService teams;
    private final ProcurementPlanService plans;
    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final ProcurementDigestLogRepository digests;
    private final JavaMailSender mail;
    private final boolean enabled;
    private final String from, baseUrl;

    public ProcurementDigestService(AutomationRuleRepository rules, LetterOfCreditRepository lettersOfCredit, TeamService teams, ProcurementPlanService plans,
                                    TenantRepository tenants, AppUserRepository users, ProcurementDigestLogRepository digests, JavaMailSender mail,
                                    @Value("${app.mail.enabled:false}") boolean enabled, @Value("${app.mail.from:}") String from,
                                    @Value("${app.security.public-base-url:}") String baseUrl) {
        this.rules = rules; this.lettersOfCredit = lettersOfCredit; this.teams = teams; this.plans = plans; this.tenants = tenants; this.users = users;
        this.digests = digests; this.mail = mail; this.enabled = enabled; this.from = from == null ? "" : from; this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    static boolean worthMentioning(ProcurementPlanService.Risk risk) {
        return risk == ProcurementPlanService.Risk.OVERDUE || risk == ProcurementPlanService.Risk.CRITICAL || risk == ProcurementPlanService.Risk.SOON;
    }

    static String label(ProcurementPlanService.Risk risk) {
        return switch (risk) { case OVERDUE -> "ÜBERFÄLLIG"; case CRITICAL -> "kritisch"; default -> "bald fällig"; };
    }

    static String subject(List<Entry> entries) {
        long documents = entries.stream().mapToLong(e -> e.lines().size()).sum();
        return "Corporate LC Manager – Beschaffung: " + documents + (documents == 1 ? " Dokument braucht" : " Dokumente brauchen") + " Aufmerksamkeit";
    }

    static String body(List<Entry> entries, String baseUrl) {
        StringBuilder text = new StringBuilder("Diese Dokumente für Ihre Akkreditive werden knapp oder sind überfällig:\n");
        for (Entry entry : entries) {
            text.append("\nLC ").append(entry.reference());
            if (entry.shipment() != null) text.append(" – spätester Versand ").append(DATE.format(entry.shipment()))
                    .append(entry.daysToShipment() == null ? "" : entry.daysToShipment() < 0 ? " (" + -entry.daysToShipment() + " Tage her)" : " (in " + entry.daysToShipment() + " Tagen)");
            text.append('\n');
            for (Line line : entry.lines()) text.append("  - ").append(label(line.risk())).append(": ").append(line.document())
                    .append(line.due() == null ? "" : ", Zieltermin " + DATE.format(line.due())).append('\n');
        }
        if (!baseUrl.isBlank()) text.append("\nZum Beschaffungsplan: ").append(baseUrl).append('\n');
        text.append("\nDiese Zusammenfassung kommt höchstens einmal täglich und nur, wenn etwas ansteht.\n");
        return text.toString();
    }

    /** Sends today's digest of the current tenant once; returns the number of mails sent. */
    @Transactional
    public int sendDaily(LocalDate today) {
        if (!enabled || from.isBlank()) return 0;
        var tenant = tenants.findById(TenantContext.currentId()).orElse(null);
        if (tenant == null || !tenant.isCorporateEnabled()) return 0;
        var rule = rules.forTrigger(AutoTaskService.PROCUREMENT).filter(AutomationRule::isEnabled).orElse(null);
        if (rule == null || rule.getTeamId() == null) return 0;
        var team = teams.one(rule.getTeamId());
        if (!team.isActive() || digests.sentOn(today)) return 0;

        Map<String, String> emails = new HashMap<>();
        for (AppUser user : users.findAssignableMembershipUsers())
            if (user.getEmail() != null && !user.getEmail().isBlank()) emails.put(user.getUsername().toLowerCase(Locale.ROOT), user.getEmail().strip());
        Map<String, List<Entry>> perRecipient = new TreeMap<>();
        for (LetterOfCredit lc : lettersOfCredit.findAll()) {
            if (lc.getStatus() == LetterOfCreditStatus.CLOSED || lc.getStatus() == LetterOfCreditStatus.EXPIRED) continue;
            var plan = plans.forLc(lc.getId(), today);
            List<Line> lines = plan.items().stream().filter(i -> worthMentioning(i.risk()))
                    .sorted(Comparator.comparing(ProcurementPlanService.Item::dueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(i -> new Line(i.risk(), i.documentName(), i.dueDate())).toList();
            if (lines.isEmpty()) continue;
            Set<String> to = new TreeSet<>();
            if (lc.getAssignedTo() != null && !lc.getAssignedTo().isBlank()) to.add(lc.getAssignedTo().toLowerCase(Locale.ROOT));
            else team.getMembers().forEach(member -> to.add(member.toLowerCase(Locale.ROOT)));
            var entry = new Entry(lc.getReference(), plan.latestShipment(), plan.daysToShipment(), lines);
            for (String recipient : to) if (emails.containsKey(recipient)) perRecipient.computeIfAbsent(emails.get(recipient), k -> new ArrayList<>()).add(entry);
        }
        if (perRecipient.isEmpty()) return 0;
        var record = new ProcurementDigestLog(today);
        record.setRecipients(perRecipient.size());
        digests.saveAndFlush(record);
        int sent = 0;
        for (var recipient : perRecipient.entrySet()) {
            try {
                var message = new SimpleMailMessage();
                message.setFrom(from); message.setTo(recipient.getKey());
                message.setSubject(subject(recipient.getValue())); message.setText(body(recipient.getValue(), baseUrl));
                mail.send(message); sent++;
            } catch (RuntimeException failure) { log.warn("Procurement digest not sent: {}", failure.getClass().getSimpleName()); }
        }
        return sent;
    }
}
