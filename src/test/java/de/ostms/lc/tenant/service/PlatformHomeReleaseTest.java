package de.ostms.lc.tenant.service;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.repository.*;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import de.ostms.lc.user.service.PlatformAdministrationService;
import de.ostms.lc.user.service.TenantAdministrationLock;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformHomeReleaseTest {
 private final AppUserRepository users=mock(AppUserRepository.class);private final AppRoleRepository roles=mock(AppRoleRepository.class);
 private final TenantMembershipService access=mock(TenantMembershipService.class);private final AuditService audit=mock(AuditService.class);
 private final PlatformAdministrationService platform=mock(PlatformAdministrationService.class);
 private final Authentication auth=mock(Authentication.class);
 private final PlatformMembershipService service=new PlatformMembershipService(platform,users,mock(TenantRepository.class),roles,mock(TenantMembershipRepository.class),mock(TenantMembershipSuspensionRepository.class),access,mock(TenantMembershipProvisioningStore.class),mock(TenantAdministrationLock.class),audit);

 private AppUser user(boolean platformAdmin){var u=spy(new AppUser());u.setUsername("admin");u.setPlatformAdministrator(platformAdmin);doReturn(Tenant.DEFAULT_ID).when(u).getTenantId();return u;}
 private AppRole viewer(){var r=spy(new AppRole());r.setBaseRole(UserRole.VIEWER);r.setPermissions(Set.of());doReturn(Tenant.DEFAULT_ID).when(r).getTenantId();doReturn(UUID.randomUUID()).when(r).getId();return r;}
 private TenantMembershipService.Membership membership(UUID userId,UUID roleId){return new TenantMembershipService.Membership(Tenant.DEFAULT_ID,userId,"admin",roleId,"Administrator",Set.of(UserPermission.USER_MANAGE),true,false,true);}

 @Test void aPlatformAdministratorKeepsTheLoginMembershipButLosesAllPermissions(){
  var u=user(true);var viewer=viewer();var id=UUID.randomUUID();
  when(users.findForPasswordReset(id)).thenReturn(Optional.of(u));when(roles.findByBaseRoleAndSystemRoleTrue(UserRole.VIEWER)).thenReturn(Optional.of(viewer));
  when(access.forUser(id)).thenReturn(Optional.of(membership(id,UUID.randomUUID())));
  var result=service.releaseHomeAccess(id,auth);
  verify(u).setAssignedRole(viewer);verify(users).flush();
  assertThat(result.permissions()).isEmpty();assertThat(result.active()).isTrue();
  verify(audit).recordChangeInTransaction(any(),eq("PLATFORM_HOME_ACCESS_RELEASED"),any(),eq(id),any(),any(),any());
 }
 @Test void ordinaryAccountsAreRefused(){
  var id=UUID.randomUUID();var viewer=viewer();var ordinary=user(false);
  when(roles.findByBaseRoleAndSystemRoleTrue(UserRole.VIEWER)).thenReturn(Optional.of(viewer));
  when(users.findForPasswordReset(id)).thenReturn(Optional.of(ordinary));
  assertThatThrownBy(()->service.releaseHomeAccess(id,auth)).isInstanceOf(IllegalArgumentException.class);
  verify(ordinary,never()).setAssignedRole(any());verifyNoInteractions(audit);
 }
 @Test void aSecondCallChangesNothing(){
  var id=UUID.randomUUID();var viewer=viewer();var admin=user(true);var current=membership(id,viewer.getId());
  when(roles.findByBaseRoleAndSystemRoleTrue(UserRole.VIEWER)).thenReturn(Optional.of(viewer));
  when(users.findForPasswordReset(id)).thenReturn(Optional.of(admin));
  when(access.forUser(id)).thenReturn(Optional.of(current));
  assertThat(service.releaseHomeAccess(id,auth)).isSameAs(current);
  verify(admin,never()).setAssignedRole(any());verifyNoInteractions(audit);
 }
}
