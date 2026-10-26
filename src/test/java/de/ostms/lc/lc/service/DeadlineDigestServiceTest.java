package de.ostms.lc.lc.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import de.ostms.lc.document.domain.DocumentDraft;
import de.ostms.lc.document.domain.DocumentDraftStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class DeadlineDigestServiceTest {
 static final LocalDate TODAY=LocalDate.of(2026,10,26);
 static LcDeadlineService.LcDeadlineView due(String title,LocalDate date){return new LcDeadlineService.LcDeadlineView("T",title,date,"src",null,true);}

 @Test void overdueAndNextThreeDaysAreSelectedEarliestFirst(){
  var lines=DeadlineDigestService.select(List.of(due("in 3 Tagen",TODAY.plusDays(3)),due("gestern",TODAY.minusDays(1)),due("heute",TODAY)),TODAY);
  assertThat(lines).extracting(DeadlineDigestService.Line::title).containsExactly("gestern","heute","in 3 Tagen");
  assertThat(lines).extracting(DeadlineDigestService.Line::daysLeft).containsExactly(-1L,0L,3L);
 }
 @Test void farFutureVeryOldAndUndatedDeadlinesAreLeftOut(){
  var lines=DeadlineDigestService.select(List.of(due("in 4 Tagen",TODAY.plusDays(4)),due("vor 31 Tagen",TODAY.minusDays(31)),due("ohne Datum",null),due("vor 30 Tagen",TODAY.minusDays(30))),TODAY);
  assertThat(lines).extracting(DeadlineDigestService.Line::title).containsExactly("vor 30 Tagen");
 }
 @Test void mailTextFollowsTheLanguageAndSaysHowToSwitchOff(){
  var entries=List.of(new DeadlineDigestService.Entry("LC-1",List.of(new DeadlineDigestService.Line("Ablauf",TODAY.minusDays(2),-2),new DeadlineDigestService.Line("Vorlage",TODAY,0))));
  var de=DeadlineDigestService.body(entries,false,"https://lc.example");
  assertThat(de).contains("LC-1").contains("Ablauf, 24.10.2026 (seit 2 Tagen überfällig)").contains("Vorlage, 26.10.2026 (heute fällig)").contains("https://lc.example").contains("ausschalten");
  var en=DeadlineDigestService.body(entries,true,"");
  assertThat(en).contains("overdue by 2 days").contains("due today").contains("switch it off").doesNotContain("http");
  assertThat(DeadlineDigestService.subject(entries,false)).isEqualTo("Corporate LC Manager – 2 Fristen");
  assertThat(DeadlineDigestService.subject(List.of(new DeadlineDigestService.Entry("X",List.of(new DeadlineDigestService.Line("a",TODAY,0)))),true)).isEqualTo("Corporate LC Manager – 1 deadline");
 }

 static final ObjectMapper JSON=JsonMapper.builder().build();
 static final UUID LC=UUID.randomUUID();
 static DocumentDraft draft(DocumentDraftStatus status,String createdBy,String checkedBy,String approvalsJson){
  var d=new DocumentDraft();d.setLcId(LC);d.setDocumentNumber("INV-1");d.setStatus(status);d.setCreatedBy(createdBy);d.setCheckedBy(checkedBy);d.setApprovalsJson(approvalsJson);return d;
 }
 static List<DeadlineDigestService.Pending> pending(String user,boolean review,boolean approve,DocumentDraft... drafts){
  return DeadlineDigestService.pending(List.of(drafts),user,review,approve,id->LC.equals(id)?"LC-1":null,JSON);
 }
 @Test void submittedDraftsWaitForCheckersExceptTheCreator(){
  var submitted=draft(DocumentDraftStatus.SUBMITTED,"anna",null,"[]");
  assertThat(pending("ben",true,false,submitted)).extracting(DeadlineDigestService.Pending::approve).containsExactly(false);
  assertThat(pending("anna",true,false,submitted)).isEmpty();
  assertThat(pending("ben",false,true,submitted)).isEmpty();
 }
 @Test void checkedDraftsWaitForApproversExceptCreatorCheckerAndThoseWhoAlreadyApproved(){
  var checked=draft(DocumentDraftStatus.REVIEWED,"anna","ben","[\"clara\"]");
  assertThat(pending("dora",false,true,checked)).extracting(DeadlineDigestService.Pending::approve).containsExactly(true);
  assertThat(pending("anna",false,true,checked)).isEmpty();
  assertThat(pending("ben",false,true,checked)).isEmpty();
  assertThat(pending("clara",false,true,checked)).isEmpty();
 }
 @Test void draftsOfClosedFilesAndFinalOnesAreIgnored(){
  var other=draft(DocumentDraftStatus.SUBMITTED,"anna",null,"[]");other.setLcId(UUID.randomUUID());
  assertThat(pending("ben",true,true,other,draft(DocumentDraftStatus.FINAL,"anna","ben","[]"),draft(DocumentDraftStatus.DRAFT,"anna",null,"[]"))).isEmpty();
 }
 @Test void mailListsWaitingDocumentsAlsoWithoutDeadlines(){
  var waiting=List.of(new DeadlineDigestService.Pending("LC-1","INV-1",true),new DeadlineDigestService.Pending("LC-2","PL-7",false));
  var de=DeadlineDigestService.body(List.of(),waiting,false,"");
  assertThat(de).contains("auf Ihre Prüfung oder Freigabe warten").contains("LC-1: INV-1 (Freigabe)").contains("LC-2: PL-7 (Prüfung)").doesNotContain("Fristen Ihrer Akten");
  assertThat(DeadlineDigestService.subject(List.of(),waiting,false)).isEqualTo("Corporate LC Manager – 2 Dokumente warten auf Sie");
  assertThat(DeadlineDigestService.body(List.of(),waiting,true,"")).contains("waiting for your check or approval").contains("(approval)").contains("(check)");
 }
}
