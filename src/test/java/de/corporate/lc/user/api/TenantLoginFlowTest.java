package de.corporate.lc.user.api;
import de.corporate.lc.user.service.*;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.config.CredentialSessionFilter;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class TenantLoginFlowTest {
 final UUID target=UUID.randomUUID();final TenantLoginService login=mock(TenantLoginService.class);final AppUserDetailsService details=mock(AppUserDetailsService.class);final TotpService totp=mock(TotpService.class);
 final AuthController controller=new AuthController(mock(AuthenticationManager.class),mock(UserService.class),details,totp,mock(AuditService.class),new LoginAttemptLimiter(),login);
 @BeforeEach void setup(){var user=User.withUsername("synthetic-user").password("hash").roles("VIEWER").build();when(login.verify("synthetic-user","password","synthetic")).thenReturn(new TenantLoginService.Verified(target,user));when(details.loadForTenant("synthetic-user",target)).thenReturn(user);}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 MockHttpServletRequest start(){var request=new MockHttpServletRequest();assertThat(controller.login(new AuthController.LoginRequest("synthetic-user","password","synthetic"),request,new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(200);return request;}
 @Test void passwordOnlyLoginSetsExplicitTargetAndNeverUsesDefaultDetails(){var request=start();assertThat(request.getSession().getAttribute(CredentialSessionFilter.TENANT)).isEqualTo(target);verifyNoInteractions(details);}
 @Test void secondFactorBindsTargetUntilVerificationAndClearsPendingState(){when(totp.enabled("synthetic-user")).thenReturn(true);when(totp.verifyLogin("synthetic-user","123456")).thenReturn(true);var request=start();assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();assertThat(request.getSession().getAttribute("TOTP_PENDING_TENANT")).isEqualTo(target);
  assertThat(controller.loginTotp(new AuthController.TotpCodeRequest("123456"),request,new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(200);assertThat(request.getSession().getAttribute(CredentialSessionFilter.TENANT)).isEqualTo(target);assertThat(request.getSession().getAttribute("TOTP_PENDING_TENANT")).isNull();verify(details).loadForTenant("synthetic-user",target);verify(details,never()).loadUserByUsername(any());
 }
 @Test void targetRevocationBetweenFactorsInvalidatesPendingSession(){when(totp.enabled("synthetic-user")).thenReturn(true);var request=start();var session=(MockHttpSession)request.getSession();when(details.loadForTenant("synthetic-user",target)).thenThrow(new DisabledException("Revoked"));assertThat(controller.loginTotp(new AuthController.TotpCodeRequest("123456"),request,new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(401);assertThat(session.isInvalid()).isTrue();verify(totp,never()).verifyLogin(any(),any());}
 @Test void missingPendingTargetNeverFallsBackToDefault(){when(totp.enabled("synthetic-user")).thenReturn(true);var request=start();var session=(MockHttpSession)request.getSession();session.removeAttribute("TOTP_PENDING_TENANT");assertThat(controller.loginTotp(new AuthController.TotpCodeRequest("123456"),request,new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(401);assertThat(session.isInvalid()).isTrue();verifyNoInteractions(details);}
 @Test void priorThreadContextDoesNotAffectIdentitySecondFactorServices(){UUID unrelated=UUID.randomUUID();when(totp.enabled("synthetic-user")).thenAnswer(i->{assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);return false;});try(var scope=TenantContext.open(unrelated)){start();assertThat(TenantContext.currentId()).isEqualTo(unrelated);}}
}
