package de.corporate.lc.user.service;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAdministrationServiceTest {
 final AppUserRepository users=mock(AppUserRepository.class);final TenantAdministrationLock lock=mock(TenantAdministrationLock.class);final AuditService audit=mock(AuditService.class);
 final PlatformAdministrationService service=new PlatformAdministrationService(users,lock,audit);
 final org.springframework.security.core.Authentication auth=UsernamePasswordAuthenticationToken.authenticated("admin",null,List.of());
 AppUser user(String name){var u=new AppUser();ReflectionTestUtils.setField(u,"id",UUID.randomUUID());u.setUsername(name);u.setDisplayName("Synthetic");u.setEmail(name+"@example.invalid");return u;}
 AppUser administrator(){var u=user("admin");u.setPlatformAdministrator(true);u.setTotpEnabled(true);when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(u));return u;}
 @Test void localAdminRoleNeverGrantsPlatformAccess(){var u=user("admin");u.setRole(UserRole.ADMIN);u.setTotpEnabled(true);when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(u));assertThat(service.enabled(auth)).isFalse();assertThatThrownBy(()->service.accounts(auth)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verify(users,never()).findAllByOrderByUsernameAsc();}
 @Test void inactiveOrUnenrolledPlatformAccountIsDenied(){var u=administrator();u.setTotpEnabled(false);assertThat(service.enabled(auth)).isFalse();u.setTotpEnabled(true);u.setActive(false);assertThat(service.enabled(auth)).isFalse();assertThat(service.enabled(null)).isFalse();}
 @Test void deniedMutationDoesNotLockAdministrationOrReadTarget(){var u=user("admin");when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(u));assertThatThrownBy(()->service.changeAccess(UUID.randomUUID(),false,auth)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(lock,audit);verify(users,never()).findById(any());}
 @Test void globalListUsesHomeScopeAndRestoresSelectedWorkspace(){var u=administrator();UUID workspace=UUID.randomUUID();when(users.findAllByOrderByUsernameAsc()).thenAnswer(call->{assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);return List.of(u);});try(var scope=TenantContext.open(workspace)){assertThat(service.accounts(auth)).hasSize(1);assertThat(TenantContext.currentId()).isEqualTo(workspace);}assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);}
 @Test void rejectedGlobalReadRestoresSelectedWorkspace(){when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());UUID workspace=UUID.randomUUID();try(var scope=TenantContext.open(workspace)){assertThatThrownBy(()->service.accounts(auth)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThat(TenantContext.currentId()).isEqualTo(workspace);}}
 @Test void globalSuspensionAuditsOnlyActivationAndDoesNotChangeRoles(){administrator();var target=user("synthetic-target");when(users.findById(target.getId())).thenReturn(Optional.of(target));UUID workspace=UUID.randomUUID();try(var scope=TenantContext.open(workspace)){assertThat(service.changeAccess(target.getId(),false,auth).active()).isFalse();assertThat(TenantContext.currentId()).isEqualTo(workspace);}verify(users).saveAndFlush(target);verify(audit).recordChangeInTransaction(eq(auth),eq("PLATFORM_ACCOUNT_ACCESS_UPDATED"),eq("USER"),eq(target.getId()),anyString(),eq("{\"active\":true}"),eq("{\"active\":false}"));assertThat(target.getAssignedRole()).isNull();assertThat(target.isPlatformAdministrator()).isFalse();}
 @Test void cannotSuspendOwnAccount(){var u=administrator();when(users.findById(u.getId())).thenReturn(Optional.of(u));assertThatThrownBy(()->service.changeAccess(u.getId(),false,auth)).isInstanceOf(IllegalArgumentException.class);assertThat(u.isActive()).isTrue();verifyNoInteractions(audit);}
 @Test void lastEnabledPlatformAdministratorIsProtected(){administrator();var target=user("synthetic-other-platform");target.setTotpEnabled(true);target.setPlatformAdministrator(true);when(users.findById(target.getId())).thenReturn(Optional.of(target));when(users.countEnabledPlatformAdministrators()).thenReturn(1L);assertThatThrownBy(()->service.changeAccess(target.getId(),false,auth)).isInstanceOf(IllegalArgumentException.class);assertThat(target.isActive()).isTrue();verify(users,never()).saveAndFlush(any());}
 @Test void unchangedAccessDoesNotWriteOrAudit(){administrator();var target=user("synthetic-target");when(users.findById(target.getId())).thenReturn(Optional.of(target));service.changeAccess(target.getId(),true,auth);verify(users,never()).saveAndFlush(any());verifyNoInteractions(audit);}
}
