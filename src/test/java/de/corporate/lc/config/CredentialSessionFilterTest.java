package de.corporate.lc.config;
import de.corporate.lc.user.domain.AppUser;
import de.corporate.lc.user.repository.AppUserRepository;
import de.corporate.lc.user.service.CredentialStamp;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.FilterChain;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CredentialSessionFilterTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void changedPasswordRevokesSessionBeforeProtectedRequest()throws Exception{
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setPasswordHash("new-hash");
        when(users.findByUsernameIgnoreCase("user")).thenReturn(Optional.of(user));
        var request=new MockHttpServletRequest("GET","/api/lcs");var session=new MockHttpSession();session.setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of("old-hash"));request.setSession(session);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user",null,List.of()));
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new CredentialSessionFilter(users).doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(401);assertThat(session.isInvalid()).isTrue();verifyNoInteractions(chain);
    }
    @Test void unchangedPasswordAllowsSession()throws Exception{
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setPasswordHash("hash");
        when(users.findByUsernameIgnoreCase("user")).thenReturn(Optional.of(user));
        var request=new MockHttpServletRequest("GET","/api/lcs");request.getSession().setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of("hash"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user",null,List.of()));
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new CredentialSessionFilter(users).doFilter(request,response,chain);verify(chain).doFilter(request,response);
    }
}
