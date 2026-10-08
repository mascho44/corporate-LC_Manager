package de.ostms.lc.user.service;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.TenantRepository;
import de.ostms.lc.tenant.service.*;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.AppUserRepository;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExplicitTenantLoginTest {
 final UUID target=UUID.randomUUID(),userId=UUID.randomUUID();final AppUserRepository users=mock(AppUserRepository.class);final TenantMembershipService access=mock(TenantMembershipService.class);final TenantRepository tenants=mock(TenantRepository.class);final AppUser user=new AppUser();final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
 AppUserDetailsService details;TenantLoginService login;
 @BeforeEach void setup(){user.setUsername("synthetic-user");user.setPasswordHash(encoder.encode("synthetic-password"));user.setRole(UserRole.ADMIN);ReflectionTestUtils.setField(user,"id",userId);when(users.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));
  var tenant=new Tenant("synthetic","Synthetic","en",true,false);ReflectionTestUtils.setField(tenant,"id",target);when(tenants.findByCodeIgnoreCase("synthetic")).thenReturn(Optional.of(tenant));
  when(access.requireActiveAccess(userId)).thenAnswer(i->{if(TenantContext.currentId().equals(Tenant.DEFAULT_ID))throw new org.springframework.security.access.AccessDeniedException("Home suspended");assertThat(TenantContext.currentId()).isEqualTo(target);return new TenantMembershipService.Access(target,userId,UUID.randomUUID(),UUID.randomUUID(),UserRole.VIEWER,Set.of(UserPermission.DOCUMENT_REVIEW));});
  details=new AppUserDetailsService(users,access);login=new TenantLoginService(new TenantLoginResolver(tenants),details,encoder);
 }
 @Test void explicitLoginWorksWithoutDefaultAccessAndUsesOnlyLocalPermissions(){var result=login.verify(user.getUsername(),"synthetic-password","synthetic");assertThat(result.tenantId()).isEqualTo(target);assertThat(result.user().getAuthorities()).extracting("authority").containsExactlyInAnyOrder("ROLE_VIEWER","PERM_DOCUMENT_REVIEW");assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);}
 @Test void codeFreeLoginUsesAccessibleWorkspaceWhenDefaultAccessIsSuspended(){
  var memberships=mock(de.ostms.lc.tenant.repository.TenantMembershipRepository.class);
  var membership=mock(TenantMembership.class);when(membership.getTenantId()).thenReturn(target);
  when(memberships.findWorkspaceMemberships(userId)).thenReturn(List.of(membership));
  when(tenants.findById(target)).thenReturn(Optional.of(new Tenant("synthetic","Synthetic","en",true,false)));
  ReflectionTestUtils.setField(login,"identities",users);ReflectionTestUtils.setField(login,"memberships",memberships);ReflectionTestUtils.setField(login,"tenants",tenants);
  assertThat(login.verify(user.getUsername(),"synthetic-password",null).tenantId()).isEqualTo(target);
  assertThatThrownBy(()->login.verify(user.getUsername(),"wrong",null)).isInstanceOf(BadCredentialsException.class);
  when(memberships.findWorkspaceMemberships(userId)).thenReturn(List.of());
  assertThatThrownBy(()->login.verify(user.getUsername(),"synthetic-password",null)).isInstanceOf(BadCredentialsException.class);
 }
 @Test void frameworkLoginNeverInheritsOuterTenantScope(){try(var scope=TenantContext.open(target)){assertThatThrownBy(()->details.loadUserByUsername(user.getUsername())).isInstanceOf(DisabledException.class);assertThat(TenantContext.currentId()).isEqualTo(target);}}
 @Test void wrongPasswordUnknownTenantAndHomeSuspensionShareGenericFailure(){for(String code:List.of("synthetic","missing","")){assertThatThrownBy(()->login.verify(user.getUsername(),"wrong",code)).isInstanceOf(BadCredentialsException.class).hasMessage("Login failed.");}assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);}
 @Test void globallyDisabledIdentityCannotLoginThroughAnotherTenant(){user.setActive(false);assertThatThrownBy(()->login.verify(user.getUsername(),"synthetic-password","synthetic")).isInstanceOf(BadCredentialsException.class);}
 @Test void revokedTargetMembershipCannotAuthenticate(){doThrow(new org.springframework.security.access.AccessDeniedException("Revoked")).when(access).requireActiveAccess(userId);assertThatThrownBy(()->login.verify(user.getUsername(),"synthetic-password","synthetic")).isInstanceOf(BadCredentialsException.class);}
 @Test void unknownTenantDoesNotLoadIdentity(){assertThatThrownBy(()->login.verify(user.getUsername(),"synthetic-password","missing")).isInstanceOf(BadCredentialsException.class);verifyNoInteractions(users,access);}
}
