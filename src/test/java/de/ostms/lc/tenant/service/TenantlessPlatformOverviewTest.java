package de.ostms.lc.tenant.service;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.repository.*;
import de.ostms.lc.user.domain.AppUser;
import de.ostms.lc.user.repository.*;
import de.ostms.lc.user.service.TenantAdministrationLock;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The workspace list of a platform administrator without any tenant is empty, not an error; everyone else still gets the refusal. */
class TenantlessPlatformOverviewTest {
 private final AppUserRepository users=mock(AppUserRepository.class);private final TenantMembershipService access=mock(TenantMembershipService.class);
 private final TenantWorkspaceService service=new TenantWorkspaceService(mock(TenantRepository.class),mock(TenantMembershipRepository.class),users,mock(AppRoleRepository.class),access,mock(TenantMembershipProvisioningStore.class),mock(TenantAdministrationLock.class),mock(AuditService.class));
 private AppUser user(boolean platform,boolean totp){var u=spy(new AppUser());u.setUsername("who");u.setActive(true);u.setPlatformAdministrator(platform);doReturn(totp).when(u).isTotpEnabled();doReturn(UUID.randomUUID()).when(u).getId();return u;}
 private final org.springframework.security.core.Authentication auth=UsernamePasswordAuthenticationToken.authenticated("who",null,List.of());

 @Test void aPlatformAdministratorWithoutAnyMembershipGetsAnEmptyList(){
  var u=user(true,true);when(users.findByUsernameIgnoreCase("who")).thenReturn(Optional.of(u));
  when(access.requireActiveAccess(u.getId())).thenThrow(new AccessDeniedException("no membership"));
  assertThat(service.overview(auth).workspaces()).isEmpty();
 }
 @Test void everyoneElseStillGetsTheRefusal(){
  for(var u:List.of(user(false,true),user(true,false))){
   when(users.findByUsernameIgnoreCase("who")).thenReturn(Optional.of(u));when(access.requireActiveAccess(u.getId())).thenThrow(new AccessDeniedException("no membership"));
   assertThatThrownBy(()->service.overview(auth)).isInstanceOf(AccessDeniedException.class);
  }
 }
}
