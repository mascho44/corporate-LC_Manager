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
 @Test void everyEventCarriesRemindersAndOneFileCanBeExportedAlone(){
  var a=lc("A",null,LocalDate.of(2026,10,20));var b=lc("B",null,LocalDate.of(2026,11,20));var idA=java.util.UUID.randomUUID();
  org.springframework.test.util.ReflectionTestUtils.setField(a,"id",idA);org.springframework.test.util.ReflectionTestUtils.setField(b,"id",java.util.UUID.randomUUID());
  when(lcs.findAll()).thenReturn(List.of(a,b));
  when(tasks.findByCompletedFalseOrderByDueDateAscCreatedAtAsc()).thenReturn(List.of());
  String all=new String(service.export("all","x"));
  assertThat(all).contains("BEGIN:VALARM").contains("TRIGGER:-P7D").contains("TRIGGER:-P1D");
  assertThat(all.split("BEGIN:VEVENT",-1).length-1).isEqualTo(2);
  String one=new String(service.export("all","x",idA));
  assertThat(one).contains("SUMMARY:LC A – Ablauf").doesNotContain("LC B");
  assertThat(LcCalendarService.reminders("task")).containsExactly("PT9H");
  assertThat(LcCalendarService.reminders("presentation")).containsExactly("-P3D","-P1D");
 }
 @Test void corporateTenantsAlsoSeeTargetDatesOfMissingDocuments(){
  var plans=mock(ProcurementPlanService.class);var tenants=mock(de.ostms.lc.tenant.repository.TenantRepository.class);
  var tenant=new de.ostms.lc.tenant.domain.Tenant();tenant.updateProfile(false,true);
  when(tenants.findById(any())).thenReturn(Optional.of(tenant));
  var item=new ProcurementPlanService.Item("Certificate of origin",de.ostms.lc.document.domain.DocumentType.CERTIFICATE_OF_ORIGIN,"Certificate of Origin",false,LocalDate.of(2026,10,12),7,null,ProcurementPlanService.Risk.SOON,"Vorlauf 7 Arbeitstage",ProcurementItemStatus.Status.NOT_STARTED,null);
  var done=new ProcurementPlanService.Item("Packing list",de.ostms.lc.document.domain.DocumentType.PACKING_LIST,"Packing List",true,LocalDate.of(2026,10,14),3,null,ProcurementPlanService.Risk.DONE,"",ProcurementItemStatus.Status.NOT_STARTED,null);
  var lc=lc("A",null,LocalDate.of(2027,1,1));
  when(plans.forLc(any())).thenReturn(new ProcurementPlanService.Plan(null,null,null,ProcurementPlanService.Risk.OK,1,0,0,List.of(item,done)));
  when(lcs.findAll()).thenReturn(List.of(lc));
  when(tasks.findByCompletedFalseOrderByDueDateAscCreatedAtAsc()).thenReturn(List.of());
  service.setProcurement(plans,tenants);
  try(var context=de.ostms.lc.tenant.domain.TenantContext.open(de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID)){
   var events=service.events("all","x",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31));
   assertThat(events).extracting(LcCalendarService.CalendarEvent::kind).containsExactly("procurement");
   assertThat(events.get(0).title()).isEqualTo("Beschaffung: Certificate of Origin bereitstellen");
  }
 }
}
