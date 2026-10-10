package de.ostms.lc.lc.service;
import de.ostms.lc.lc.domain.*;
import de.ostms.lc.lc.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LcCalendarServiceTest {
 private final LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);
 private final LcTaskRepository tasks=mock(LcTaskRepository.class);
 private final LcDeadlineService deadlines=mock(LcDeadlineService.class);
 private final LcCalendarService service=new LcCalendarService(lcs,tasks,deadlines);

 private LetterOfCredit lc(String ref,String assigned,LocalDate expiry){
  var lc=new LetterOfCredit();lc.setReference(ref);lc.setAssignedTo(assigned);lc.setExpiryDate(expiry);lc.setStatus(LetterOfCreditStatus.ACTIVE);return lc;
 }
 @Test void eventsAreFilteredByRangeAndScopeAndSorted(){
  when(lcs.findAll()).thenReturn(List.of(lc("A","anna",LocalDate.of(2026,10,20)),lc("B",null,LocalDate.of(2026,10,5)),lc("C","anna",LocalDate.of(2027,1,1))));
  when(tasks.findByCompletedFalseOrderByDueDateAscCreatedAtAsc()).thenReturn(List.of());
  var all=service.events("all","anna",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31));
  assertThat(all).extracting(LcCalendarService.CalendarEvent::reference).containsExactly("B","A");
  assertThat(service.events("mine","anna",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31))).extracting(LcCalendarService.CalendarEvent::reference).containsExactly("A");
  assertThat(service.events("unassigned","anna",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31))).extracting(LcCalendarService.CalendarEvent::reference).containsExactly("B");
 }
 @Test void exportStillContainsTheSameEvents(){
  when(lcs.findAll()).thenReturn(List.of(lc("A",null,LocalDate.of(2026,10,20))));
  when(tasks.findByCompletedFalseOrderByDueDateAscCreatedAtAsc()).thenReturn(List.of());
  assertThat(new String(service.export("all","x"))).contains("SUMMARY:LC A – Ablauf").contains("DTSTART;VALUE=DATE:20261020");
 }
}
