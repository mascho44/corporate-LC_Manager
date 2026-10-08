package de.ostms.lc.tenant;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.TenantMembershipRepository;
import de.ostms.lc.tenant.service.TenantMembershipAdministrationService;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import de.ostms.lc.user.service.TenantAdministrationLock;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantMembershipAdministrationTest {
 final TenantMembershipRepository memberships=mock(TenantMembershipRepository.class);
 final AppUserRepository users=mock(AppUserRepository.class);
 final AppRoleRepository roles=mock(AppRoleRepository.class);
 final de.ostms.lc.tenant.repository.TenantMembershipSuspensionRepository suspensions=mock(de.ostms.lc.tenant.repository.TenantMembershipSuspensionRepository.class);
 final TenantAdministrationLock lock=mock(TenantAdministrationLock.class);
 final TenantMembershipAdministrationService service=new TenantMembershipAdministrationService(memberships,users,roles,lock,suspensions);
 final UUID id=UUID.randomUUID(),roleId=UUID.randomUUID();
 final AppUser user=new AppUser();final AppRole original=new AppRole(),replacement=new AppRole();
 final TenantMembership member=new TenantMembership();
 @BeforeEach void setup(){
  ReflectionTestUtils.setField(user,"id",id);user.setUsername("synthetic-user");user.setDisplayName("Synthetic identity");user.setEmail("user@example.com");user.setPasswordHash("hash");
  original.setBaseRole(UserRole.ADMIN);user.setAssignedRole(original);
  replacement.setBaseRole(UserRole.VIEWER);replacement.setName("Synthetic viewer");
  ReflectionTestUtils.setField(replacement,"id",roleId);
  ReflectionTestUtils.setField(member,"user",user);ReflectionTestUtils.setField(member,"role",original);ReflectionTestUtils.setField(member,"active",true);
  when(memberships.findByUserId(id)).thenReturn(Optional.of(member));when(roles.findById(roleId)).thenReturn(Optional.of(replacement));when(users.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(2L);
 }
 @Test void roleOnlyUpdateDoesNotChangeIdentityOrActivation(){
  var result=service.updateRole(id,roleId,"other-user");
  assertThat(result.roleId()).isEqualTo(roleId);assertThat(user.getAssignedRole()).isSameAs(replacement);
  assertThat(user.getUsername()).isEqualTo("synthetic-user");assertThat(user.getDisplayName()).isEqualTo("Synthetic identity");assertThat(user.getEmail()).isEqualTo("user@example.com");assertThat(user.getPasswordHash()).isEqualTo("hash");assertThat(user.isActive()).isTrue();
  verify(lock).acquire();verify(users).flush();verify(memberships,never()).save(any());
 }
 @Test void suspensionChangesOnlyTenantAccess(){
  var result=service.updateSuspension(id,true,"other-user");
  assertThat(result.active()).isFalse();assertThat(result.suspended()).isTrue();assertThat(result.identityActive()).isTrue();
  assertThat(user.isActive()).isTrue();assertThat(user.getPasswordHash()).isEqualTo("hash");assertThat(user.getAssignedRole()).isSameAs(original);
  verify(users,never()).save(any());verify(suspensions).save(any(TenantMembershipSuspension.class));
 }
 @Test void ownMembershipCannotBeSuspended(){
  assertThatThrownBy(()->service.updateSuspension(id,true,user.getUsername())).isInstanceOf(IllegalArgumentException.class);
  verify(suspensions,never()).save(any());
 }
 @Test void lastAdministratorCannotBeSuspended(){
  when(users.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(1L);
  assertThatThrownBy(()->service.updateSuspension(id,true,"other-user")).isInstanceOf(IllegalArgumentException.class);
  verify(suspensions,never()).save(any());
 }
 @Test void activationDoesNotReactivateGloballyDisabledIdentity(){
  user.setActive(false);
  assertThatThrownBy(()->service.updateSuspension(id,false,"other-user")).isInstanceOf(IllegalArgumentException.class);
  assertThat(user.isActive()).isFalse();verify(suspensions,never()).save(any());
 }
 @Test void suspendedMembershipCanBeEnabledWithoutChangingRole(){
  var state=new TenantMembershipSuspension(id);state.setSuspended(true);when(suspensions.findByUserId(id)).thenReturn(Optional.of(state));
  var result=service.updateSuspension(id,false,"other-user");
  assertThat(state.isSuspended()).isFalse();assertThat(result.active()).isTrue();assertThat(result.suspended()).isFalse();assertThat(user.getAssignedRole()).isSameAs(original);
 }
 @Test void roleChangeCannotRemoveSuspension(){
  var state=new TenantMembershipSuspension(id);state.setSuspended(true);when(suspensions.findByUserId(id)).thenReturn(Optional.of(state));when(users.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(1L);
  var result=service.updateRole(id,roleId,"other-user");
  assertThat(result.suspended()).isTrue();assertThat(result.active()).isFalse();assertThat(state.isSuspended()).isTrue();
  verify(suspensions,never()).save(any());
 }
 @Test void lastAdministratorCannotBeDemoted(){
  when(users.countByRoleAndActiveTrue(UserRole.ADMIN)).thenReturn(1L);
  assertThatThrownBy(()->service.updateRole(id,roleId,"other-user")).isInstanceOf(IllegalArgumentException.class);
  assertThat(user.getAssignedRole()).isSameAs(original);verify(users,never()).flush();
 }
 @Test void ownRoleMustKeepAdministrationPermission(){
  assertThatThrownBy(()->service.updateRole(id,roleId,user.getUsername())).isInstanceOf(IllegalArgumentException.class);
  assertThat(user.getAssignedRole()).isSameAs(original);
 }
 @Test void foreignRoleCannotBeAssigned(){
  ReflectionTestUtils.setField(replacement,"tenantId",UUID.randomUUID());
  assertThatThrownBy(()->service.updateRole(id,roleId,"other-user")).isInstanceOf(AccessDeniedException.class);
  assertThat(user.getAssignedRole()).isSameAs(original);
 }
 @Test void foreignIdentityCannotBeEditedThroughBootstrapBridge(){
  ReflectionTestUtils.setField(user,"tenantId",UUID.randomUUID());
  assertThatThrownBy(()->service.updateRole(id,roleId,"other-user")).isInstanceOf(AccessDeniedException.class);
  verifyNoInteractions(roles);verify(users,never()).flush();
 }
 @Test void missingMembershipIsRejected(){
  when(memberships.findByUserId(id)).thenReturn(Optional.empty());
  assertThatThrownBy(()->service.updateRole(id,roleId,"other-user")).isInstanceOf(NoSuchElementException.class);
  verifyNoInteractions(roles);
 }
 @Test void foreignTenantRemainsReadOnly(){
  try(var scope=TenantContext.open(UUID.randomUUID())){
   assertThatThrownBy(()->service.updateRole(id,roleId,"other-user")).isInstanceOf(AccessDeniedException.class);
  }
  verifyNoInteractions(memberships,roles);
 }
}
