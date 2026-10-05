package de.corporate.lc.user.api;
import de.corporate.lc.user.service.*;
import de.corporate.lc.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.User;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerResetTest {
    @Test void pendingTotpLoginCannotBypassNewPasswordAfterReset(){
        var details=mock(AppUserDetailsService.class);var totp=mock(TotpService.class);
        var controller=new AuthController(mock(AuthenticationManager.class),mock(UserService.class),details,totp,mock(AuditService.class),new LoginAttemptLimiter());
        when(details.loadUserByUsername("user")).thenReturn(User.withUsername("user").password("new-hash").roles("VIEWER").build());
        var request=new MockHttpServletRequest();var session=new MockHttpSession();request.setSession(session);
        session.setAttribute("TOTP_PENDING_LOGIN","user");session.setAttribute("TOTP_PENDING_STAMP",CredentialStamp.of("old-hash"));
        session.setAttribute("TOTP_PENDING_AT",System.currentTimeMillis());
        var result=controller.loginTotp(new AuthController.TotpCodeRequest("123456"),request,new MockHttpServletResponse());
        assertThat(result.getStatusCode().value()).isEqualTo(401);assertThat(session.isInvalid()).isTrue();
        verifyNoInteractions(totp);
    }
}
