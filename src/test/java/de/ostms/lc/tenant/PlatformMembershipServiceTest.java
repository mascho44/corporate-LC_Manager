package de.ostms.lc.tenant;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.*;
import de.ostms.lc.tenant.service.*;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.AppRoleRepository;
import de.ostms.lc.user.service.PlatformAdministrationService;
import de.ostms.lc.user.service.TenantAdministrationLock;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformMembershipServiceTest {
 final PlatformAdministrationService platform=mock(PlatformAdministrationService.class);final TenantRepository tenants=mock(TenantRepository.class);final AppRoleRepository roles=mock(AppRoleRepository.class);
 final TenantMembershipRepository memberships=mock(TenantMembershipRepository.class);final TenantMembershipSuspensionRepository suspensions=mock(TenantMembershipSuspensionRepository.class);
 final TenantMembershipService access=mock(TenantMembershipService.class);final TenantMembershipProvisioningStore store=mock(TenantMembershipProvisioningStore.class);
 final TenantAdministrationLock lock=mock(TenantAdministrationLock.class);final AuditService audit=mock(AuditService.class);
 final PlatformMembershipService service=new PlatformMembershipService(platform,tenants,roles,memberships,suspensions,access,store,lock,audit);
 final TestingAuthenticationToken auth=new TestingAuthenticationToken("platform","x");
 final UUID tenantId=UUID.randomUUID(),userId=UUID.randomUUID(),oldRole=UUID.randomUUID(),newRole=UUID.randomUUID();

 private AppUser user(UUID home){var user=mock(AppUser.class);when(user.getTenantId()).thenReturn(home);when(user.getUsername()).thenReturn("shared.user");when(user.isActive()).thenReturn(true);return user;}
 private AppRole role(UUID id,UserRole base){var role=mock(AppRole.class);when(role.getId()).thenReturn(id);when(role.getName()).thenReturn("Role");when(role.getBaseRole()).thenReturn(base);when(role.getTenantId()).thenReturn(tenantId);when(role.getPermissions()).thenReturn(Set.of());return role;}
 private TenantMembership membership(AppUser user,AppRole role){var m=mock(TenantMembership.class);when(m.getTenantId()).thenReturn(tenantId);when(m.getUser()).thenReturn(user);when(m.getRole()).thenReturn(role);when(m.isActive()).thenReturn(true);return m;}
 private TenantMembershipService.Membership view(UUID role,boolean suspended){return new TenantMembershipService.Membership(tenantId,userId,"shared.user",role,"Role",Set.of(),!suspended,suspended,true);}
 private void tenantExists(){var tenant=new Tenant("acme","Acme","en",true,true);when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));}

 @Test void withoutLivePlatformAccessNothingIsTouched(){
  doThrow(new AccessDeniedException("no")).when(platform).verifyLiveAccess(auth);
  assertThatThrownBy(()->service.changeRole(tenantId,userId,newRole,auth)).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->service.changeAccess(tenantId,userId,true,auth)).isInstanceOf(AccessDeniedException.class);
  verifyNoInteractions(store,suspensions,audit,memberships);
 }
 @Test void bootstrapTenantAndHomeIdentitiesAreRefused(){
  assertThatThrownBy(()->service.changeRole(Tenant.DEFAULT_ID,userId,newRole,auth)).isInstanceOf(AccessDeniedException.class);
  tenantExists();var homeUser=user(tenantId);var homeRole=role(oldRole,UserRole.USER);var homeMembership=membership(homeUser,homeRole);when(memberships.findByUserId(userId)).thenReturn(Optional.of(homeMembership));
  assertThatThrownBy(()->service.changeRole(tenantId,userId,newRole,auth)).isInstanceOf(AccessDeniedException.class).hasMessageContaining("Home identities");
  assertThatThrownBy(()->service.changeAccess(tenantId,userId,true,auth)).isInstanceOf(AccessDeniedException.class);
  verify(store,never()).updateRole(any(),any(),any());verify(suspensions,never()).save(any());
 }
 @Test void lastActiveAdministratorCannotBeDemotedOrSuspended(){
  tenantExists();var admin=role(oldRole,UserRole.ADMIN);var viewer=role(newRole,UserRole.VIEWER);
  var sharedUser=user(UUID.randomUUID());var adminMembership=membership(sharedUser,admin);when(memberships.findByUserId(userId)).thenReturn(Optional.of(adminMembership));when(roles.findById(newRole)).thenReturn(Optional.of(viewer));
  when(access.forUser(userId)).thenReturn(Optional.of(view(oldRole,false)));when(memberships.countAccessibleAdministrators()).thenReturn(1L);
  assertThatThrownBy(()->service.changeRole(tenantId,userId,newRole,auth)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("last active administrator");
  assertThatThrownBy(()->service.changeAccess(tenantId,userId,true,auth)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("last active administrator");
  verify(store,never()).updateRole(any(),any(),any());
 }
 @Test void roleChangeIsStoredAndAuditedInsideTheTenant(){
  tenantExists();var viewer=role(newRole,UserRole.VIEWER);var sharedUser=user(UUID.randomUUID());var userRole=role(oldRole,UserRole.USER);var userMembership=membership(sharedUser,userRole);
  when(memberships.findByUserId(userId)).thenReturn(Optional.of(userMembership));when(roles.findById(newRole)).thenReturn(Optional.of(viewer));
  when(access.forUser(userId)).thenReturn(Optional.of(view(oldRole,false)));
  var result=service.changeRole(tenantId,userId,newRole,auth);
  assertThat(result.roleId()).isEqualTo(newRole);
  verify(store).updateRole(tenantId,userId,newRole);verify(audit).recordChangeInTransaction(eq(auth),eq("PLATFORM_MEMBERSHIP_ROLE_UPDATED"),eq("MEMBERSHIP"),eq(userId),anyString(),anyString(),anyString());
  assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);
 }
 @Test void accessChangeSuspendsAndAudits(){
  tenantExists();var sharedUser=user(UUID.randomUUID());var userRole=role(oldRole,UserRole.USER);var userMembership=membership(sharedUser,userRole);when(memberships.findByUserId(userId)).thenReturn(Optional.of(userMembership));
  when(access.forUser(userId)).thenReturn(Optional.of(view(oldRole,false)));when(suspensions.findByUserId(userId)).thenReturn(Optional.empty());
  var result=service.changeAccess(tenantId,userId,true,auth);
  assertThat(result.suspended()).isTrue();assertThat(result.active()).isFalse();
  verify(suspensions).save(any());verify(audit).recordChangeInTransaction(eq(auth),eq("PLATFORM_MEMBERSHIP_ACCESS_UPDATED"),eq("MEMBERSHIP"),eq(userId),anyString(),anyString(),anyString());
 }
}
