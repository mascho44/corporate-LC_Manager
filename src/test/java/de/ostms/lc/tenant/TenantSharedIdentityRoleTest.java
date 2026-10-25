package de.ostms.lc.tenant;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.*;
import de.ostms.lc.tenant.service.*;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import de.ostms.lc.user.service.TenantAdministrationLock;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantSharedIdentityRoleTest {
 final AppUserRepository users=mock(AppUserRepository.class);final AppRoleRepository roles=mock(AppRoleRepository.class);
 final TenantMembershipRepository memberships=mock(TenantMembershipRepository.class);final TenantMembershipService access=mock(TenantMembershipService.class);
 final TenantMembershipProvisioningStore store=mock(TenantMembershipProvisioningStore.class);final TenantAdministrationLock lock=mock(TenantAdministrationLock.class);final AuditService audit=mock(AuditService.class);
 final TenantSharedIdentityRoleService service=new TenantSharedIdentityRoleService(users,roles,memberships,access,store,lock,audit);
 final UUID tenant=UUID.randomUUID(),id=UUID.randomUUID(),actorId=UUID.randomUUID(),roleId=UUID.randomUUID();
 final AppUser actor=new AppUser(),user=new AppUser();final AppRole original=new AppRole(),replacement=new AppRole();final TenantMembership membership=new TenantMembership();
 final UsernamePasswordAuthenticationToken authentication=new UsernamePasswordAuthenticationToken("admin","",List.of());TenantContext.Scope scope;
 @BeforeEach void setup(){
  scope=TenantContext.open(tenant);ReflectionTestUtils.setField(actor,"id",actorId);ReflectionTestUtils.setField(user,"id",id);user.setUsername("shared");user.setPasswordHash("unchanged");
  ReflectionTestUtils.setField(original,"tenantId",tenant);original.setBaseRole(UserRole.ADMIN);
  ReflectionTestUtils.setField(replacement,"tenantId",tenant);ReflectionTestUtils.setField(replacement,"id",roleId);replacement.setName("Viewer");replacement.setBaseRole(UserRole.VIEWER);
  ReflectionTestUtils.setField(membership,"tenantId",tenant);ReflectionTestUtils.setField(membership,"user",user);ReflectionTestUtils.setField(membership,"role",original);ReflectionTestUtils.setField(membership,"active",true);
  when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(actor));when(roles.findById(roleId)).thenReturn(Optional.of(replacement));
  when(access.requireActiveAccess(actorId)).thenReturn(new TenantMembershipService.Access(tenant,actorId,UUID.randomUUID(),UUID.randomUUID(),UserRole.ADMIN,Set.of(UserPermission.USER_MANAGE)));
  when(memberships.findByUserId(id)).thenReturn(Optional.of(membership));when(memberships.countAccessibleAdministrators()).thenReturn(2L);
  when(access.forUser(id)).thenReturn(Optional.of(new TenantMembershipService.Membership(tenant,id,"shared",null,"Administrator",Set.of(),true)));
 }
 @AfterEach void cleanup(){scope.close();}
 @Test void updatesOnlySelectedMembershipAndAudits(){
  var result=service.changeRole(id,roleId,authentication);assertThat(result.roleId()).isEqualTo(roleId);assertThat(user.getPasswordHash()).isEqualTo("unchanged");assertThat(user.getTenantId()).isEqualTo(Tenant.DEFAULT_ID);assertThat(user.getAssignedRole()).isNull();
  verify(store).updateRole(tenant,id,roleId);verify(users,never()).save(any());verify(audit).recordChangeInTransaction(eq(authentication),eq("USER_MEMBERSHIP_ROLE_UPDATED"),eq("MEMBERSHIP"),eq(id),anyString(),anyString(),anyString());
 }
 @Test void lastAdministratorCannotBeDemoted(){when(memberships.countAccessibleAdministrators()).thenReturn(1L);
  assertThatThrownBy(()->service.changeRole(id,roleId,authentication)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(store,audit);
 }
 @Test void suspensionSurvivesRoleChange(){when(access.forUser(id)).thenReturn(Optional.of(new TenantMembershipService.Membership(tenant,id,"shared",null,"Administrator",Set.of(),false,true,true)));
  var result=service.changeRole(id,roleId,authentication);assertThat(result.active()).isFalse();assertThat(result.suspended()).isTrue();
 }
 @Test void ownRoleMustKeepAdministrationPermission(){ReflectionTestUtils.setField(actor,"id",id);when(access.requireActiveAccess(id)).thenReturn(new TenantMembershipService.Access(tenant,id,UUID.randomUUID(),UUID.randomUUID(),UserRole.ADMIN,Set.of(UserPermission.USER_MANAGE)));
  assertThatThrownBy(()->service.changeRole(id,roleId,authentication)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(store,audit);
 }
 @Test void foreignRoleCannotBeAssigned(){ReflectionTestUtils.setField(replacement,"tenantId",UUID.randomUUID());
  assertThatThrownBy(()->service.changeRole(id,roleId,authentication)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(store,audit);
 }
 @Test void homeIdentityCannotBeChanged(){ReflectionTestUtils.setField(user,"tenantId",tenant);
  assertThatThrownBy(()->service.changeRole(id,roleId,authentication)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(store,audit);
 }
 @Test void bootstrapTenantRemainsBlocked(){try(var ignored=TenantContext.open(Tenant.DEFAULT_ID)){
  assertThatThrownBy(()->service.changeRole(id,roleId,authentication)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(users,store,audit);
 }}
}
