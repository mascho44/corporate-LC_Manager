package de.corporate.lc.tenant;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.TenantMembershipProvisioningStore;
import de.corporate.lc.tenant.service.*;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.*;
import de.corporate.lc.user.service.TenantAdministrationLock;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantMembershipProvisioningTest {
 final AppUserRepository users=mock(AppUserRepository.class);
 final AppRoleRepository roles=mock(AppRoleRepository.class);
 final TenantMembershipService memberships=mock(TenantMembershipService.class);
 final TenantMembershipProvisioningStore store=mock(TenantMembershipProvisioningStore.class);
 final TenantAdministrationLock lock=mock(TenantAdministrationLock.class);
 final AuditService audit=mock(AuditService.class);
 final TenantMembershipProvisioningService service=new TenantMembershipProvisioningService(users,roles,memberships,store,lock,audit);
 final UUID tenant=UUID.randomUUID(),userId=UUID.randomUUID(),actorId=UUID.randomUUID(),roleId=UUID.randomUUID();
 final AppUser actor=new AppUser(),user=new AppUser();final AppRole role=new AppRole();
 final UsernamePasswordAuthenticationToken authentication=new UsernamePasswordAuthenticationToken("admin","",List.of());
 TenantContext.Scope scope;
 @BeforeEach void setup(){
  ReflectionTestUtils.setField(actor,"id",actorId);ReflectionTestUtils.setField(user,"id",userId);
  user.setUsername("existing");user.setPasswordHash("unchanged-hash");
  scope=TenantContext.open(tenant);role.setName("Viewer");role.setBaseRole(UserRole.VIEWER);ReflectionTestUtils.setField(role,"id",roleId);ReflectionTestUtils.setField(role,"tenantId",tenant);
  when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(actor));
  when(users.findByUsernameIgnoreCase("existing")).thenReturn(Optional.of(user));
  when(roles.findById(roleId)).thenReturn(Optional.of(role));
  when(memberships.requireActiveAccess(actorId)).thenReturn(new TenantMembershipService.Access(tenant,actorId,UUID.randomUUID(),roleId,UserRole.ADMIN,Set.of(UserPermission.USER_MANAGE)));
  when(memberships.forUser(userId)).thenReturn(Optional.empty());
 }
 @AfterEach void cleanup(){scope.close();}
 @Test void createsScopedMembershipAndAllowlistedAuditWithoutChangingIdentity(){
  var result=service.assignExistingIdentity("existing",roleId,authentication);
  assertThat(result.tenantId()).isEqualTo(tenant);assertThat(user.getPasswordHash()).isEqualTo("unchanged-hash");assertThat(user.getTenantId()).isEqualTo(Tenant.DEFAULT_ID);
  verify(store).create(tenant,userId,roleId);
  verify(audit).recordChangeInTransaction(eq(authentication),eq("USER_MEMBERSHIP_CREATED"),eq("MEMBERSHIP"),eq(userId),anyString(),isNull(),argThat(json->json.contains("existing")&&!json.contains("unchanged-hash")));
  verify(users,never()).save(any());
 }
 @Test void bootstrapGateRejectsBeforeIdentityLookup(){try(var ignored=TenantContext.open(Tenant.DEFAULT_ID)){
  assertThatThrownBy(()->service.assignExistingIdentity("existing",roleId,authentication)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(users,store,audit);
 }}
 @Test void unauthenticatedCallerCannotProvision(){assertThatThrownBy(()->service.assignExistingIdentity("existing",roleId,null)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(users,store,audit);}
 @Test void liveMembershipPermissionIsRequired(){
  when(memberships.requireActiveAccess(actorId)).thenReturn(new TenantMembershipService.Access(tenant,actorId,UUID.randomUUID(),roleId,UserRole.VIEWER,Set.of()));
  assertThatThrownBy(()->service.assignExistingIdentity("existing",roleId,authentication)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(store,audit);
 }
 @Test void foreignRoleIsRejected(){ReflectionTestUtils.setField(role,"tenantId",Tenant.DEFAULT_ID);
  assertThatThrownBy(()->service.assignExistingIdentity("existing",roleId,authentication)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(store,audit);
 }
 @Test void inactiveIdentityIsRejected(){user.setActive(false);
  assertThatThrownBy(()->service.assignExistingIdentity("existing",roleId,authentication)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(store,audit);
 }
 @Test void duplicateMembershipIsRejected(){when(memberships.forUser(userId)).thenReturn(Optional.of(new TenantMembershipService.Membership(tenant,userId,"existing",roleId,"Viewer",Set.of(),true)));
  assertThatThrownBy(()->service.assignExistingIdentity("existing",roleId,authentication)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(store,audit);
 }
 @Test void auditFailureIsNotSwallowed(){doThrow(new IllegalStateException("Audit unavailable")).when(audit).recordChangeInTransaction(any(),anyString(),anyString(),any(),anyString(),isNull(),anyString());
  assertThatThrownBy(()->service.assignExistingIdentity("existing",roleId,authentication)).isInstanceOf(IllegalStateException.class);
 }
}
