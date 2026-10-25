package de.ostms.lc.sla;

import de.ostms.lc.lc.domain.LcTask;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.AutoTaskService;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SlaEscalationServiceTest {
    private static SlaRule rule(UUID team) {
        var rule = new SlaRule();
        rule.set("TASK", "Dokumentenprüfung", List.of("A_X"), List.of("B_X"), 5, 80, true, team, "t");
        return rule;
    }
    private static SlaService.FileStatus status(UUID ruleId, SlaEvaluator.State state, LocalDateTime stopped) {
        var clock = new SlaEvaluator.Instance(LocalDateTime.of(2026, 10, 12, 9, 0), stopped, LocalDate.of(2026, 10, 19), 8, 5, state);
        return new SlaService.FileStatus(state.name(), List.of(new SlaService.InstanceView(ruleId, "Dokumentenprüfung", "TASK", clock)));
    }

    @Test void aBreachedRunningClockCreatesOneTeamTaskPerClockAndOnlyWithAnEscalationTeam() {
        var team = UUID.randomUUID(); var lcId = UUID.randomUUID();
        var withTeam = rule(team); var withoutTeam = rule(null);
        var sla = mock(SlaService.class); var tasks = mock(AutoTaskService.class); var lcs = mock(LetterOfCreditRepository.class);
        when(lcs.findAll()).thenReturn(List.of());
        when(sla.activeRules()).thenReturn(Map.of(withTeam.getId(), withTeam, withoutTeam.getId(), withoutTeam));
        when(sla.statusOfAll(any())).thenReturn(Map.of(lcId, new SlaService.FileStatus("BREACHED", List.of(
            status(withTeam.getId(), SlaEvaluator.State.BREACHED, null).instances().get(0),
            status(withoutTeam.getId(), SlaEvaluator.State.BREACHED, null).instances().get(0),
            status(withTeam.getId(), SlaEvaluator.State.DONE_LATE, LocalDateTime.of(2026, 10, 20, 9, 0)).instances().get(0),
            status(withTeam.getId(), SlaEvaluator.State.WARNING, null).instances().get(0)))));
        when(tasks.createForTeam(eq(team), eq("AUTO_SLA"), anyString(), eq(lcId), startsWith("SLA:"), any())).thenReturn(Optional.of(new LcTask()));
        int created = new SlaEscalationService(sla, tasks, lcs).escalate(LocalDate.of(2026, 10, 22));
        assertThat(created).isEqualTo(1);
        verify(tasks, times(1)).createForTeam(any(), any(), anyString(), any(), anyString(), any());
    }
}
