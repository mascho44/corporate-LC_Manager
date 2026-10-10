package de.ostms.lc.lc;
import de.ostms.lc.lc.domain.LetterOfCredit;
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
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:workflows;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class WorkflowTest {
 @Autowired WorkflowRepository workflowRepo;@Autowired TeamRepository teamRepo;@Autowired LcTaskRepository taskRepo;@Autowired LetterOfCreditRepository lcs;@Autowired de.ostms.lc.tenant.repository.TenantRepository tenants;
 final UserService users=Mockito.mock(UserService.class);
 TeamService teams;WorkflowService workflows;LcTaskService taskService;GroupInboxService inbox;LetterOfCredit lc;TeamService.View desk,approvers;
 @BeforeEach void setUp(){
  if(tenants.findById(Tenant.DEFAULT_ID).isEmpty())tenants.saveAndFlush(new Tenant());
  Mockito.when(users.isAssignable(Mockito.anyString())).thenReturn(true);
  teams=new TeamService(teamRepo,users);workflows=new WorkflowService(workflowRepo,taskRepo,teams,lcs);
  taskService=new LcTaskService(taskRepo,Mockito.mock(LetterOfCreditService.class));
  ReflectionTestUtils.setField(taskService,"workflowService",workflows);ReflectionTestUtils.setField(taskService,"teams",teams);
  inbox=new GroupInboxService(taskRepo,teams,lcs,workflowRepo);
  lc=new LetterOfCredit();lc.setReference("LC-W");lc=lcs.saveAndFlush(lc);
  desk=teams.create(new TeamService.Request("Sachbearbeitung",null,List.of("anna","ben"),null));
  approvers=teams.create(new TeamService.Request("Freigabe",null,List.of("anna","chef"),null));
 }
 UUID start(String template){return workflows.start(lc.getId(),new WorkflowService.StartRequest(template,desk.id(),approvers.id()),"boss").getId();}
 de.ostms.lc.lc.domain.LcTask open(UUID workflow){return taskRepo.forWorkflow(workflow).stream().filter(t->!t.isCompleted()).findFirst().orElseThrow();}
 void done(UUID workflow,String user){var t=open(workflow);taskService.complete(lc.getId(),t.getId(),true,user);}

 @Test void startCreatesTheFirstStepAsAnUnclaimedTeamTask(){
  var id=start("NEW_LC");var task=open(id);
  assertThat(task.getStepNo()).isEqualTo(1);assertThat(task.getTeamId()).isEqualTo(desk.id());assertThat(task.getAssignedTo()).isNull();
  assertThat(task.getTitle()).contains("Neue Akte prüfen");assertThat(task.getDueDate()).isNotNull();
  var label=inbox.inbox("ben").get(0);assertThat(label.workflow()).isEqualTo("Neue Akte prüfen · Schritt 1/3");
  assertThat(inbox.inbox("chef")).as("approval team sees nothing yet").isEmpty();
 }
 @Test void completingStepsWalksThroughTheTemplateAndFinishes(){
  var id=start("NEW_LC");
  done(id,"ben");assertThat(open(id).getStepNo()).isEqualTo(2);
  done(id,"anna");
  var approve=open(id);assertThat(approve.getStepNo()).isEqualTo(3);assertThat(approve.getTeamId()).as("approval step goes to the approval team").isEqualTo(approvers.id());assertThat(approve.isFourEyes()).isTrue();
  done(id,"chef");
  var view=workflows.forLc(lc.getId()).get(0);
  assertThat(view.status()).isEqualTo("DONE");assertThat(view.steps()).extracting("state").containsOnly("DONE");assertThat(view.steps()).extracting("completedBy").containsExactly("ben","anna","chef");
  assertThat(inbox.inbox("chef")).isEmpty();
 }
 @Test void fourEyesBlocksTheSamePersonAtClaimAndAtCompletion(){
  var id=start("NEW_LC");done(id,"ben");done(id,"anna");
  var approve=open(id);
  assertThatThrownBy(()->inbox.claim(approve.getId(),"anna")).isInstanceOf(AccessDeniedException.class).hasMessageContaining("Vier-Augen");
  assertThatThrownBy(()->taskService.complete(lc.getId(),approve.getId(),true,"anna")).isInstanceOf(AccessDeniedException.class).hasMessageContaining("Vier-Augen");
  assertThat(open(id).isCompleted()).isFalse();
  taskService.complete(lc.getId(),approve.getId(),true,"chef");
  assertThat(workflows.forLc(lc.getId()).get(0).status()).isEqualTo("DONE");
 }
 @Test void onlyTeamMembersAndTheClaimerCanComplete(){
  var id=start("AMENDMENT");var step=open(id);
  assertThatThrownBy(()->taskService.complete(lc.getId(),step.getId(),true,"mallory")).isInstanceOf(AccessDeniedException.class).hasMessageContaining("Mitglieder");
  inbox.claim(step.getId(),"ben");
  assertThatThrownBy(()->taskService.complete(lc.getId(),step.getId(),true,"anna")).isInstanceOf(AccessDeniedException.class).hasMessageContaining("ben");
  taskService.complete(lc.getId(),step.getId(),true,"ben");
  assertThat(open(id).getStepNo()).isEqualTo(2);
 }
 @Test void aTemplateRunsOnlyOncePerDossierAtATime(){
  var id=start("GUARANTEE");
  assertThatThrownBy(()->start("GUARANTEE")).isInstanceOf(IllegalStateException.class).hasMessageContaining("Für diese Akte läuft");
  start("DOCUMENT_CHECK");
  workflows.cancel(id,"boss");
  assertThat(start("GUARANTEE")).isNotEqualTo(id);
 }
 @Test void cancelRemovesOnlyTheOpenStepAndKeepsHistory(){
  var id=start("NEW_LC");done(id,"ben");
  workflows.cancel(id,"boss");
  var tasks=taskRepo.forWorkflow(id);
  assertThat(tasks).hasSize(1);assertThat(tasks.get(0).isCompleted()).isTrue();
  assertThat(workflows.forLc(lc.getId()).stream().filter(w->w.id().equals(id)).findFirst().orElseThrow().status()).isEqualTo("CANCELLED");
  assertThatThrownBy(()->workflows.cancel(id,"boss")).isInstanceOf(IllegalStateException.class);
 }
 @Test void workflowStepsCannotBeReopenedOrDeletedAsPlainTasks(){
  var id=start("AMENDMENT");var step=open(id);
  assertThatThrownBy(()->taskService.delete(lc.getId(),step.getId())).isInstanceOf(IllegalStateException.class);
  assertThatThrownBy(()->taskService.complete(lc.getId(),step.getId(),false,"ben")).isInstanceOf(IllegalStateException.class);
  assertThatThrownBy(()->taskService.complete(lc.getId(),step.getId(),true)).isInstanceOf(IllegalStateException.class);
 }
 @Test void startValidatesTemplateTeamsAndDossier(){
  assertThatThrownBy(()->workflows.start(lc.getId(),new WorkflowService.StartRequest("NOPE",desk.id(),null),"boss")).hasMessageContaining("Vorlage");
  assertThatThrownBy(()->workflows.start(lc.getId(),new WorkflowService.StartRequest("NEW_LC",null,null),"boss")).hasMessageContaining("Team");
  teams.update(desk.id(),new TeamService.Request("Sachbearbeitung",null,List.of("anna"),false));
  assertThatThrownBy(()->workflows.start(lc.getId(),new WorkflowService.StartRequest("NEW_LC",desk.id(),null),"boss")).hasMessageContaining("nicht aktiv");
  assertThatThrownBy(()->workflows.start(UUID.randomUUID(),new WorkflowService.StartRequest("NEW_LC",approvers.id(),null),"boss")).isInstanceOf(java.util.NoSuchElementException.class);
 }
 @Test void withoutApprovalTeamAllStepsGoToTheMainTeam(){
  var id=workflows.start(lc.getId(),new WorkflowService.StartRequest("GUARANTEE",desk.id(),null),"boss").getId();
  done(id,"ben");done(id,"anna");
  assertThat(open(id).getTeamId()).isEqualTo(desk.id());
 }
 @Test void templatesAreWellFormed(){
  assertThat(WorkflowTemplates.all()).hasSize(4);
  WorkflowTemplates.all().forEach(t->{assertThat(t.steps()).isNotEmpty();assertThat(t.steps().get(0).fourEyes()).as("first step cannot need four eyes").isFalse();});
 }
}
