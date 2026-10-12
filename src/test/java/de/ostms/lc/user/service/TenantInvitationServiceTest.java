package de.ostms.lc.user.service;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.service.TenantMembershipService;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantInvitationServiceTest {
 private final PlatformInvitationService invitations=mock(PlatformInvitationService.class);private final TenantMembershipService memberships=mock(TenantMembershipService.class);
 private final AppUserRepository users=mock(AppUserRepository.class);private final AppRoleRepository roles=mock(AppRoleRepository.class);
 private final TenantInvitationService service=new TenantInvitationService(invitations,memberships,users,roles);
 private final org.springframework.security.core.Authentication auth=UsernamePasswordAuthenticationToken.authenticated("anna",null,List.of());
 private final UUID tenant=UUID.randomUUID(),roleId=UUID.randomUUID(),userId=UUID.randomUUID();

 private void manager(Set<UserPermission> permissions){
  var u=new AppUser();ReflectionTestUtils.setField(u,"id",userId);when(users.findByUsernameIgnoreCase("anna")).thenReturn(Optional.of(u));
  when(memberships.requireActiveAccess(userId)).thenReturn(new TenantMembershipService.Access(tenant,userId,UUID.randomUUID(),UUID.randomUUID(),UserRole.ADMIN,permissions));
 }
 @Test void onlyUserManagersOfARealTenantMayInvite(){
  manager(Set.of(UserPermission.LC_EDIT));
  try(var scope=TenantContext.open(tenant)){assertThatThrownBy(()->service.invite("A","a@x.org",roleId,auth)).isInstanceOf(AccessDeniedException.class).hasMessageContaining("Benutzerverwaltung");}
  manager(Set.of(UserPermission.USER_MANAGE));
  try(var scope=TenantContext.open(Tenant.DEFAULT_ID)){assertThatThrownBy(()->service.overview(auth)).isInstanceOf(AccessDeniedException.class).hasMessageContaining("nur in Mandanten");}
  verifyNoInteractions(invitations);
 }
 @Test void aManagerInvitesIntoTheCurrentTenantOnlyAndGetsAGenericAnswerForExistingAddresses(){
  manager(Set.of(UserPermission.USER_MANAGE));
  when(invitations.inviteForTenant("A","a@x.org",tenant,roleId,auth)).thenReturn(Optional.empty());
  try(var scope=TenantContext.open(tenant)){
   var r=service.invite("A","a@x.org",roleId,auth);
   assertThat(r.invited()).isFalse();assertThat(r.message()).contains("bereits ein Konto").doesNotContain("a@x.org");
  }
 }
 @Test void resendAndRevokeAreScopedToTheCurrentTenant(){
  manager(Set.of(UserPermission.USER_MANAGE));
  try(var scope=TenantContext.open(tenant)){service.resend(userId,auth);service.revoke(userId,auth);}
  verify(invitations).resendForTenant(userId,tenant,auth);verify(invitations).revokeForTenant(userId,tenant,auth);
 }
}
