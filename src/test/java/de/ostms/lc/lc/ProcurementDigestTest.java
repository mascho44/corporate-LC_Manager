package de.ostms.lc.lc;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.domain.ProcurementItemStatus;
import de.ostms.lc.lc.repository.*;
import de.ostms.lc.lc.service.*;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.repository.TenantRepository;
import de.ostms.lc.user.domain.AppUser;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:procurementdigest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class ProcurementDigestTest {
 @Autowired AutomationRuleRepository ruleRepo;@Autowired TeamRepository teamRepo;@Autowired LcTaskRepository taskRepo;@Autowired LetterOfCreditRepository lcs;@Autowired TenantRepository tenants;@Autowired ProcurementDigestLogRepository digests;
 final UserService userService=Mockito.mock(UserService.class);final ProcurementPlanService plans=Mockito.mock(ProcurementPlanService.class);
 final AppUserRepository users=Mockito.mock(AppUserRepository.class);final JavaMailSender mail=Mockito.mock(JavaMailSender.class);
 TeamService teams;AutoTaskService auto;TeamService.View desk;LetterOfCredit lc;
 final LocalDate today=LocalDate.of(2026,11,2);

 ProcurementDigestService service(boolean enabled){return new ProcurementDigestService(ruleRepo,lcs,teams,plans,tenants,users,digests,mail,enabled,"lc@example.com","https://lc.example.com");}
 AppUser user(String name,String email){var u=new AppUser();u.setUsername(name);u.setEmail(email);return u;}
 ProcurementPlanService.Item item(String name,DocumentType type,LocalDate due,ProcurementPlanService.Risk risk){
  return new ProcurementPlanService.Item(name,type,type.getDisplayName(),false,due,3,null,risk,"",ProcurementItemStatus.Status.NOT_STARTED,null);
 }
 @BeforeEach void setUp(){
  var tenant=tenants.findById(Tenant.DEFAULT_ID).orElseGet(()->tenants.saveAndFlush(new Tenant()));
  tenant.updateProfile(false,true);tenants.saveAndFlush(tenant);
  Mockito.when(userService.isAssignable(Mockito.anyString())).thenReturn(true);
  teams=new TeamService(teamRepo,userService);auto=new AutoTaskService(ruleRepo,taskRepo,lcs,teams);
  desk=teams.create(new TeamService.Request("Beschaffung",null,List.of("anna","ben"),null));
  Mockito.when(users.findAssignableMembershipUsers()).thenReturn(List.of(user("anna","anna@example.com"),user("ben","ben@example.com"),user("cora","cora@example.com")));
  var l=new LetterOfCredit();l.setReference("EXP-1");l.setStatus(LetterOfCreditStatus.ACTIVE);lc=lcs.saveAndFlush(l);
  Mockito.when(plans.forLc(Mockito.eq(lc.getId()),Mockito.any())).thenReturn(new ProcurementPlanService.Plan(today.plusDays(8),null,8,ProcurementPlanService.Risk.SOON,2,1,0,List.of(
   item("Certificate of origin",DocumentType.CERTIFICATE_OF_ORIGIN,today.minusDays(1),ProcurementPlanService.Risk.OVERDUE),
   item("Insurance",DocumentType.INSURANCE_CERTIFICATE,today.plusDays(30),ProcurementPlanService.Risk.OK))));
 }
 @Test void unassignedLcGoesToTheTeamOnceADay(){
  auto.update(new AutoTaskService.RuleRequest("PROCUREMENT",true,desk.id(),10));
  var digest=service(true);
  assertThat(digest.sendDaily(today)).isEqualTo(2);
  var captor=ArgumentCaptor.forClass(SimpleMailMessage.class);
  Mockito.verify(mail,Mockito.times(2)).send(captor.capture());
  assertThat(captor.getAllValues()).extracting(m->m.getTo()[0]).containsExactlyInAnyOrder("anna@example.com","ben@example.com");
  var message=captor.getAllValues().get(0);
  assertThat(message.getSubject()).contains("1 Dokument braucht");
  assertThat(message.getText()).contains("LC EXP-1").contains("ÜBERFÄLLIG: Certificate of Origin").contains("01.11.2026").contains("10.11.2026").doesNotContain("Insurance");
  assertThat(digest.sendDaily(today)).isZero();
  Mockito.verify(mail,Mockito.times(2)).send(Mockito.any(SimpleMailMessage.class));
 }
 @Test void assignedLcGoesOnlyToItsAssignee(){
  auto.update(new AutoTaskService.RuleRequest("PROCUREMENT",true,desk.id(),10));
  lc.setAssignedTo("cora");lcs.saveAndFlush(lc);
  assertThat(service(true).sendDaily(today)).isEqualTo(1);
  var captor=ArgumentCaptor.forClass(SimpleMailMessage.class);
  Mockito.verify(mail).send(captor.capture());
  assertThat(captor.getValue().getTo()[0]).isEqualTo("cora@example.com");
 }
 @Test void nothingWithoutRuleMailSettingOrCorporateProfile(){
  assertThat(service(true).sendDaily(today)).isZero();
  auto.update(new AutoTaskService.RuleRequest("PROCUREMENT",true,desk.id(),10));
  assertThat(service(false).sendDaily(today)).isZero();
  var tenant=tenants.findById(Tenant.DEFAULT_ID).orElseThrow();tenant.updateProfile(true,false);tenants.saveAndFlush(tenant);
  assertThat(service(true).sendDaily(today)).isZero();
  Mockito.verifyNoInteractions(mail);
 }
}
