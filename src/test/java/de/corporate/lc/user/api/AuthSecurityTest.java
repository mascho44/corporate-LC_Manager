package de.corporate.lc.user.api;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.user.service.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSecurityTest {
    final AuthenticationManager manager=mock(AuthenticationManager.class);
    final AppUserDetailsService details=mock(AppUserDetailsService.class);
    final TotpService totp=mock(TotpService.class);
    final AuthController controller=new AuthController(manager,mock(UserService.class),details,totp,mock(AuditService.class),new LoginAttemptLimiter());
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void account(String role){
        var user=User.withUsername("user").password("hash").roles(role).build();
        when(details.loadUserByUsername("user")).thenReturn(user);
        when(manager.authenticate(any())).thenReturn(UsernamePasswordAuthenticationToken.authenticated(user,null,user.getAuthorities()));
    }
    @Test void adminWithoutTotpOnlyGetsEnrollmentSession(){
        account("ADMIN");when(totp.setup("user")).thenReturn(new TotpService.Setup("secret","uri","data:image/png;base64,test"));
        var request=new MockHttpServletRequest();
        var result=controller.login(new AuthController.LoginRequest("user","password"),request,new MockHttpServletResponse());
        assertThat(((Map<?,?>)result.getBody()).containsKey("requiresTotpSetup")).isTrue();
        assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        assertThat(request.getSession().getAttribute("TOTP_PENDING_SECRET")).isEqualTo("secret");
    }
    @Test void adminCompletesEnrollmentAndReceivesRecoveryCodes(){
        account("ADMIN");when(totp.setup("user")).thenReturn(new TotpService.Setup("secret","uri","qr"));
        when(totp.enable("user","secret","123456")).thenReturn(List.of("recovery"));
        var request=new MockHttpServletRequest();
        controller.login(new AuthController.LoginRequest("user","password"),request,new MockHttpServletResponse());
        var result=controller.loginTotp(new AuthController.TotpCodeRequest("123456"),request,new MockHttpServletResponse());
        assertThat(((Map<?,?>)result.getBody()).containsKey("recoveryCodes")).isTrue();
        assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();
        assertThat(request.getSession().getAttribute("TOTP_PENDING_SECRET")).isNull();
    }
    @Test void expiredSecondFactorCannotAuthenticate(){
        var request=new MockHttpServletRequest();var session=request.getSession();
        session.setAttribute("TOTP_PENDING_LOGIN","user");session.setAttribute("TOTP_PENDING_AT",System.currentTimeMillis()-300_001);
        assertThat(controller.loginTotp(new AuthController.TotpCodeRequest("123456"),request,new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(totp,details);
    }
    @Test void sixthFailedPasswordAttemptIsThrottled(){
        when(details.loadUserByUsername("user")).thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("not found"));
        for(int i=0;i<5;i++)assertThat(controller.login(new AuthController.LoginRequest("user","wrong"),new MockHttpServletRequest(),new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(401);
        var result=controller.login(new AuthController.LoginRequest("user","wrong"),new MockHttpServletRequest(),new MockHttpServletResponse());
        assertThat(result.getStatusCode().value()).isEqualTo(429);assertThat(result.getHeaders().getFirst("Retry-After")).isEqualTo("300");
    }
    @Test void wrongEnrollmentCodeNeverAuthenticates(){
        account("ADMIN");when(totp.setup("user")).thenReturn(new TotpService.Setup("secret","uri","qr"));
        when(totp.enable("user","secret","wrong")).thenThrow(new IllegalArgumentException("invalid"));
        var request=new MockHttpServletRequest();controller.login(new AuthController.LoginRequest("user","password"),request,new MockHttpServletResponse());
        assertThat(controller.loginTotp(new AuthController.TotpCodeRequest("wrong"),request,new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(401);
        assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
    }
    @Test void sixthFailedSecondFactorAttemptIsThrottled(){
        account("ADMIN");when(totp.enabled("user")).thenReturn(true);
        var request=new MockHttpServletRequest();controller.login(new AuthController.LoginRequest("user","password"),request,new MockHttpServletResponse());
        for(int i=0;i<5;i++)assertThat(controller.loginTotp(new AuthController.TotpCodeRequest("wrong"),request,new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.loginTotp(new AuthController.TotpCodeRequest("wrong"),request,new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(429);
        verify(totp,times(5)).verifyLogin("user","wrong");
        assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
    }
}
