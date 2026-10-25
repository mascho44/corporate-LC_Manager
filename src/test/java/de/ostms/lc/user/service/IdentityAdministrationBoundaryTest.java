package de.ostms.lc.user.service;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import de.ostms.lc.user.api.UserRequest;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityAdministrationBoundaryTest {
 final AppUserRepository users=mock(AppUserRepository.class);
 final AppRoleRepository roles=mock(AppRoleRepository.class);
 final PasswordEncoder encoder=mock(PasswordEncoder.class);
 final IdentityCredentialService credentials=new IdentityCredentialService(users,encoder);
 final UserService service=new UserService(users,roles,encoder,mock(TenantAdministrationLock.class),credentials);
 final UUID id=UUID.randomUUID(),roleId=UUID.randomUUID();
 final AppUser user=new AppUser();
 final AppRole role=new AppRole();
 @BeforeEach void setup(){
  org.springframework.test.util.ReflectionTestUtils.setField(user,"id",id);
  user.setUsername("synthetic-user");user.setDisplayName("Synthetic User");user.setEmail("user@example.com");user.setPasswordHash("old-hash");
  role.setBaseRole(UserRole.VIEWER);role.setPermissions(Set.of());user.setAssignedRole(role);
  when(users.findById(id)).thenReturn(Optional.of(user));when(users.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));
  when(users.countForeignMemberships(id)).thenReturn(1L);when(roles.findById(roleId)).thenReturn(Optional.of(role));
 }
 @Test void tenantAdministratorCannotDeleteSharedIdentity(){
  assertThatThrownBy(()->service.delete(id,"other-user")).isInstanceOf(AccessDeniedException.class);
  verify(users,never()).delete(any(AppUser.class));
 }
 @Test void tenantAdministratorCannotChangeSharedCredentialsOrIdentityState(){
  for(var request:List.of(
    new UserRequest(user.getUsername(),user.getDisplayName(),user.getEmail(),"NewPassword123",roleId,true),
    new UserRequest(user.getUsername(),user.getDisplayName(),user.getEmail(),null,roleId,false),
    new UserRequest(user.getUsername(),"Changed Name",user.getEmail(),null,roleId,true),
    new UserRequest(user.getUsername(),user.getDisplayName(),"changed@example.com",null,roleId,true),
    new UserRequest("changed-user",user.getDisplayName(),user.getEmail(),null,roleId,true))){
   assertThatThrownBy(()->service.update(id,request,"other-user")).isInstanceOf(AccessDeniedException.class);
  }
  assertThat(user.getPasswordHash()).isEqualTo("old-hash");assertThat(user.isActive()).isTrue();
  assertThat(user.getEmail()).isEqualTo("user@example.com");verifyNoInteractions(encoder);
 }
 @Test void homeMembershipRoleCanStillBeUpdatedWithoutChangingIdentity(){
  var replacement=new AppRole();replacement.setBaseRole(UserRole.EDITOR);replacement.setPermissions(Set.of(UserPermission.LC_EDIT));
  when(roles.findById(roleId)).thenReturn(Optional.of(replacement));
  service.update(id,new UserRequest(user.getUsername(),user.getDisplayName(),user.getEmail(),null,roleId,true),"other-user");
  assertThat(user.getAssignedRole()).isSameAs(replacement);assertThat(user.getRole()).isEqualTo(UserRole.EDITOR);assertThat(user.getPasswordHash()).isEqualTo("old-hash");
 }
 @Test void identityOwnerCanChangeOwnPasswordWithoutTenantAdministration(){
  when(encoder.matches("OldPassword123","old-hash")).thenReturn(true);
  when(encoder.encode("NewPassword123")).thenReturn("new-hash");
  credentials.changeOwnPassword(user.getUsername(),"OldPassword123","NewPassword123");
  assertThat(user.getPasswordHash()).isEqualTo("new-hash");verify(users,never()).countForeignMemberships(any());
 }
 @Test void wrongCurrentPasswordLeavesIdentityUnchanged(){
  assertThatThrownBy(()->credentials.changeOwnPassword(user.getUsername(),"wrong","NewPassword123")).isInstanceOf(IllegalArgumentException.class);
  assertThat(user.getPasswordHash()).isEqualTo("old-hash");verify(encoder,never()).encode(anyString());
 }
}
