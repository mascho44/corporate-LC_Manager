package de.corporate.lc.tenant;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.*;
import de.corporate.lc.tenant.service.*;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.*;
import de.corporate.lc.user.service.TenantAdministrationLock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.*;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring transactions/repositories; the PostgreSQL function itself is tested separately. */
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:sharedidentityaudit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({TenantSettingsService.class,TenantWorkspaceService.class,TenantSharedIdentityRoleService.class,TenantSharedIdentityAccessService.class,TenantMembershipService.class,TenantAdministrationLock.class,TenantSharedIdentityTransactionTest.Beans.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class TenantSharedIdentityTransactionTest {
 @TestConfiguration static class Beans {
  @Bean AuditService audit(){return mock(AuditService.class);}
  @Bean TenantMembershipProvisioningStore store(EntityManager em,DataSource source){return new TransactionalTestStore(em,source);}
 }
 static class TransactionalTestStore extends TenantMembershipProvisioningStore {
  private final JdbcTemplate jdbc;
  TransactionalTestStore(EntityManager em,DataSource source){super(em);jdbc=new JdbcTemplate(source);}
  @Override @Transactional(propagation=Propagation.MANDATORY)
  public void updateRole(UUID tenantId,UUID userId,UUID roleId){
   if(jdbc.update("update tenant_membership set role_id=? where tenant_id=? and user_id=?",roleId,tenantId,userId)!=1)throw new IllegalStateException("Fixture membership missing");
  }
  @Override @Transactional(propagation=Propagation.MANDATORY)
  public UUID create(UUID tenantId,UUID userId,UUID roleId){var id=UUID.randomUUID();jdbc.update("insert into tenant_membership(id,tenant_id,user_id,role_id,active) values(?,?,?,?,true)",id,tenantId,userId,roleId);return id;}
 }
 @Autowired TenantSharedIdentityRoleService roleService;
 @Autowired TenantSharedIdentityAccessService accessService;
 @Autowired TenantWorkspaceService workspaceService;
 @Autowired TenantSettingsService settingsService;
 @Autowired TenantRepository tenants;
 @Autowired AppRoleRepository roles;
 @Autowired AppUserRepository users;
 @Autowired AuditService audit;
 @Autowired DataSource source;
 UUID tenantId,userId,roleId,replacementId,homeRoleId;
 JdbcTemplate jdbc;
 final UsernamePasswordAuthenticationToken authentication=UsernamePasswordAuthenticationToken.authenticated("synthetic-shared-admin",null,List.of());
 @BeforeEach void setup(){
  AuditService target=AopTestUtils.getUltimateTargetObject(audit);reset(target);jdbc=new JdbcTemplate(source);
  jdbc.update("delete from tenant_membership_suspension");jdbc.update("delete from tenant_membership");jdbc.update("delete from app_user");jdbc.update("delete from app_role_permission");jdbc.update("delete from app_role");jdbc.update("delete from tenant");
  tenants.saveAndFlush(new Tenant());tenantId=UUID.randomUUID();var tenant=new Tenant();ReflectionTestUtils.setField(tenant,"id",tenantId);ReflectionTestUtils.setField(tenant,"code","synthetic-shared");tenants.saveAndFlush(tenant);
  var home=role("Synthetic home",UserRole.VIEWER,Set.of());homeRoleId=home.getId();
  var actor=user("synthetic-shared-admin",role("Synthetic home admin",UserRole.ADMIN,Set.of(UserPermission.USER_MANAGE)));actor.setTotpEnabled(true);users.saveAndFlush(actor);seed(Tenant.DEFAULT_ID,actor.getId(),actor.getAssignedRole().getId());
  var identity=user("synthetic-shared-target",home);userId=identity.getId();
  seed(Tenant.DEFAULT_ID,userId,homeRoleId);
  try(var scope=TenantContext.open(tenantId)){
   var admin=role("Synthetic selected admin",UserRole.ADMIN,Set.of(UserPermission.USER_MANAGE));seed(tenantId,actor.getId(),admin.getId());
   var original=role("Synthetic selected editor",UserRole.EDITOR,Set.of(UserPermission.LC_EDIT));roleId=original.getId();seed(tenantId,userId,roleId);
   replacementId=role("Synthetic selected reviewer",UserRole.VIEWER,Set.of(UserPermission.DOCUMENT_REVIEW)).getId();
  }
 }
 AppRole role(String name,UserRole base,Set<UserPermission> permissions){var role=new AppRole();role.setName(name);role.setBaseRole(base);role.setPermissions(permissions);return roles.saveAndFlush(role);}
 AppUser user(String username,AppRole home){var user=new AppUser();user.setUsername(username);user.setDisplayName("Synthetic identity");user.setEmail(username+"@example.invalid");user.setPasswordHash("synthetic-unchanged-hash");user.setAssignedRole(home);return users.saveAndFlush(user);}
 void seed(UUID tenant,UUID user,UUID role){jdbc.update("insert into tenant_membership(id,tenant_id,user_id,role_id,active) values(?,?,?,?,true)",UUID.randomUUID(),tenant,user,role);}
 UUID selectedRole(){return jdbc.queryForObject("select role_id from tenant_membership where tenant_id=? and user_id=?",UUID.class,tenantId,userId);}
 void failAudit(){AuditService target=AopTestUtils.getUltimateTargetObject(audit);doThrow(new IllegalStateException("Synthetic audit unavailable")).when(target).recordChangeInTransaction(any(),anyString(),anyString(),any(),anyString(),nullable(String.class),nullable(String.class));}
 @Test void auditFailureRollsBackNativeRoleUpdate(){
  failAudit();try(var scope=TenantContext.open(tenantId)){
   assertThatThrownBy(()->roleService.changeRole(userId,replacementId,authentication)).isInstanceOf(IllegalStateException.class);
  }
  assertThat(selectedRole()).isEqualTo(roleId);assertThat(users.findById(userId).orElseThrow().getAssignedRole().getId()).isEqualTo(homeRoleId);
 }
 @Test void successfulRoleUpdatePreservesHomeMembershipAndCredentials(){
  try(var scope=TenantContext.open(tenantId)){assertThat(roleService.changeRole(userId,replacementId,authentication).roleId()).isEqualTo(replacementId);}
  assertThat(selectedRole()).isEqualTo(replacementId);
  assertThat(jdbc.queryForObject("select role_id from tenant_membership where tenant_id=? and user_id=?",UUID.class,Tenant.DEFAULT_ID,userId)).isEqualTo(homeRoleId);
  var identity=users.findById(userId).orElseThrow();assertThat(identity.getPasswordHash()).isEqualTo("synthetic-unchanged-hash");assertThat(identity.isActive()).isTrue();assertThat(identity.getAssignedRole().getId()).isEqualTo(homeRoleId);
  AuditService target=AopTestUtils.getUltimateTargetObject(audit);verify(target).recordChangeInTransaction(eq(authentication),eq("USER_MEMBERSHIP_ROLE_UPDATED"),eq("MEMBERSHIP"),eq(userId),anyString(),contains("LC_EDIT"),contains("DOCUMENT_REVIEW"));
 }
 @Test void auditFailureRollsBackNewSuspension(){
  failAudit();try(var scope=TenantContext.open(tenantId)){
   assertThatThrownBy(()->accessService.changeAccess(userId,true,authentication)).isInstanceOf(IllegalStateException.class);
  }
  assertThat(jdbc.queryForObject("select count(*) from tenant_membership_suspension where tenant_id=? and user_id=?",Long.class,tenantId,userId)).isZero();assertThat(users.findById(userId).orElseThrow().isActive()).isTrue();
 }
 @Test void suspensionAndRoleUpdateLeaveAnotherTenantUnchanged(){
  UUID otherId=UUID.randomUUID();var other=new Tenant();ReflectionTestUtils.setField(other,"id",otherId);ReflectionTestUtils.setField(other,"code","synthetic-independent");tenants.saveAndFlush(other);
  UUID otherRole;
  try(var scope=TenantContext.open(otherId)){otherRole=role("Synthetic independent reviewer",UserRole.VIEWER,Set.of(UserPermission.DOCUMENT_REVIEW)).getId();seed(otherId,userId,otherRole);}
  try(var scope=TenantContext.open(tenantId)){
   assertThat(accessService.changeAccess(userId,true,authentication).suspended()).isTrue();
   var changed=roleService.changeRole(userId,replacementId,authentication);assertThat(changed.suspended()).isTrue();assertThat(changed.active()).isFalse();
  }
  assertThat(jdbc.queryForObject("select role_id from tenant_membership where tenant_id=? and user_id=?",UUID.class,otherId,userId)).isEqualTo(otherRole);
  assertThat(jdbc.queryForObject("select count(*) from tenant_membership_suspension where tenant_id=? and user_id=?",Long.class,otherId,userId)).isZero();
  assertThat(users.findById(userId).orElseThrow().isActive()).isTrue();
 }
 @Test void tenantCreationAssignsInitialAdministratorAndRestoresScope(){
  var created=workspaceService.create("synthetic-created","Synthetic created workspace","en",true,false,authentication);
  assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);assertThat(created.id()).isNotEqualTo(Tenant.DEFAULT_ID);
  var selected=workspaceService.select(created.id(),authentication);assertThat(selected.baseRole()).isEqualTo(UserRole.ADMIN);assertThat(selected.permissions()).contains(UserPermission.USER_MANAGE);
  assertThat(workspaceService.overview(authentication).workspaces()).extracting(TenantWorkspaceService.Workspace::id).contains(created.id());
 }
 @Test void creationAuditFailureRollsBackTenantRoleAndMembership(){
  long beforeTenants=tenants.count(),beforeRoles=roles.count();
  AuditService target=AopTestUtils.getUltimateTargetObject(audit);doThrow(new IllegalStateException("Synthetic audit unavailable")).when(target).recordInTransaction(any(),anyString(),anyString(),any(),anyString());
  assertThatThrownBy(()->workspaceService.create("synthetic-rollback","Synthetic rollback workspace","en",true,false,authentication)).isInstanceOf(IllegalStateException.class);
  assertThat(tenants.count()).isEqualTo(beforeTenants);assertThat(roles.count()).isEqualTo(beforeRoles);assertThat(tenants.existsByCodeIgnoreCase("synthetic-rollback")).isFalse();assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);
 }
 @Test void unassignedTenantSelectionIsDeniedAndScopeRestored(){
  assertThatThrownBy(()->workspaceService.select(UUID.randomUUID(),authentication)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);
 }
 @Test void duplicateCodesAndForeignTenantCreationAreRejected(){
  assertThatThrownBy(()->workspaceService.create("DEFAULT","Duplicate","en",true,false,authentication)).isInstanceOf(IllegalArgumentException.class);
  try(var scope=TenantContext.open(tenantId)){assertThatThrownBy(()->workspaceService.create("blocked","Blocked","en",true,false,authentication)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
 }
 @Test void settingsUpdateChangesOnlySelectedTenantAndAuditsValues(){
  try(var scope=TenantContext.open(tenantId)){var changed=settingsService.update("Synthetic renamed","de",authentication);assertThat(changed.name()).isEqualTo("Synthetic renamed");assertThat(changed.defaultLanguage()).isEqualTo("de");assertThat(changed.code()).isEqualTo("synthetic-shared");}
  assertThat(tenants.findById(Tenant.DEFAULT_ID).orElseThrow().getName()).isEqualTo("Default tenant");
  verify(AopTestUtils.<AuditService>getUltimateTargetObject(audit)).recordChangeInTransaction(eq(authentication),eq("TENANT_SETTINGS_UPDATED"),eq("TENANT"),eq(tenantId),anyString(),contains("Default tenant"),contains("Synthetic renamed"));
 }
 @Test void settingsAuditFailureRollsBackChanges(){
  failAudit();try(var scope=TenantContext.open(tenantId)){assertThatThrownBy(()->settingsService.update("Synthetic rejected","de",authentication)).isInstanceOf(IllegalStateException.class);}
  assertThat(tenants.findById(tenantId).orElseThrow().getName()).isEqualTo("Default tenant");
 }
 @Test void ordinaryMembershipCannotChangeSettings(){
  var ordinary=UsernamePasswordAuthenticationToken.authenticated("synthetic-shared-target",null,List.of());
  try(var scope=TenantContext.open(tenantId)){assertThat(settingsService.get(ordinary).editingEnabled()).isFalse();assertThatThrownBy(()->settingsService.update("Blocked","de",ordinary)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
 }
 @Test void invalidSettingsAreRejectedWithoutAuditOrChanges(){
  try(var scope=TenantContext.open(tenantId)){for(String name:List.of("","bad\nname","x".repeat(256)))assertThatThrownBy(()->settingsService.update(name,"en",authentication)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->settingsService.update("Synthetic","unknown",authentication)).isInstanceOf(IllegalArgumentException.class);}
  verifyNoInteractions(AopTestUtils.<AuditService>getUltimateTargetObject(audit));
 }
}
