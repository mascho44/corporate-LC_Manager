package de.ostms.lc.lc;
import de.ostms.lc.lc.api.LcTaskRequest;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:groupinbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class GroupInboxTest {
 @Autowired WorkflowRepository workflowRepo;@Autowired TeamRepository teamRepo;@Autowired LcTaskRepository taskRepo;@Autowired LetterOfCreditRepository lcs;@Autowired de.ostms.lc.tenant.repository.TenantRepository tenants;
 final UserService users=Mockito.mock(UserService.class);
 TeamService teams;GroupInboxService inbox;LcTaskService taskService;LetterOfCredit lc;
 @BeforeEach void setUp(){
  if(tenants.findById(Tenant.DEFAULT_ID).isEmpty())tenants.saveAndFlush(new Tenant());
  Mockito.when(users.isAssignable(Mockito.anyString())).thenAnswer(i->!"ghost".equals(i.getArgument(0)));
  teams=new TeamService(teamRepo,users);inbox=new GroupInboxService(taskRepo,teams,lcs,workflowRepo);
  var lcService=Mockito.mock(LetterOfCreditService.class);
  taskService=new LcTaskService(taskRepo,lcService);ReflectionTestUtils.setField(taskService,"teams",teams);
  lc=new LetterOfCredit();lc.setReference("LC-1");lc=lcs.saveAndFlush(lc);
 }
 TeamService.View team(String name,String... members){return teams.create(new TeamService.Request(name,null,List.of(members),null));}
 de.ostms.lc.lc.domain.LcTask teamTask(TeamService.View t,String title){return taskService.create(lc.getId(),new LcTaskRequest(title,null,LocalDate.now().plusDays(3),t.id()),"boss");}

 @Test void teamNamesAreUniqueAndMembersMustBeActiveUsers(){
  team("Akkreditive","anna");
  assertThatThrownBy(()->team("akkreditive")).hasMessageContaining("bereits");
  assertThatThrownBy(()->team("Garantien","ghost")).hasMessageContaining("kein aktiver Nutzer");
  assertThatThrownBy(()->teams.create(new TeamService.Request(" ",null,null,null))).hasMessageContaining("Teamnamen");
  assertThat(team("Garantien","anna","ANNA","ben").members()).hasSize(2);
 }
 @Test void membersSeeUnclaimedTeamTasksOthersDoNot(){
  var t=team("Akkreditive","anna");teamTask(t,"Neue Akte prüfen");
  assertThat(inbox.inbox("anna")).hasSize(1);
  assertThat(inbox.inbox("anna").get(0).lcReference()).isEqualTo("LC-1");assertThat(inbox.inbox("anna").get(0).teamName()).isEqualTo("Akkreditive");
  assertThat(inbox.inbox("ben")).isEmpty();
 }
 @Test void claimAssignsOnceAndBlocksNonMembers(){
  var t=team("Akkreditive","anna","ben");var task=teamTask(t,"Prüfen");
  assertThatThrownBy(()->inbox.claim(task.getId(),"mallory")).isInstanceOf(AccessDeniedException.class);
  var claimed=inbox.claim(task.getId(),"anna");
  assertThat(claimed.getAssignedTo()).isEqualTo("anna");assertThat(claimed.getClaimedAt()).isNotNull();
  assertThatThrownBy(()->inbox.claim(task.getId(),"ben")).isInstanceOf(IllegalStateException.class).hasMessageContaining("anna");
  assertThat(inbox.inbox("ben").get(0).assignedTo()).isEqualTo("anna");
 }
 @Test void releaseReturnsTheTaskToTheInboxForTheClaimerOrAnAdministrator(){
  var t=team("Akkreditive","anna","ben");var task=teamTask(t,"Prüfen");inbox.claim(task.getId(),"anna");
  assertThatThrownBy(()->inbox.release(task.getId(),"ben",false)).isInstanceOf(AccessDeniedException.class);
  assertThat(inbox.release(task.getId(),"anna",false).getAssignedTo()).isNull();
  inbox.claim(task.getId(),"ben");
  assertThat(inbox.release(task.getId(),"chef",true).getAssignedTo()).isNull();
  assertThatThrownBy(()->inbox.release(task.getId(),"anna",false)).isInstanceOf(IllegalStateException.class);
 }
 @Test void completedTasksLeaveTheInboxAndCannotBeClaimed(){
  var t=team("Akkreditive","anna");var task=teamTask(t,"Prüfen");
  taskService.complete(lc.getId(),task.getId(),true);
  assertThat(inbox.inbox("anna")).isEmpty();
  assertThatThrownBy(()->inbox.claim(task.getId(),"anna")).isInstanceOf(IllegalStateException.class);
 }
 @Test void taskWithTeamAndAssigneeIsClaimedImmediatelyButOnlyForMembers(){
  var t=team("Akkreditive","anna");
  var direct=taskService.create(lc.getId(),new LcTaskRequest("Direkt","anna",null,t.id()),"boss");
  assertThat(direct.getAssignedTo()).isEqualTo("anna");assertThat(direct.getClaimedAt()).isNotNull();
  assertThatThrownBy(()->taskService.create(lc.getId(),new LcTaskRequest("X","ben",null,t.id()),"boss")).hasMessageContaining("nicht zum gewählten Team");
 }
 @Test void inactiveTeamsCannotReceiveTasksAndStopShowingUp(){
  var t=team("Alt","anna");teamTask(t,"Offen");
  teams.update(t.id(),new TeamService.Request("Alt",null,List.of("anna"),false));
  assertThat(inbox.inbox("anna")).isEmpty();
  assertThatThrownBy(()->teamTask(t,"Neu")).hasMessageContaining("nicht aktiv");
 }
 @Test void tasksWithoutTeamKeepWorkingAsBefore(){
  var plain=taskService.create(lc.getId(),new LcTaskRequest("Normal","anna",null),"boss");
  assertThat(plain.getTeamId()).isNull();assertThat(plain.getClaimedAt()).isNull();
  assertThatThrownBy(()->inbox.claim(plain.getId(),"anna")).isInstanceOf(IllegalArgumentException.class);
 }
}
