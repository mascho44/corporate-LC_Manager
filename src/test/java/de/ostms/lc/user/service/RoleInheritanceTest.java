package de.ostms.lc.user.service;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.api.RoleRequest;
import de.ostms.lc.user.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class RoleInheritanceTest {
 @Test void assignedRoleBaseChangesAffectUserAuthoritiesAndSessionStamp(){
  var user=new AppUser();user.setUsername("synthetic-user");user.setPasswordHash("synthetic-hash");var role=new AppRole();role.setBaseRole(UserRole.EDITOR);role.setPermissions(Set.of(UserPermission.LC_EDIT));user.setAssignedRole(role);
  var stamp=AuthorizationStamp.of(user);role.setBaseRole(UserRole.ADMIN);
  assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);assertThat(AuthorizationStamp.of(user)).isNotEqualTo(stamp);
  var repo=mock(AppUserRepository.class);when(repo.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));
  var memberships=mock(de.ostms.lc.tenant.service.TenantMembershipService.class);when(memberships.requireActiveAccess(org.mockito.ArgumentMatchers.nullable(UUID.class))).thenReturn(new de.ostms.lc.tenant.service.TenantMembershipService.Access(user.getTenantId(),user.getId(),UUID.randomUUID(),UUID.randomUUID(),role.getBaseRole(),role.getPermissions()));
  assertThat(new AppUserDetailsService(repo,memberships).loadUserByUsername(user.getUsername()).getAuthorities()).extracting("authority").contains("ROLE_ADMIN");
  var passwords=mock(org.springframework.security.crypto.password.PasswordEncoder.class);var totp=new TotpService(repo,passwords,"");
  assertThatThrownBy(()->totp.disable(user.getUsername(),"synthetic-password","123456")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("verpflichtend");verifyNoInteractions(passwords);
  role.setBaseRole(UserRole.VIEWER);assertThat(user.getRole()).isEqualTo(UserRole.VIEWER);
 }
 @Test void lastActiveAdministratorCannotBeDemotedThroughCustomRole(){
  var roles=mock(AppRoleRepository.class);var users=mock(AppUserRepository.class);var id=UUID.randomUUID();var role=new AppRole();role.setBaseRole(UserRole.ADMIN);role.setName("Synthetic administrator");when(roles.findById(id)).thenReturn(Optional.of(role));when(roles.findAllByOrderByNameAsc()).thenReturn(List.of());when(users.countByAssignedRoleIdAndActiveTrue(id)).thenReturn(2L);when(users.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(2L);
  var service=new RoleService(roles,users,mock(TenantAdministrationLock.class));var request=new RoleRequest(role.getName(),UserRole.VIEWER,Set.of());
  assertThatThrownBy(()->service.update(id,request)).isInstanceOf(IllegalArgumentException.class);assertThat(role.getBaseRole()).isEqualTo(UserRole.ADMIN);
  when(users.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(3L);service.update(id,request);assertThat(role.getBaseRole()).isEqualTo(UserRole.VIEWER);
 }
}
