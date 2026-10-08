package de.ostms.lc.user.api;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import de.ostms.lc.user.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:administrationaudit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({de.ostms.lc.tenant.api.TenantMembershipController.class,de.ostms.lc.tenant.service.TenantMembershipService.class,de.ostms.lc.tenant.service.TenantMembershipAdministrationService.class,RoleController.class,UserController.class,RoleService.class,UserService.class,IdentityCredentialService.class,TenantAdministrationLock.class,AdministrationAuditTransactionTest.Beans.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AdministrationAuditTransactionTest {
 @TestConfiguration static class Beans{
  @Bean AuditService audit(){return mock(AuditService.class);}
  @Bean PasswordEncoder passwords(){return mock(PasswordEncoder.class);}
 }
 @Autowired RoleController roleController;@Autowired UserController userController;
 @Autowired AppRoleRepository roles;@Autowired AppUserRepository users;@Autowired AuditService audit;
 @Autowired de.ostms.lc.tenant.repository.TenantRepository tenants;
 @Autowired TenantAdministrationLock administrationLock;
 @Autowired de.ostms.lc.tenant.api.TenantMembershipController membershipController;
 @Autowired de.ostms.lc.tenant.service.TenantMembershipAdministrationService membershipAdministration;
 @Autowired javax.sql.DataSource dataSource;
 @Autowired de.ostms.lc.tenant.repository.TenantMembershipSuspensionRepository suspensions;
 @org.junit.jupiter.api.BeforeEach void prepareTenant(){if(!tenants.existsById(de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID))tenants.saveAndFlush(new de.ostms.lc.tenant.domain.Tenant());}
 @Test void administrationLockRequiresTransactionAndExistingTenant(){
  assertThatThrownBy(()->administrationLock.acquire()).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
  try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(UUID.randomUUID())){
   assertThatThrownBy(()->roleController.create(new RoleRequest("Synthetic unavailable tenant",UserRole.VIEWER,Set.of()),UsernamePasswordAuthenticationToken.authenticated("synthetic-operator",null,List.of()))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }
 }
 @Test void simultaneousAdministratorRemovalsLeaveOneActiveAccount()throws Exception{
  AuditService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);reset(target);
  var role=new AppRole();role.setName("Synthetic parallel administrator");role.setBaseRole(UserRole.ADMIN);role.setPermissions(Set.of(UserPermission.USER_MANAGE));role=roles.saveAndFlush(role);
  var roleId=role.getId();var requests=new java.util.ArrayList<Map.Entry<UUID,UserRequest>>();
  for(int i=0;i<2;i++){var user=new AppUser();user.setUsername("synthetic-parallel-"+i);user.setDisplayName("Synthetic parallel user");user.setEmail("parallel"+i+"@example.invalid");user.setPasswordHash("synthetic-hash");user.setAssignedRole(role);user=users.saveAndFlush(user);requests.add(Map.entry(user.getId(),new UserRequest(user.getUsername(),user.getDisplayName(),user.getEmail(),null,roleId,false)));}
  var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try{
   var futures=requests.stream().map(entry->pool.submit(()->{start.await();try{userController.update(entry.getKey(),entry.getValue(),UsernamePasswordAuthenticationToken.authenticated("synthetic-operator",null,List.of()));return true;}catch(IllegalArgumentException rejected){return false;}})).toList();start.countDown();
   int succeeded=0;for(var result:futures)if(result.get(15,java.util.concurrent.TimeUnit.SECONDS))succeeded++;
   assertThat(succeeded).isEqualTo(1);assertThat(users.countByRoleAndActiveTrue(UserRole.ADMIN)).isEqualTo(1);
  }finally{pool.shutdownNow();for(var entry:requests)users.deleteById(entry.getKey());roles.deleteById(roleId);}
 }
 @Test void auditFailureRollsBackRoleAndUserMutations(){
  var role=new AppRole();role.setName("Synthetic audit role");role.setBaseRole(UserRole.EDITOR);role.setPermissions(Set.of(UserPermission.LC_EDIT));role=roles.saveAndFlush(role);
  var user=new AppUser();user.setUsername("synthetic-audit-user");user.setDisplayName("Synthetic user");user.setEmail("user@example.invalid");user.setPasswordHash("synthetic-secret-hash");user.setAssignedRole(role);user=users.saveAndFlush(user);
  var auth=UsernamePasswordAuthenticationToken.authenticated("synthetic-admin",null,List.of());var roleId=role.getId();var userId=user.getId();
  AuditService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
  doThrow(new IllegalStateException("Synthetic audit failure")).when(target).recordChangeInTransaction(any(),anyString(),anyString(),any(),anyString(),nullable(String.class),nullable(String.class));
  assertThatThrownBy(()->roleController.update(roleId,new RoleRequest("Changed name",UserRole.VIEWER,Set.of()),auth)).isInstanceOf(IllegalStateException.class);
  assertThat(roles.findById(roleId).orElseThrow().getName()).isEqualTo("Synthetic audit role");
  var request=new UserRequest(user.getUsername(),user.getDisplayName(),user.getEmail(),null,roleId,false);
  assertThatThrownBy(()->userController.update(userId,request,auth)).isInstanceOf(IllegalStateException.class);
  assertThat(users.findById(userId).orElseThrow().isActive()).isTrue();
  assertThatThrownBy(()->userController.delete(userId,auth)).isInstanceOf(IllegalStateException.class);assertThat(users.existsById(userId)).isTrue();
  reset(target);userController.update(userId,request,auth);
  verify(target).recordChangeInTransaction(eq(auth),eq("USER_UPDATED"),eq("USER"),eq(userId),anyString(),contains("\"active\":true"),contains("\"active\":false"));
  assertThat(users.findById(userId).orElseThrow().isActive()).isFalse();
 }
 @Test void snapshotsAreAllowlistedAndEscapeNames(){
  var user=new UserView(UUID.randomUUID(),"<script>\"synthetic</script>","Private display",UUID.randomUUID(),"Private role",UserRole.EDITOR,Set.of(),true,null,"private@example.invalid");
  var value=AdministrationAuditSnapshot.user(user);assertThat(value).contains("\\\"synthetic").doesNotContain("private@example.invalid","Private display","password","totp");
  var role=new RoleView(UUID.randomUUID(),"Synthetic role",UserRole.EDITOR,false,Set.of(UserPermission.LC_EDIT,UserPermission.AUDIT_VIEW));
  assertThat(AdministrationAuditSnapshot.role(role)).contains("\"permissions\":[\"AUDIT_VIEW\",\"LC_EDIT\"]");
 }
 @Test void membershipRoleMutationAndAuditShareOneTransaction(){
  AuditService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);reset(target);
  var original=new AppRole();original.setName("Synthetic membership original");original.setBaseRole(UserRole.EDITOR);original.setPermissions(Set.of(UserPermission.LC_EDIT));original=roles.saveAndFlush(original);
  var replacement=new AppRole();replacement.setName("Synthetic membership replacement");replacement.setBaseRole(UserRole.VIEWER);replacement.setPermissions(Set.of(UserPermission.DOCUMENT_REVIEW));replacement=roles.saveAndFlush(replacement);
  var user=new AppUser();user.setUsername("synthetic-membership-audit");user.setDisplayName("Synthetic identity");user.setEmail("membership@example.invalid");user.setPasswordHash("synthetic-unchanged-hash");user.setAssignedRole(original);user=users.saveAndFlush(user);
  var userId=user.getId();var originalId=original.getId();var replacementId=replacement.getId();
  var jdbc=new org.springframework.jdbc.core.JdbcTemplate(dataSource);
  jdbc.update("insert into tenant_membership(id,tenant_id,user_id,role_id,active) values (?,?,?,?,?)",UUID.randomUUID(),de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID,userId,originalId,true);
  var auth=UsernamePasswordAuthenticationToken.authenticated("synthetic-operator",null,List.of());
  try{
   assertThatThrownBy(()->membershipAdministration.getForAdministration(userId)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
   doThrow(new IllegalStateException("Synthetic audit failure")).when(target).recordChangeInTransaction(any(),anyString(),anyString(),any(),anyString(),nullable(String.class),nullable(String.class));
   assertThatThrownBy(()->membershipController.assignRole(userId,new de.ostms.lc.tenant.api.TenantMembershipController.RoleAssignment(replacementId),auth)).isInstanceOf(IllegalStateException.class);
   assertThat(users.findById(userId).orElseThrow().getAssignedRole().getId()).isEqualTo(originalId);
   reset(target);
   var result=membershipController.assignRole(userId,new de.ostms.lc.tenant.api.TenantMembershipController.RoleAssignment(replacementId),auth);
   assertThat(result.roleId()).isEqualTo(replacementId);assertThat(users.findById(userId).orElseThrow().getAssignedRole().getId()).isEqualTo(replacementId);
   var identity=users.findById(userId).orElseThrow();assertThat(identity.getPasswordHash()).isEqualTo("synthetic-unchanged-hash");assertThat(identity.getEmail()).isEqualTo("membership@example.invalid");assertThat(identity.isActive()).isTrue();
   verify(target).recordChangeInTransaction(eq(auth),eq("USER_MEMBERSHIP_ROLE_UPDATED"),eq("MEMBERSHIP"),eq(userId),anyString(),contains("LC_EDIT"),contains("DOCUMENT_REVIEW"));
   doThrow(new IllegalStateException("Synthetic access audit failure")).when(target).recordChangeInTransaction(any(),anyString(),anyString(),any(),anyString(),nullable(String.class),nullable(String.class));
   assertThatThrownBy(()->membershipController.changeAccess(userId,new de.ostms.lc.tenant.api.TenantMembershipController.AccessState(true),auth)).isInstanceOf(IllegalStateException.class);
   assertThat(suspensions.findByUserId(userId)).isEmpty();assertThat(users.findById(userId).orElseThrow().isActive()).isTrue();
   reset(target);
   var blocked=membershipController.changeAccess(userId,new de.ostms.lc.tenant.api.TenantMembershipController.AccessState(true),auth);
   assertThat(blocked.active()).isFalse();assertThat(blocked.identityActive()).isTrue();assertThat(suspensions.findByUserId(userId).orElseThrow().isSuspended()).isTrue();
   verify(target).recordChangeInTransaction(eq(auth),eq("USER_MEMBERSHIP_ACCESS_UPDATED"),eq("MEMBERSHIP"),eq(userId),anyString(),contains("\"suspended\":false"),contains("\"suspended\":true"));
   var enabled=membershipController.changeAccess(userId,new de.ostms.lc.tenant.api.TenantMembershipController.AccessState(false),auth);
   assertThat(enabled.active()).isTrue();assertThat(suspensions.findByUserId(userId).orElseThrow().isSuspended()).isFalse();assertThat(users.findById(userId).orElseThrow().getAssignedRole().getId()).isEqualTo(replacementId);
  }finally{reset(target);suspensions.findByUserId(userId).ifPresent(suspensions::delete);jdbc.update("delete from tenant_membership where user_id=?",userId);users.deleteById(userId);roles.deleteById(originalId);roles.deleteById(replacementId);}
 }
 @Test void concurrentMembershipSuspensionsLeaveOneAccessibleAdministrator()throws Exception{
  AuditService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);reset(target);
  var role=new AppRole();role.setName("Synthetic parallel membership administrators");role.setBaseRole(UserRole.ADMIN);role.setPermissions(Set.of(UserPermission.USER_MANAGE));role=roles.saveAndFlush(role);
  var roleId=role.getId();var ids=new ArrayList<UUID>();var jdbc=new org.springframework.jdbc.core.JdbcTemplate(dataSource);
  for(int i=0;i<2;i++){
   var user=new AppUser();user.setUsername("synthetic-membership-parallel-"+i);user.setDisplayName("Synthetic member");user.setEmail("member"+i+"@example.invalid");user.setPasswordHash("synthetic-hash");user.setAssignedRole(role);user=users.saveAndFlush(user);ids.add(user.getId());
   jdbc.update("insert into tenant_membership(id,tenant_id,user_id,role_id,active) values (?,?,?,?,?)",UUID.randomUUID(),de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID,user.getId(),roleId,true);
  }
  var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try{
   var futures=ids.stream().map(id->pool.submit(()->{start.await();try{membershipController.changeAccess(id,new de.ostms.lc.tenant.api.TenantMembershipController.AccessState(true),UsernamePasswordAuthenticationToken.authenticated("synthetic-operator",null,List.of()));return true;}catch(IllegalArgumentException denied){return false;}})).toList();
   start.countDown();int succeeded=0;for(var result:futures)if(result.get(20,java.util.concurrent.TimeUnit.SECONDS))succeeded++;
   assertThat(succeeded).isEqualTo(1);assertThat(users.countByRoleAndActiveTrue(UserRole.ADMIN)).isEqualTo(1);
  }finally{pool.shutdownNow();for(var id:ids){suspensions.findByUserId(id).ifPresent(suspensions::delete);jdbc.update("delete from tenant_membership where user_id=?",id);users.deleteById(id);}roles.deleteById(roleId);reset(target);}
 }
}
