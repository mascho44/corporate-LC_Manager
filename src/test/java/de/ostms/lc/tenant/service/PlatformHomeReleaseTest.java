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
 private final Authentication auth=mock(Authentication.class);private final TenantMembershipSuspensionRepository suspensions=mock(TenantMembershipSuspensionRepository.class);
 private final PlatformMembershipService service=new PlatformMembershipService(platform,users,mock(TenantRepository.class),roles,mock(TenantMembershipRepository.class),suspensions,access,mock(TenantMembershipProvisioningStore.class),mock(TenantAdministrationLock.class),audit);

 private AppUser user(boolean platformAdmin){var u=spy(new AppUser());u.setUsername("admin");u.setPlatformAdministrator(platformAdmin);doReturn(Tenant.DEFAULT_ID).when(u).getTenantId();doReturn(true).when(u).isTotpEnabled();doReturn(UUID.randomUUID()).when(u).getId();return u;}
 private AppRole viewer(){var r=spy(new AppRole());r.setBaseRole(UserRole.VIEWER);r.setPermissions(Set.of());doReturn(Tenant.DEFAULT_ID).when(r).getTenantId();doReturn(UUID.randomUUID()).when(r).getId();return r;}
 private TenantMembershipService.Membership membership(UUID userId,UUID roleId){return new TenantMembershipService.Membership(Tenant.DEFAULT_ID,userId,"admin",roleId,"Administrator",Set.of(UserPermission.USER_MANAGE),true,false,true);}

 private UUID live(AppUser u){when(users.hasLivePlatformAccess(u.getId())).thenReturn(true);return u.getId();}
 @Test void aPlatformAdministratorLosesTheHomeMembershipAndStaysAbleToSignInAsPlatformOnly(){
  var u=user(true);var viewer=viewer();var id=live(u);
  when(users.findForPasswordReset(id)).thenReturn(Optional.of(u));when(roles.findByBaseRoleAndSystemRoleTrue(UserRole.VIEWER)).thenReturn(Optional.of(viewer));
  when(access.forUser(id)).thenReturn(Optional.of(membership(id,UUID.randomUUID())));
  when(suspensions.findByUserId(id)).thenReturn(Optional.empty());
  var result=service.releaseHomeAccess(id,auth);
  verify(u).setAssignedRole(viewer);verify(users).flush();verify(suspensions).saveAndFlush(any());
  assertThat(result.permissions()).isEmpty();assertThat(result.active()).isFalse();assertThat(result.suspended()).isTrue();
  verify(audit).recordChangeInTransaction(any(),eq("PLATFORM_HOME_ACCESS_RELEASED"),any(),eq(id),any(),any(),any());
 }
 @Test void withoutLivePlatformAccessOrTotpTheAccountIsNotReleasedBecauseItWouldBeLockedOut(){
  var viewer=viewer();when(roles.findByBaseRoleAndSystemRoleTrue(UserRole.VIEWER)).thenReturn(Optional.of(viewer));
  var noLive=user(true);var id=noLive.getId();when(users.findForPasswordReset(id)).thenReturn(Optional.of(noLive));when(users.hasLivePlatformAccess(id)).thenReturn(false);
  assertThatThrownBy(()->service.releaseHomeAccess(id,auth)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("locked out");
  var noTotp=user(true);doReturn(false).when(noTotp).isTotpEnabled();var id2=live(noTotp);when(users.findForPasswordReset(id2)).thenReturn(Optional.of(noTotp));
  assertThatThrownBy(()->service.releaseHomeAccess(id2,auth)).isInstanceOf(IllegalArgumentException.class);
  verify(noLive,never()).setAssignedRole(any());verifyNoInteractions(audit);
 }
 @Test void ordinaryAccountsAreRefused(){
  var id=UUID.randomUUID();var viewer=viewer();var ordinary=user(false);
  when(roles.findByBaseRoleAndSystemRoleTrue(UserRole.VIEWER)).thenReturn(Optional.of(viewer));
  when(users.findForPasswordReset(id)).thenReturn(Optional.of(ordinary));
  assertThatThrownBy(()->service.releaseHomeAccess(id,auth)).isInstanceOf(IllegalArgumentException.class);
  verify(ordinary,never()).setAssignedRole(any());verifyNoInteractions(audit);
 }
 @Test void aSecondCallChangesNothing(){
  var viewer=viewer();var admin=user(true);var id=live(admin);var current=new TenantMembershipService.Membership(Tenant.DEFAULT_ID,id,"admin",viewer.getId(),"Viewer",Set.of(),false,true,true);
  when(roles.findByBaseRoleAndSystemRoleTrue(UserRole.VIEWER)).thenReturn(Optional.of(viewer));
  when(users.findForPasswordReset(id)).thenReturn(Optional.of(admin));
  when(access.forUser(id)).thenReturn(Optional.of(current));
  assertThat(service.releaseHomeAccess(id,auth)).isSameAs(current);
  verify(admin,never()).setAssignedRole(any());verifyNoInteractions(audit);
 }
}
