package de.ostms.lc.user.api;
import de.ostms.lc.user.service.*;
import de.ostms.lc.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetControllerTest {
    @Test void acceptedAndThrottledRequestsHaveExactlySameResponse(){
        var service=mock(PasswordResetService.class);var limiter=mock(PasswordResetLimiter.class);
        var controller=new PasswordResetController(service,limiter,mock(AuditService.class));
        var request=new MockHttpServletRequest();request.setRemoteAddr("client");
        when(limiter.allow("request","client","user")).thenReturn(true,false);
        var body=new PasswordResetController.Request("user","user@example.com");
        assertThat(controller.request(body,request)).isEqualTo(controller.request(body,request));
        verify(service,times(1)).request("user","user@example.com");
    }
}
