package de.ostms.lc.user.api;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.user.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.User;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Someone who administers any tenant must enroll two-factor authentication at login, even if the role in the login tenant is not an administrator role. */
class AdminTwoFactorAtLoginTest {
 final TenantLoginService login=mock(TenantLoginService.class);final AppUserDetailsService details=mock(AppUserDetailsService.class);final TotpService totp=mock(TotpService.class);
 final AuthController controller=new AuthController(mock(AuthenticationManager.class),mock(UserService.class),details,totp,mock(AuditService.class),new LoginAttemptLimiter(),login);
 final org.springframework.security.core.userdetails.UserDetails editor=User.withUsername("markus").password("hash").roles("EDITOR").build();

 @Test void aTenantAdministratorWithoutTwoFactorIsAskedToSetItUpAtLogin(){
  when(login.verify("markus","pw",null)).thenReturn(new TenantLoginService.Verified(Tenant.DEFAULT_ID,editor));
  when(login.administersAnyTenant("markus")).thenReturn(true);when(totp.enabled("markus")).thenReturn(false);
  when(totp.setup("markus")).thenReturn(new TotpService.Setup("SECRET","otpauth://x","data:image/png;base64,x"));
  var result=controller.login(new AuthController.LoginRequest("markus","pw",null),new MockHttpServletRequest(),new MockHttpServletResponse());
  assertThat(result.getStatusCode().value()).isEqualTo(200);assertThat(((Map<?,?>)result.getBody()).get("requiresTotpSetup")).isEqualTo(true);
  verify(totp).setup("markus");
 }
 @Test void aPlainUserSignsInWithoutTwoFactorAsBefore(){
  when(login.verify("markus","pw",null)).thenReturn(new TenantLoginService.Verified(Tenant.DEFAULT_ID,editor));
  when(login.administersAnyTenant("markus")).thenReturn(false);when(totp.enabled("markus")).thenReturn(false);
  var result=controller.login(new AuthController.LoginRequest("markus","pw",null),new MockHttpServletRequest(),new MockHttpServletResponse());
  assertThat(((Map<?,?>)result.getBody()).get("requiresTotp")).isEqualTo(false);verify(totp,never()).setup(any());
 }
}
