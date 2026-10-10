package de.ostms.lc.lc;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.*;
import de.ostms.lc.lc.service.*;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:autotasks;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class AutoTaskTest {
 @Autowired AutomationRuleRepository ruleRepo;@Autowired WorkflowRepository workflowRepo;@Autowired TeamRepository teamRepo;@Autowired LcTaskRepository taskRepo;@Autowired LetterOfCreditRepository lcs;@Autowired de.ostms.lc.tenant.repository.TenantRepository tenants;
 final UserService users=Mockito.mock(UserService.class);
 TeamService teams;AutoTaskService auto;GroupInboxService inbox;TeamService.View desk;
 @BeforeEach void setUp(){
  if(tenants.findById(Tenant.DEFAULT_ID).isEmpty())tenants.saveAndFlush(new Tenant());
  Mockito.when(users.isAssignable(Mockito.anyString())).thenReturn(true);
  teams=new TeamService(teamRepo,users);auto=new AutoTaskService(ruleRepo,taskRepo,lcs,teams);inbox=new GroupInboxService(taskRepo,teams,lcs,workflowRepo);
  desk=teams.create(new TeamService.Request("Desk",null,List.of("anna","ben"),null));
 }
 void enable(String trigger,int lead){auto.update(new AutoTaskService.RuleRequest(trigger,true,desk.id(),lead));}
 LetterOfCredit lc(String ref,LocalDate expiry,LocalDate followUp,LetterOfCreditStatus status){var l=new LetterOfCredit();l.setReference(ref);l.setExpiryDate(expiry);l.setFollowUpDate(followUp);l.setStatus(status);return lcs.saveAndFlush(l);}

 @Test void nothingHappensWithoutAnEnabledRule(){
  assertThat(auto.onEbicsMessage(UUID.randomUUID(),"MT700","R1")).isEmpty();
  assertThat(auto.onInboxItem(UUID.randomUUID(),"a.pdf")).isEmpty();
  lc("L1",LocalDate.now().plusDays(1),null,LetterOfCreditStatus.ACTIVE);
  assertThat(auto.scanDeadlines(LocalDate.now())).isZero();
  assertThat(auto.rules()).extracting("enabled").containsOnly(false);
 }
 @Test void ruleValidationNeedsAnActiveTeamAndSaneLeadDays(){
  assertThatThrownBy(()->auto.update(new AutoTaskService.RuleRequest("EBICS_MESSAGE",true,null,null))).hasMessageContaining("Team");
  assertThatThrownBy(()->auto.update(new AutoTaskService.RuleRequest("NOPE",false,null,null))).hasMessageContaining("Auslöser");
  assertThatThrownBy(()->auto.update(new AutoTaskService.RuleRequest("DEADLINE",true,desk.id(),99))).hasMessageContaining("Vorlaufzeit");
  var off=auto.update(new AutoTaskService.RuleRequest("INBOX_ITEM",false,null,null));assertThat(off.enabled()).isFalse();
  teams.update(desk.id(),new TeamService.Request("Desk",null,List.of("anna"),false));
  assertThatThrownBy(()->auto.update(new AutoTaskService.RuleRequest("INBOX_ITEM",true,desk.id(),null))).hasMessageContaining("nicht aktiv");
 }
 @Test void ebicsMessageCreatesOneTeamTaskWithoutDossierAndClosesWhenHandled(){
  enable("EBICS_MESSAGE",3);var id=UUID.randomUUID();
  var task=auto.onEbicsMessage(id,"MT700","LC2026").orElseThrow();
  assertThat(task.getLetterOfCreditId()).isNull();assertThat(task.getTeamId()).isEqualTo(desk.id());assertThat(task.getTitle()).contains("MT700").contains("LC2026");
  assertThat(task.getSource()).isEqualTo("AUTO_EBICS_MESSAGE");assertThat(task.getSubjectType()).isEqualTo("EBICS_MESSAGE");assertThat(task.getSubjectId()).isEqualTo(id);
  assertThat(auto.onEbicsMessage(id,"MT700","LC2026")).as("same message only once").isEmpty();
  assertThat(inbox.inbox("ben")).hasSize(1);assertThat(inbox.inbox("ben").get(0).lcReference()).isNull();assertThat(inbox.inbox("ben").get(0).subjectType()).isEqualTo("EBICS_MESSAGE");
  assertThat(auto.closeFor("EBICS_MESSAGE",id,"anna")).isEqualTo(1);
  assertThat(inbox.inbox("ben")).isEmpty();assertThat(auto.closeFor("EBICS_MESSAGE",id,"anna")).isZero();
 }
 @Test void inboxDocumentCreatesATaskThatCanBeCompletedWithoutADossier(){
  enable("INBOX_ITEM",3);var item=UUID.randomUUID();
  var task=auto.onInboxItem(item,"Rechnung.pdf").orElseThrow();
  assertThat(task.getTitle()).contains("Rechnung.pdf");
  assertThatThrownBy(()->inbox.complete(task.getId(),"mallory")).isInstanceOf(AccessDeniedException.class);
  var done=inbox.complete(task.getId(),"ben");
  assertThat(done.isCompleted()).isTrue();assertThat(done.getCompletedBy()).isEqualTo("ben");assertThat(done.getAssignedTo()).isEqualTo("ben");
  assertThatThrownBy(()->inbox.complete(task.getId(),"ben")).isInstanceOf(IllegalStateException.class);
 }
 @Test void aClaimedTaskCanOnlyBeCompletedByTheClaimer(){
  enable("INBOX_ITEM",3);var task=auto.onInboxItem(UUID.randomUUID(),"x.pdf").orElseThrow();
  inbox.claim(task.getId(),"anna");
  assertThatThrownBy(()->inbox.complete(task.getId(),"ben")).isInstanceOf(AccessDeniedException.class).hasMessageContaining("anna");
  assertThat(inbox.complete(task.getId(),"anna").isCompleted()).isTrue();
 }
 @Test void deadlinesWithinTheLeadTimeCreateOneTaskPerDossierAndDate(){
  enable("DEADLINE",3);var today=LocalDate.of(2026,10,10);
  lc("SOON",today.plusDays(2),null,LetterOfCreditStatus.ACTIVE);
  lc("EDGE",today.plusDays(3),today.plusDays(1),LetterOfCreditStatus.ACTIVE);
  lc("LATER",today.plusDays(4),null,LetterOfCreditStatus.ACTIVE);
  lc("PAST",today.minusDays(1),null,LetterOfCreditStatus.ACTIVE);
  lc("DONE",today.plusDays(1),null,LetterOfCreditStatus.CLOSED);
  lc("GONE",today.plusDays(1),null,LetterOfCreditStatus.EXPIRED);
  assertThat(auto.scanDeadlines(today)).isEqualTo(3);
  assertThat(auto.scanDeadlines(today)).as("second scan creates nothing").isZero();
  var titles=inbox.inbox("anna").stream().map(i->i.title()).toList();
  assertThat(titles).anyMatch(t->t.contains("SOON")&&t.contains("Ablauf")).anyMatch(t->t.contains("EDGE")&&t.contains("Ablauf")).anyMatch(t->t.contains("EDGE")&&t.contains("Wiedervorlage"));
  assertThat(inbox.inbox("anna")).allMatch(i->i.lcReference()!=null&&i.dueDate()!=null);
  assertThat(auto.scanDeadlines(today.plusDays(1))).as("LATER is now within 3 days").isEqualTo(1);
 }
 @Test void aCompletedDeadlineTaskIsNotCreatedAgainForTheSameDate(){
  enable("DEADLINE",5);var today=LocalDate.of(2026,10,10);lc("L","2026-10-12".isEmpty()?null:today.plusDays(2),null,LetterOfCreditStatus.ACTIVE);
  auto.scanDeadlines(today);var item=inbox.inbox("anna").get(0);inbox.complete(item.taskId(),"anna");
  assertThat(auto.scanDeadlines(today.plusDays(1))).isZero();
 }
 @Test void automaticTasksDoNotShowUpInTheDossierTaskQueue(){
  enable("EBICS_MESSAGE",3);auto.onEbicsMessage(UUID.randomUUID(),"MT707","X");
  var lcService=Mockito.mock(LetterOfCreditService.class);
  assertThat(new LcTaskService(taskRepo,lcService).open()).isEmpty();
 }
}
