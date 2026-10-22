package de.ostms.lc.sla;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlaTest {
    @Test void easterAndHolidaysOfGermany() {
        assertThat(BusinessCalendar.easter(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(BusinessCalendar.isBusinessDay(LocalDate.of(2026, 4, 3))).isFalse();   // Good Friday
        assertThat(BusinessCalendar.isBusinessDay(LocalDate.of(2026, 4, 6))).isFalse();   // Easter Monday
        assertThat(BusinessCalendar.isBusinessDay(LocalDate.of(2026, 10, 3))).isFalse();  // Saturday and holiday
        assertThat(BusinessCalendar.isBusinessDay(LocalDate.of(2026, 10, 21))).isTrue();
        assertThat(BusinessCalendar.isBusinessDay(LocalDate.of(2026, 10, 24))).isFalse(); // Saturday
    }

    @Test void businessDaysSkipWeekendsAndHolidays() {
        // Wednesday 2026-12-23 + 3 banking days: Thu 24, (Fri 25, Sat 26, Sun 27 off), Mon 28, Tue 29 -> 24, 28, 29
        assertThat(BusinessCalendar.addBusinessDays(LocalDate.of(2026, 12, 23), 3)).isEqualTo(LocalDate.of(2026, 12, 29));
        assertThat(BusinessCalendar.businessDaysBetween(LocalDate.of(2026, 12, 23), LocalDate.of(2026, 12, 29))).isEqualTo(3);
        assertThat(BusinessCalendar.businessDaysBetween(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 21))).isZero();
    }

    private static SlaEvaluator.Event e(String action, String at) { return new SlaEvaluator.Event(action, LocalDateTime.parse(at)); }

    @Test void aClockRunsFromTheStartEventToTheStopEventAndIsRatedInBankingDays() {
        var events = List.of(e("DOCUMENT_UPLOADED", "2026-10-19T10:00:00"), e("DOCUMENT_UPLOADED", "2026-10-19T11:00:00"), e("DOCUMENT_CHECK_REPORT_CREATED", "2026-10-23T09:00:00"));
        var result = SlaEvaluator.evaluate(events, List.of("DOCUMENT_UPLOADED"), List.of("DOCUMENT_CHECK_REPORT_CREATED"), 5, 80, LocalDate.of(2026, 10, 30));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).state()).isEqualTo(SlaEvaluator.State.DONE_IN_TIME);
        assertThat(result.get(0).elapsedDays()).isEqualTo(4);
        assertThat(result.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test void aLateStopIsDoneLateAndARunningClockTurnsFromTrackToWarningToBreached() {
        var late = SlaEvaluator.evaluate(List.of(e("A", "2026-10-19T10:00:00"), e("B", "2026-10-28T10:00:00")), List.of("A"), List.of("B"), 5, 80, LocalDate.of(2026, 11, 2));
        assertThat(late.get(0).state()).isEqualTo(SlaEvaluator.State.DONE_LATE);
        var start = List.of(e("A", "2026-10-19T10:00:00"));
        assertThat(SlaEvaluator.evaluate(start, List.of("A"), List.of("B"), 5, 80, LocalDate.of(2026, 10, 21)).get(0).state()).isEqualTo(SlaEvaluator.State.ON_TRACK);
        assertThat(SlaEvaluator.evaluate(start, List.of("A"), List.of("B"), 5, 80, LocalDate.of(2026, 10, 23)).get(0).state()).isEqualTo(SlaEvaluator.State.WARNING);
        assertThat(SlaEvaluator.evaluate(start, List.of("A"), List.of("B"), 5, 80, LocalDate.of(2026, 10, 26)).get(0).state()).isEqualTo(SlaEvaluator.State.WARNING); // due day itself
        assertThat(SlaEvaluator.evaluate(start, List.of("A"), List.of("B"), 5, 80, LocalDate.of(2026, 10, 27)).get(0).state()).isEqualTo(SlaEvaluator.State.BREACHED);
        assertThat(SlaEvaluator.worstRunning(SlaEvaluator.evaluate(start, List.of("A"), List.of("B"), 5, 80, LocalDate.of(2026, 10, 27)))).isEqualTo(SlaEvaluator.State.BREACHED);
        assertThat(SlaEvaluator.worstRunning(late)).isNull();
    }

    @Test void severalClocksFollowEachOtherAndAStopWithoutStartIsIgnored() {
        var events = List.of(e("B", "2026-10-01T08:00:00"), e("A", "2026-10-02T08:00:00"), e("B", "2026-10-02T09:00:00"), e("A", "2026-10-05T08:00:00"), e("B", "2026-10-06T08:00:00"));
        assertThat(SlaEvaluator.evaluate(events, List.of("A"), List.of("B"), 2, 80, LocalDate.of(2026, 10, 10))).hasSize(2);
    }

    @Test void rulesAreValidated() {
        java.util.function.Function<String, Optional<SlaRule>> none = n -> Optional.empty();
        var ok = new SlaService.RuleRequest("TASK", "Aufgabe", List.of("LC_TASK_CREATED"), List.of("LC_TASK_COMPLETED"), 3, 80, true, null);
        SlaService.validate(ok, Set.of(), null, none);
        assertThatThrownBy(() -> SlaService.validate(new SlaService.RuleRequest(null, " ", List.of("A_B"), List.of("C_D"), 3, 80, true, null), Set.of(), null, none)).hasMessageContaining("Namen");
        assertThatThrownBy(() -> SlaService.validate(new SlaService.RuleRequest(null, "x", List.of("ABC"), List.of("ABC"), 3, 80, true, null), Set.of(), null, none)).hasMessageContaining("gleichzeitig");
        assertThatThrownBy(() -> SlaService.validate(new SlaService.RuleRequest(null, "x", List.of("ABC"), List.of("DEF"), 0, 80, true, null), Set.of(), null, none)).hasMessageContaining("Zielzeit");
        assertThatThrownBy(() -> SlaService.validate(new SlaService.RuleRequest(null, "x", List.of("ABC"), List.of("DEF"), 3, 5, true, null), Set.of(), null, none)).hasMessageContaining("Warnschwelle");
        assertThatThrownBy(() -> SlaService.validate(new SlaService.RuleRequest(null, "x", List.of("abc"), List.of("DEF"), 3, 80, true, null), Set.of(), null, none)).hasMessageContaining("Ungültiges Ereignis");
    }

    @Test void anOpenInboxDocumentIsRatedFromItsArrival() {
        var received = LocalDateTime.of(2026, 10, 19, 10, 0);
        assertThat(SlaService.inboxClock(received, 1, 80, LocalDate.of(2026, 10, 19)).state()).isEqualTo(SlaEvaluator.State.ON_TRACK);
        assertThat(SlaService.inboxClock(received, 1, 80, LocalDate.of(2026, 10, 20)).state()).isEqualTo(SlaEvaluator.State.WARNING);
        assertThat(SlaService.inboxClock(received, 1, 80, LocalDate.of(2026, 10, 21)).state()).isEqualTo(SlaEvaluator.State.BREACHED);
    }
}
