package de.ostms.lc.user.api;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.config.CredentialSessionFilter;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.user.service.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A platform administrator without any membership: password, then second factor, ends in a session for the identity home with only the platform authority. */
class PlatformOnlyLoginFlowTest {
 final TenantLoginService login=mock(TenantLoginService.class);final AppUserDetailsService details=mock(AppUserDetailsService.class);final TotpService totp=mock(TotpService.class);
 final AuthController controller=new AuthController(mock(AuthenticationManager.class),mock(UserService.class),details,totp,mock(AuditService.class),new LoginAttemptLimiter(),login);
 final org.springframework.security.core.userdetails.UserDetails platformUser=User.withUsername("platform").password("hash").authorities(AppUserDetailsService.PLATFORM_ONLY).build();
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void secondFactorCompletesTheLoginThroughThePlatformOnlyIdentity(){
  when(login.verify("platform","password",null)).thenReturn(new TenantLoginService.Verified(Tenant.DEFAULT_ID,platformUser));
  when(details.loadForTenant("platform",Tenant.DEFAULT_ID)).thenThrow(new DisabledException("no membership"));when(details.loadPlatformOnly("platform")).thenReturn(platformUser);
  when(totp.enabled("platform")).thenReturn(true);when(totp.verifyLogin("platform","123456")).thenReturn(true);
  var request=new MockHttpServletRequest();
  assertThat(controller.login(new AuthController.LoginRequest("platform","password",null),request,new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(200);
  assertThat(controller.loginTotp(new AuthController.TotpCodeRequest("123456"),request,new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(200);
  assertThat(request.getSession().getAttribute(CredentialSessionFilter.TENANT)).isEqualTo(Tenant.DEFAULT_ID);
  assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).extracting(a->a.getAuthority()).containsExactly(AppUserDetailsService.PLATFORM_ONLY);
 }
 @Test void aTargetOtherThanTheIdentityHomeNeverFallsBackToPlatformOnly(){
  var other=java.util.UUID.randomUUID();
  when(login.verify("platform","password","firma")).thenReturn(new TenantLoginService.Verified(other,platformUser));
  when(details.loadForTenant("platform",other)).thenThrow(new DisabledException("revoked"));when(details.loadPlatformOnly("platform")).thenReturn(platformUser);
  when(totp.enabled("platform")).thenReturn(true);
  var request=new MockHttpServletRequest();
  controller.login(new AuthController.LoginRequest("platform","password","firma"),request,new MockHttpServletResponse());
  assertThat(controller.loginTotp(new AuthController.TotpCodeRequest("123456"),request,new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(401);
  verify(details,never()).loadPlatformOnly(any());
 }
}
