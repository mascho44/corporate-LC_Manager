package de.ostms.lc.user.service;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.service.TenantLoginResolver;
import de.ostms.lc.tenant.service.TenantMembershipService;
import de.ostms.lc.tenant.repository.*;
import de.ostms.lc.user.domain.AppUser;
import de.ostms.lc.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A platform administrator belongs to no tenant: with two-factor authentication and no membership at all the login still works, but only as a platform-only identity. */
class PlatformOnlyLoginTest {
 private final AppUserRepository repo=mock(AppUserRepository.class);private final TenantMembershipService memberships=mock(TenantMembershipService.class);
 private final AppUserDetailsService details=new AppUserDetailsService(repo,memberships);
 private AppUser user(boolean platform,boolean totp,boolean active){var u=spy(new AppUser());u.setUsername("platform");u.setPasswordHash("hash");u.setActive(active);u.setPlatformAdministrator(platform);doReturn(totp).when(u).isTotpEnabled();doReturn(Tenant.DEFAULT_ID).when(u).getTenantId();doReturn(UUID.randomUUID()).when(u).getId();return u;}

 @Test void anEligiblePlatformAdministratorGetsOnlyThePlatformAuthority(){
  var u=user(true,true,true);when(repo.findByUsernameIgnoreCase("platform")).thenReturn(Optional.of(u));when(repo.hasLivePlatformAccess(u.getId())).thenReturn(true);
  var loaded=details.loadPlatformOnly("platform");
  assertThat(loaded.getAuthorities()).extracting(a->a.getAuthority()).containsExactly(AppUserDetailsService.PLATFORM_ONLY);
 }
 @Test void everyoneElseIsRefused(){
  for(var u:List.of(user(false,true,true),user(true,false,true),user(true,true,false))){
   when(repo.findByUsernameIgnoreCase("platform")).thenReturn(Optional.of(u));when(repo.hasLivePlatformAccess(u.getId())).thenReturn(true);
   assertThatThrownBy(()->details.loadPlatformOnly("platform")).isInstanceOf(AuthenticationException.class);
  }
  var noLive=user(true,true,true);when(repo.findByUsernameIgnoreCase("platform")).thenReturn(Optional.of(noLive));when(repo.hasLivePlatformAccess(noLive.getId())).thenReturn(false);
  assertThatThrownBy(()->details.loadPlatformOnly("platform")).isInstanceOf(AuthenticationException.class);
  when(repo.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());
  assertThatThrownBy(()->details.loadPlatformOnly("nobody")).isInstanceOf(AuthenticationException.class);
 }
 @Test void loginFallsBackToPlatformOnlyOnlyWithoutATenantCodeAndOnlyAfterTheMembershipChecksFailed(){
  var resolver=mock(TenantLoginResolver.class);var encoder=mock(PasswordEncoder.class);when(encoder.encode(any())).thenReturn("dummy");
  var d=mock(AppUserDetailsService.class);var platformUser=org.springframework.security.core.userdetails.User.withUsername("platform").password("hash").authorities(AppUserDetailsService.PLATFORM_ONLY).build();
  when(resolver.resolve(null)).thenReturn(Tenant.DEFAULT_ID);
  when(d.loadForTenant("platform",Tenant.DEFAULT_ID)).thenThrow(new DisabledException("no membership"));
  when(d.loadPlatformOnly("platform")).thenReturn(platformUser);
  when(encoder.matches("secret","hash")).thenReturn(true);
  var login=new TenantLoginService(resolver,d,encoder);
  var verified=login.verify("platform","secret",null);
  assertThat(verified.tenantId()).isEqualTo(Tenant.DEFAULT_ID);assertThat(verified.user().getAuthorities()).extracting(a->a.getAuthority()).containsExactly(AppUserDetailsService.PLATFORM_ONLY);
  // an explicit tenant code never falls back to platform-only
  when(resolver.resolve("firma")).thenReturn(UUID.randomUUID());when(d.loadForTenant(eq("platform"),any())).thenThrow(new DisabledException("no membership"));
  assertThatThrownBy(()->login.verify("platform","secret","firma")).isInstanceOf(BadCredentialsException.class);
  // wrong password stays a plain failure
  when(encoder.matches("wrong","hash")).thenReturn(false);
  assertThatThrownBy(()->login.verify("platform","wrong",null)).isInstanceOf(BadCredentialsException.class);
 }
}
