package de.corporate.lc.tenant;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.*;
import de.corporate.lc.tenant.service.*;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.AppUserRepository;
import de.corporate.lc.user.service.TenantAdministrationLock;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantSharedIdentityAccessTest {
 final AppUserRepository users=mock(AppUserRepository.class);
 final TenantMembershipRepository memberships=mock(TenantMembershipRepository.class);
 final TenantMembershipSuspensionRepository suspensions=mock(TenantMembershipSuspensionRepository.class);
 final TenantMembershipService access=mock(TenantMembershipService.class);
 final TenantAdministrationLock lock=mock(TenantAdministrationLock.class);
 final AuditService audit=mock(AuditService.class);
 final TenantSharedIdentityAccessService service=new TenantSharedIdentityAccessService(users,memberships,suspensions,access,lock,audit);
 final UUID tenant=UUID.randomUUID(),id=UUID.randomUUID(),actorId=UUID.randomUUID();
 final AppUser actor=new AppUser(),user=new AppUser();final AppRole role=new AppRole();final TenantMembership membership=new TenantMembership();
 final UsernamePasswordAuthenticationToken authentication=new UsernamePasswordAuthenticationToken("admin","",List.of());
 TenantContext.Scope scope;
 @BeforeEach void setup(){
  scope=TenantContext.open(tenant);ReflectionTestUtils.setField(actor,"id",actorId);ReflectionTestUtils.setField(user,"id",id);user.setUsername("shared");user.setPasswordHash("unchanged");
  ReflectionTestUtils.setField(role,"tenantId",tenant);role.setName("Administrator");role.setBaseRole(UserRole.ADMIN);
  ReflectionTestUtils.setField(membership,"tenantId",tenant);ReflectionTestUtils.setField(membership,"user",user);ReflectionTestUtils.setField(membership,"role",role);ReflectionTestUtils.setField(membership,"active",true);
  when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(actor));
  when(access.requireActiveAccess(actorId)).thenReturn(new TenantMembershipService.Access(tenant,actorId,UUID.randomUUID(),UUID.randomUUID(),UserRole.ADMIN,Set.of(UserPermission.USER_MANAGE)));
  when(memberships.findByUserId(id)).thenReturn(Optional.of(membership));when(memberships.countAccessibleAdministrators()).thenReturn(2L);
  when(access.forUser(id)).thenReturn(Optional.of(new TenantMembershipService.Membership(tenant,id,"shared",null,"Administrator",Set.of(),true)));
 }
 @AfterEach void cleanup(){scope.close();}
 @Test void suspensionDoesNotChangeGlobalIdentity(){
  var result=service.changeAccess(id,true,authentication);assertThat(result.suspended()).isTrue();assertThat(result.active()).isFalse();assertThat(user.isActive()).isTrue();assertThat(user.getPasswordHash()).isEqualTo("unchanged");
  verify(users,never()).save(any());verify(suspensions).save(any());verify(audit).recordChangeInTransaction(eq(authentication),eq("USER_MEMBERSHIP_ACCESS_UPDATED"),eq("MEMBERSHIP"),eq(id),anyString(),anyString(),anyString());
 }
 @Test void lastTenantAdministratorIsProtected(){when(memberships.countAccessibleAdministrators()).thenReturn(1L);
  assertThatThrownBy(()->service.changeAccess(id,true,authentication)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(suspensions,audit);
 }
 @Test void ownAccessCannotBeSuspended(){ReflectionTestUtils.setField(actor,"id",id);when(access.requireActiveAccess(id)).thenReturn(new TenantMembershipService.Access(tenant,id,UUID.randomUUID(),UUID.randomUUID(),UserRole.ADMIN,Set.of(UserPermission.USER_MANAGE)));
  assertThatThrownBy(()->service.changeAccess(id,true,authentication)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(suspensions,audit);
 }
 @Test void inactiveGlobalAccountCannotBeReactivated(){user.setActive(false);
  assertThatThrownBy(()->service.changeAccess(id,false,authentication)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(suspensions,audit);
 }
 @Test void foreignMembershipIsDenied(){ReflectionTestUtils.setField(membership,"tenantId",UUID.randomUUID());
  assertThatThrownBy(()->service.changeAccess(id,true,authentication)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(suspensions,audit);
 }
 @Test void missingLivePermissionIsDenied(){when(access.requireActiveAccess(actorId)).thenReturn(new TenantMembershipService.Access(tenant,actorId,UUID.randomUUID(),UUID.randomUUID(),UserRole.VIEWER,Set.of()));
  assertThatThrownBy(()->service.changeAccess(id,true,authentication)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(memberships,suspensions,audit);
 }
 @Test void bootstrapTenantRemainsBlocked(){try(var ignored=TenantContext.open(Tenant.DEFAULT_ID)){
  assertThatThrownBy(()->service.changeAccess(id,true,authentication)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(users,memberships,suspensions,audit);
 }}
}
