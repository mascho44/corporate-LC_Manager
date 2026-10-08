package de.ostms.lc.tenant;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.*;
import de.ostms.lc.tenant.service.*;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import de.ostms.lc.user.service.TenantAdministrationLock;
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
@DataJpaTest(properties={"app.security.totp-encryption-key=SyntheticMailQueueKeyForTestsOnly32", "app.mail.enabled=true","app.mail.from=synthetic@example.invalid","spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:sharedidentityaudit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({de.ostms.lc.user.service.InvitationMailQueue.class,de.ostms.lc.user.service.InvitationMailCipher.class,de.ostms.lc.user.service.PlatformInvitationMailListener.class,de.ostms.lc.user.service.PlatformInvitationMailService.class,de.ostms.lc.user.service.PlatformInvitationService.class,de.ostms.lc.user.service.PlatformAccountCreationService.class,de.ostms.lc.user.service.PlatformAdministrationService.class,PlatformTenantService.class,TenantSettingsService.class,TenantWorkspaceService.class,TenantSharedIdentityRoleService.class,TenantSharedIdentityAccessService.class,TenantMembershipService.class,TenantAdministrationLock.class,TenantSharedIdentityTransactionTest.Beans.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class TenantSharedIdentityTransactionTest {
 @org.springframework.test.context.bean.override.mockito.MockitoBean TenantInventoryStore inventory;
 @Test void archivalRollsBackOnAuditFailure(){var tenant=tenants.findById(tenantId).orElseThrow();tenant.setActive(false);tenants.saveAndFlush(tenant);failAudit();assertThatThrownBy(()->platformTenants.archive(tenantId,true,authentication)).isInstanceOf(IllegalStateException.class);var retained=tenants.findById(tenantId).orElseThrow();assertThat(retained.isArchived()).isFalse();assertThat(retained.isActive()).isFalse();}
 @TestConfiguration static class Beans {
  @Bean org.springframework.mail.javamail.JavaMailSender mailSender(){return mock(org.springframework.mail.javamail.JavaMailSender.class);}
  @Bean AuditService audit(){return mock(AuditService.class);}
  @Bean org.springframework.security.crypto.password.PasswordEncoder encoder(){return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);}
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
 @Autowired PlatformTenantService platformTenants;
 @Autowired TenantSettingsService settingsService;
 @Autowired de.ostms.lc.user.service.PlatformAdministrationService platform;
 @Autowired de.ostms.lc.user.service.PlatformAccountCreationService creation;
 @Autowired de.ostms.lc.user.service.PlatformInvitationService invitations;
 @Autowired PlatformInvitationRepository invitationTokens;
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
  jdbc.update("delete from platform_invitation");jdbc.update("delete from tenant_membership_suspension");jdbc.update("delete from tenant_membership");jdbc.update("delete from app_user");jdbc.update("delete from app_role_permission");jdbc.update("delete from app_role");jdbc.update("delete from tenant");
  tenants.saveAndFlush(new Tenant());tenantId=UUID.randomUUID();var tenant=new Tenant();ReflectionTestUtils.setField(tenant,"id",tenantId);ReflectionTestUtils.setField(tenant,"code","synthetic-shared");tenants.saveAndFlush(tenant);
  var home=role("Synthetic home",UserRole.VIEWER,Set.of());homeRoleId=home.getId();
  var actor=user("synthetic-shared-admin",role("Synthetic home admin",UserRole.ADMIN,Set.of(UserPermission.USER_MANAGE)));actor.setTotpEnabled(true);actor.setPlatformAdministrator(true);users.saveAndFlush(actor);seed(Tenant.DEFAULT_ID,actor.getId(),actor.getAssignedRole().getId());
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
 @Test void platformCreationRollsBackIdentityAndSuspensionOnAuditFailure(){
  var actor=users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();actor.setPlatformAdministrator(true);users.saveAndFlush(actor);var viewer=roles.findById(homeRoleId).orElseThrow();viewer.setSystemRole(true);roles.saveAndFlush(viewer);failAudit();long count=users.count();
  try(var scope=TenantContext.open(tenantId)){assertThatThrownBy(()->creation.create("synthetic-new","Synthetic new","new@example.invalid","Synthetic123!",authentication)).isInstanceOf(IllegalStateException.class).hasMessage("Synthetic audit unavailable");assertThat(TenantContext.currentId()).isEqualTo(tenantId);}
  assertThat(users.count()).isEqualTo(count);assertThat(users.existsByUsernameIgnoreCase("synthetic-new")).isFalse();assertThat(jdbc.queryForObject("select count(*) from tenant_membership_suspension",Long.class)).isZero();
 }
 @Test void platformSuspensionRollsBackOnAuditFailureAndPreservesCallerScope(){
  var actor=users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();actor.setPlatformAdministrator(true);users.saveAndFlush(actor);failAudit();
  try(var scope=TenantContext.open(tenantId)){assertThatThrownBy(()->platform.changeAccess(userId,false,authentication)).isInstanceOf(IllegalStateException.class);assertThat(TenantContext.currentId()).isEqualTo(tenantId);}
  assertThat(users.findById(userId).orElseThrow().isActive()).isTrue();
 }
 @Test void platformGrantRollsBackOnAuditFailure(){var actor=users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();actor.setPlatformAdministrator(true);users.saveAndFlush(actor);var target=users.findById(userId).orElseThrow();target.setTotpEnabled(true);users.saveAndFlush(target);failAudit();assertThatThrownBy(()->platform.changePlatformGrant(userId,true,authentication)).isInstanceOf(IllegalStateException.class);assertThat(users.findById(userId).orElseThrow().isPlatformAdministrator()).isFalse();}
 @Autowired de.ostms.lc.user.service.InvitationMailQueue mailQueue;
 @Autowired org.springframework.mail.javamail.JavaMailSender mailSender;
 @Autowired de.ostms.lc.user.service.InvitationMailCipher mailCipher;
 @Test void missingQueueKeyRollsBackInvitationAndIdentity(){
  var viewer=roles.findById(homeRoleId).orElseThrow();viewer.setSystemRole(true);roles.saveAndFlush(viewer);long count=users.count();Object original=ReflectionTestUtils.getField(mailCipher,"secret");
  try{ReflectionTestUtils.setField(mailCipher,"secret","");assertThatThrownBy(()->invitations.invite("synthetic-no-key","Synthetic no key","no-key@example.invalid",tenantId,replacementId,authentication)).isInstanceOf(IllegalStateException.class);assertThat(users.count()).isEqualTo(count);assertThat(invitationTokens.count()).isZero();assertThat(users.existsByUsernameIgnoreCase("synthetic-no-key")).isFalse();}
  finally{ReflectionTestUtils.setField(mailCipher,"secret",original);}
 }
 @Test void invitationQueueCommitsEncryptedPayloadAndRetriesPersistedFailure(){
  var viewer=roles.findById(homeRoleId).orElseThrow();viewer.setSystemRole(true);roles.saveAndFlush(viewer);
  var created=invitations.invite("synthetic-queued","Synthetic queued","queued@example.invalid",tenantId,replacementId,authentication);
  var queued=invitationTokens.findByUserId(created.userId()).orElseThrow();
  assertThat(queued.getEncryptedMailPayload()).isNotBlank().doesNotContain("queued@example.invalid");assertThat(queued.getNextDeliveryAttempt()).isNotNull();
  doThrow(new org.springframework.mail.MailSendException("Synthetic SMTP rejection")).when(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
  try{mailQueue.deliverNext();var failed=invitationTokens.findByUserId(created.userId()).orElseThrow();assertThat(failed.getDeliveryStatus()).isEqualTo("MAIL_RETRY");assertThat(failed.getDeliveryAttempts()).isEqualTo(1);assertThat(failed.getEncryptedMailPayload()).isNotBlank();
   reset(mailSender);jdbc.update("update platform_invitation set next_delivery_attempt=? where user_id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),created.userId());mailQueue.deliverNext();var sent=invitationTokens.findByUserId(created.userId()).orElseThrow();assertThat(sent.getDeliveryStatus()).isEqualTo("SENT");assertThat(sent.getDeliveryAttempts()).isEqualTo(2);assertThat(sent.getEncryptedMailPayload()).isNull();assertThat(sent.getNextDeliveryAttempt()).isNull();
  }finally{reset(mailSender);}
 }
 @Test void invitationIssuanceRollsBackIdentityTokenAndSuspensionOnAuditFailure(){
  var actor=users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();actor.setPlatformAdministrator(true);users.saveAndFlush(actor);var viewer=roles.findById(homeRoleId).orElseThrow();viewer.setSystemRole(true);roles.saveAndFlush(viewer);failAudit();long count=users.count();
  assertThatThrownBy(()->invitations.invite("synthetic-invited","Synthetic invited","invited@example.invalid",tenantId,replacementId,authentication)).isInstanceOf(IllegalStateException.class).hasMessage("Synthetic audit unavailable");assertThat(users.count()).isEqualTo(count);assertThat(invitationTokens.count()).isZero();assertThat(users.existsByUsernameIgnoreCase("synthetic-invited")).isFalse();assertThat(jdbc.queryForObject("select count(*) from tenant_membership_suspension",Long.class)).isZero();
 }
 @Test void invitationAcceptanceRollsBackPasswordActivationAndMembershipOnAuditFailure(){
  var actor=users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();actor.setPlatformAdministrator(true);users.saveAndFlush(actor);var viewer=roles.findById(homeRoleId).orElseThrow();viewer.setSystemRole(true);roles.saveAndFlush(viewer);var pending=creation.createPending("synthetic-pending","Synthetic pending","pending@example.invalid","Synthetic123!",authentication);var identity=users.findById(pending.id()).orElseThrow();String originalHash=identity.getPasswordHash();AppRole targetRole;try(var scope=TenantContext.open(tenantId)){targetRole=roles.findById(replacementId).orElseThrow();}
  String token="A".repeat(43);String stamp=de.ostms.lc.user.service.CredentialStamp.of(targetRole.getId()+":"+targetRole.getBaseRole()+":"+targetRole.getPermissions().stream().map(Enum::name).sorted().toList());invitationTokens.saveAndFlush(new PlatformInvitation(de.ostms.lc.user.service.CredentialStamp.of(token),identity.getId(),actor.getId(),tenantId,replacementId,stamp,de.ostms.lc.user.service.CredentialStamp.of(originalHash),identity.getEmail(),java.time.Instant.now().plusSeconds(3600)));failAudit();
  assertThatThrownBy(()->invitations.accept(token,"SyntheticNew123!")).isInstanceOf(IllegalStateException.class).hasMessage("Synthetic audit unavailable");identity=users.findById(pending.id()).orElseThrow();assertThat(identity.isActive()).isFalse();assertThat(identity.isInvitationPending()).isTrue();assertThat(identity.getPasswordHash()).isEqualTo(originalHash);assertThat(invitationTokens.count()).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from tenant_membership where tenant_id=? and user_id=?",Long.class,tenantId,identity.getId())).isZero();
 }
 @Test void platformSuspensionLeavesLocalMembershipsAndRolesUnchanged(){
  var actor=users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();actor.setPlatformAdministrator(true);users.saveAndFlush(actor);
  try(var scope=TenantContext.open(tenantId)){assertThat(platform.changeAccess(userId,false,authentication).active()).isFalse();assertThat(TenantContext.currentId()).isEqualTo(tenantId);}
  assertThat(selectedRole()).isEqualTo(roleId);assertThat(jdbc.queryForObject("select active from tenant_membership where tenant_id=? and user_id=?",Boolean.class,tenantId,userId)).isTrue();
 }
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
  var created=workspaceService.createForPlatform("synthetic-created","Synthetic created workspace","en",true,false,authentication);
  assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);assertThat(created.id()).isNotEqualTo(Tenant.DEFAULT_ID);
  var selected=workspaceService.select(created.id(),authentication);assertThat(selected.baseRole()).isEqualTo(UserRole.ADMIN);assertThat(selected.permissions()).contains(UserPermission.USER_MANAGE);
  assertThat(workspaceService.overview(authentication).workspaces()).extracting(TenantWorkspaceService.Workspace::id).contains(created.id());
  try(var scope=TenantContext.open(created.id())){
   var standard=roles.findAllByOrderByNameAsc();assertThat(standard).hasSize(3);assertThat(standard).allMatch(AppRole::isSystemRole);
   assertThat(standard).extracting(AppRole::getBaseRole).containsExactlyInAnyOrder(UserRole.ADMIN,UserRole.EDITOR,UserRole.VIEWER);
   for(var role:standard)assertThat(role.getPermissions()).containsExactlyInAnyOrderElementsOf(UserPermission.defaults(role.getBaseRole()));
  }
 }
 @Test void tenantSuspensionAndProfileAuditRollBackTogether(){
  failAudit();assertThatThrownBy(()->platformTenants.update(tenantId,false,false,true,authentication)).isInstanceOf(IllegalStateException.class);
  var target=tenants.findById(tenantId).orElseThrow();assertThat(target.isActive()).isTrue();assertThat(target.isBankEnabled()).isTrue();assertThat(target.isCorporateEnabled()).isFalse();
 }
 @Test void suspendedTenantDeniesLoginAndSelectionUntilReactivated(){
  platformTenants.update(tenantId,false,true,false,authentication);
  assertThatThrownBy(()->workspaceService.select(tenantId,authentication)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  assertThat(workspaceService.overview(authentication).workspaces()).extracting(TenantWorkspaceService.Workspace::id).doesNotContain(tenantId);
  platformTenants.update(tenantId,true,true,false,authentication);assertThat(workspaceService.select(tenantId,authentication).tenantId()).isEqualTo(tenantId);
 }
 @Test void newTenantStandardRolesDoNotChangeExistingTenantRoles(){
  var homeIds=roles.findAllByOrderByNameAsc().stream().map(AppRole::getId).toList();
  java.util.List<UUID> existingIds;try(var scope=TenantContext.open(tenantId)){existingIds=roles.findAllByOrderByNameAsc().stream().map(AppRole::getId).toList();}
  var created=workspaceService.createForPlatform("synthetic-roles","Synthetic roles workspace","en",true,false,authentication);
  assertThat(roles.findAllByOrderByNameAsc()).extracting(AppRole::getId).containsExactlyElementsOf(homeIds);
  try(var scope=TenantContext.open(tenantId)){assertThat(roles.findAllByOrderByNameAsc()).extracting(AppRole::getId).containsExactlyElementsOf(existingIds);}
  try(var scope=TenantContext.open(created.id())){assertThat(roles.findAllByOrderByNameAsc()).allMatch(r->r.getTenantId().equals(created.id()));}
 }
 @Test void creationAuditFailureRollsBackTenantRoleAndMembership(){
  long beforeTenants=tenants.count(),beforeRoles=roles.count();
  AuditService target=AopTestUtils.getUltimateTargetObject(audit);doThrow(new IllegalStateException("Synthetic audit unavailable")).when(target).recordInTransaction(any(),anyString(),anyString(),any(),anyString());
  assertThatThrownBy(()->workspaceService.createForPlatform("synthetic-rollback","Synthetic rollback workspace","en",true,false,authentication)).isInstanceOf(IllegalStateException.class);
  assertThat(tenants.count()).isEqualTo(beforeTenants);assertThat(roles.count()).isEqualTo(beforeRoles);assertThat(tenants.existsByCodeIgnoreCase("synthetic-rollback")).isFalse();assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);
 }
 @Test void unassignedTenantSelectionIsDeniedAndScopeRestored(){
  assertThatThrownBy(()->workspaceService.select(UUID.randomUUID(),authentication)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);
 }
 @Test void duplicateCodesAndForeignTenantCreationAreRejected(){
  assertThatThrownBy(()->workspaceService.createForPlatform("DEFAULT","Duplicate","en",true,false,authentication)).isInstanceOf(IllegalArgumentException.class);
  try(var scope=TenantContext.open(tenantId)){assertThatThrownBy(()->workspaceService.createForPlatform("blocked","Blocked","en",true,false,authentication)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
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
