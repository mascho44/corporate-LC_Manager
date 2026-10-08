package de.ostms.lc.user.service;
import de.ostms.lc.user.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class AuthorizationStampTest {
 @Test void loginAuthoritiesMatchStoredRoleAndPermissionSnapshot(){
  var user=new AppUser();var role=new AppRole();role.setBaseRole(UserRole.EDITOR);role.setPermissions(Set.of(UserPermission.LC_EDIT,UserPermission.DOCUMENT_UPLOAD));user.setAssignedRole(role);
  var granted=List.of(new SimpleGrantedAuthority("PERM_DOCUMENT_UPLOAD"),new SimpleGrantedAuthority("ROLE_EDITOR"),new SimpleGrantedAuthority("PERM_LC_EDIT"));
  assertThat(AuthorizationStamp.of(user)).isEqualTo(AuthorizationStamp.of(granted));
  role.setPermissions(Set.of(UserPermission.LC_EDIT));assertThat(AuthorizationStamp.of(user)).isNotEqualTo(AuthorizationStamp.of(granted));
 }
 @Test void legacyRoleDefaultsAndDuplicateAuthorityOrderAreStable(){
  var user=new AppUser();user.setRole(UserRole.VIEWER);
  assertThat(AuthorizationStamp.of(user)).isEqualTo(AuthorizationStamp.of(List.of(new SimpleGrantedAuthority("ROLE_VIEWER"),new SimpleGrantedAuthority("ROLE_VIEWER"))));
  user.setRole(UserRole.EDITOR);assertThat(AuthorizationStamp.of(user)).isNotEqualTo(AuthorizationStamp.of(List.of(new SimpleGrantedAuthority("ROLE_VIEWER"))));
 }
}
