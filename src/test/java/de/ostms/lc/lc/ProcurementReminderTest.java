package de.ostms.lc.lc;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.domain.ProcurementItemStatus;
import de.ostms.lc.lc.repository.*;
import de.ostms.lc.lc.service.*;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.repository.TenantRepository;
import de.ostms.lc.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:procurementreminders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class ProcurementReminderTest {
 @Autowired AutomationRuleRepository ruleRepo;@Autowired TeamRepository teamRepo;@Autowired LcTaskRepository taskRepo;@Autowired LetterOfCreditRepository lcs;@Autowired TenantRepository tenants;
 final UserService users=Mockito.mock(UserService.class);
 final ProcurementPlanService plans=Mockito.mock(ProcurementPlanService.class);
 TeamService teams;AutoTaskService auto;ProcurementReminderService reminders;TeamService.View desk;LetterOfCredit lc;
 final LocalDate today=LocalDate.of(2026,11,2);

 ProcurementPlanService.Item item(String requirement,DocumentType type,boolean present,LocalDate due,ProcurementPlanService.Risk risk){
  return new ProcurementPlanService.Item(requirement,type,type.getDisplayName(),present,due,3,null,risk,"",ProcurementItemStatus.Status.NOT_STARTED,null);
 }
 void plan(ProcurementPlanService.Item... items){
  Mockito.when(plans.forLc(Mockito.eq(lc.getId()),Mockito.any())).thenReturn(new ProcurementPlanService.Plan(today.plusDays(20),null,20,ProcurementPlanService.Risk.OK,items.length,0,0,List.of(items)));
 }
 @BeforeEach void setUp(){
  var tenant=tenants.findById(Tenant.DEFAULT_ID).orElseGet(()->tenants.saveAndFlush(new Tenant()));
  tenant.updateProfile(false,true);tenants.saveAndFlush(tenant);
  Mockito.when(users.isAssignable(Mockito.anyString())).thenReturn(true);
  teams=new TeamService(teamRepo,users);auto=new AutoTaskService(ruleRepo,taskRepo,lcs,teams);
  reminders=new ProcurementReminderService(ruleRepo,taskRepo,lcs,teams,plans,tenants);
  desk=teams.create(new TeamService.Request("Beschaffung",null,List.of("anna"),null));
  var l=new LetterOfCredit();l.setReference("EXP-1");l.setStatus(LetterOfCreditStatus.ACTIVE);lc=lcs.saveAndFlush(l);
 }
 @Test void nothingWithoutAnEnabledRule(){
  plan(item("Certificate of origin",DocumentType.CERTIFICATE_OF_ORIGIN,false,today.minusDays(1),ProcurementPlanService.Risk.OVERDUE));
  assertThat(reminders.scan(today)).isZero();
 }
 @Test void dueMissingDocumentsGetOneTaskEachAndCloseWhenTheDocumentArrives(){
  auto.update(new AutoTaskService.RuleRequest("PROCUREMENT",true,desk.id(),10));
  var late=item("Certificate of origin",DocumentType.CERTIFICATE_OF_ORIGIN,false,today.minusDays(1),ProcurementPlanService.Risk.OVERDUE);
  plan(late,
   item("Packing list",DocumentType.PACKING_LIST,true,today.plusDays(1),ProcurementPlanService.Risk.DONE),
   item("Insurance",DocumentType.INSURANCE_CERTIFICATE,false,today.plusDays(30),ProcurementPlanService.Risk.OK));
  assertThat(reminders.scan(today)).isEqualTo(1);
  var open=taskRepo.openBySource("AUTO_PROCUREMENT");
  assertThat(open).hasSize(1);
  assertThat(open.get(0).getTitle()).contains("EXP-1").contains("Certificate of Origin").contains("überfällig").contains("01.11.2026");
  assertThat(reminders.scan(today)).isZero();
  plan(item("Certificate of origin",DocumentType.CERTIFICATE_OF_ORIGIN,true,today.minusDays(1),ProcurementPlanService.Risk.DONE));
  reminders.scan(today);
  assertThat(taskRepo.openBySource("AUTO_PROCUREMENT")).isEmpty();
 }
 @Test void bankOnlyTenantsGetNoProcurementTasks(){
  var tenant=tenants.findById(Tenant.DEFAULT_ID).orElseThrow();tenant.updateProfile(true,false);tenants.saveAndFlush(tenant);
  auto.update(new AutoTaskService.RuleRequest("PROCUREMENT",true,desk.id(),10));
  plan(item("Certificate of origin",DocumentType.CERTIFICATE_OF_ORIGIN,false,today.minusDays(1),ProcurementPlanService.Risk.OVERDUE));
  assertThat(reminders.scan(today)).isZero();
 }
}
