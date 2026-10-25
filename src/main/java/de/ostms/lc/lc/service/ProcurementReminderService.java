package de.ostms.lc.lc.service;

import de.ostms.lc.lc.domain.AutomationRule;
import de.ostms.lc.lc.domain.LcTask;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.AutomationRuleRepository;
import de.ostms.lc.lc.repository.LcTaskRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Team tasks for documents that are still missing and due soon (corporate tenants, trigger PROCUREMENT). One task per LC, document and due date;
 * it closes itself as soon as the document has been uploaded or the LC is finished. The lead time of the rule is the warning horizon in days.
 */
@Service
public class ProcurementReminderService {
    static final String SOURCE = "AUTO_PROCUREMENT", SYSTEM = "system:automation";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private final AutomationRuleRepository rules;
    private final LcTaskRepository tasks;
    private final LetterOfCreditRepository lettersOfCredit;
    private final TeamService teams;
    private final ProcurementPlanService plans;
    private final TenantRepository tenants;

    public ProcurementReminderService(AutomationRuleRepository rules, LcTaskRepository tasks, LetterOfCreditRepository lettersOfCredit, TeamService teams,
                                      ProcurementPlanService plans, TenantRepository tenants) {
        this.rules = rules; this.tasks = tasks; this.lettersOfCredit = lettersOfCredit; this.teams = teams; this.plans = plans; this.tenants = tenants;
    }

    static String keyOf(UUID lcId, String requirement, LocalDate due) {
        return "PROCUREMENT:" + lcId + ":" + UUID.nameUUIDFromBytes(requirement.getBytes(StandardCharsets.UTF_8)) + ":" + due;
    }

    /** Creates the due reminders and closes those that are obsolete; returns the number of created tasks. */
    @Transactional
    public int scan(LocalDate today) {
        var tenant = tenants.findById(TenantContext.currentId()).orElse(null);
        if (tenant == null || !tenant.isCorporateEnabled()) return 0;
        var rule = rules.forTrigger(AutoTaskService.PROCUREMENT).filter(AutomationRule::isEnabled).orElse(null);
        if (rule == null || rule.getTeamId() == null) return 0;
        var team = teams.one(rule.getTeamId());
        if (!team.isActive()) return 0;
        LocalDate horizon = today.plusDays(rule.getLeadDays());
        Set<String> wanted = new HashSet<>();
        int created = 0;
        for (LetterOfCredit lc : lettersOfCredit.findAll()) {
            if (lc.getStatus() == LetterOfCreditStatus.CLOSED || lc.getStatus() == LetterOfCreditStatus.EXPIRED || !lc.isExporterSide()) continue;
            for (var item : plans.forLc(lc.getId(), today).items()) {
                if (item.risk() == ProcurementPlanService.Risk.DONE || item.dueDate() == null || item.dueDate().isAfter(horizon)) continue;
                String key = keyOf(lc.getId(), item.requirement(), item.dueDate());
                wanted.add(key);
                if (tasks.autoKeyExists(key)) continue;
                var task = new LcTask();
                task.setLetterOfCreditId(lc.getId());
                String title = "Beschaffung: " + lc.getReference() + " – " + item.documentName() + " bis " + DATE.format(item.dueDate())
                        + (item.risk() == ProcurementPlanService.Risk.OVERDUE ? " (überfällig)" : "");
                task.setTitle(title.length() > 500 ? title.substring(0, 500) : title);
                task.setDueDate(item.dueDate()); task.setCreatedBy(SYSTEM); task.setTeamId(team.getId()); task.setSource(SOURCE); task.setAutoKey(key);
                tasks.save(task);
                created++;
            }
        }
        close(wanted);
        return created;
    }

    /** Open procurement tasks whose document is there now (or whose due date moved) are completed by the system. */
    private void close(Set<String> wanted) {
        for (LcTask task : tasks.openBySource(SOURCE)) {
            if (task.getAutoKey() != null && wanted.contains(task.getAutoKey())) continue;
            task.setCompleted(true); task.setCompletedAt(LocalDateTime.now()); task.setCompletedBy(SYSTEM);
            if (task.getAssignedTo() == null) task.setAssignedTo(SYSTEM);
            tasks.save(task);
        }
    }
}
