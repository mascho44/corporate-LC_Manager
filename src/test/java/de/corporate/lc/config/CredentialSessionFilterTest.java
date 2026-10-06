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
    @Test void changedRolePermissionsRevokeExistingSession()throws Exception{
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setPasswordHash("hash");var role=new de.corporate.lc.user.domain.AppRole();role.setBaseRole(de.corporate.lc.user.domain.UserRole.EDITOR);role.setPermissions(Set.of(de.corporate.lc.user.domain.UserPermission.LC_EDIT));user.setAssignedRole(role);
        when(users.findByUsernameIgnoreCase("user")).thenReturn(Optional.of(user));
        var request=new MockHttpServletRequest("GET","/api/lcs");var session=request.getSession();session.setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of("hash"));session.setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());session.setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,de.corporate.lc.user.service.AuthorizationStamp.of(user));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user",null,List.of()));role.setPermissions(Set.of());
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);new CredentialSessionFilter(users).doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(401);assertThat(((MockHttpSession)session).isInvalid()).isTrue();verifyNoInteractions(chain);
    }
    @Test void legacySessionWithoutAuthorizationStampRequiresNewLogin()throws Exception{
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setPasswordHash("hash");when(users.findByUsernameIgnoreCase("user")).thenReturn(Optional.of(user));
        var request=new MockHttpServletRequest("GET","/api/lcs");request.getSession().setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of("hash"));request.getSession().setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user",null,List.of()));var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new CredentialSessionFilter(users).doFilter(request,response,chain);assertThat(response.getStatus()).isEqualTo(401);verifyNoInteractions(chain);
    }
    @Test void browserTenantHeaderCannotChangeServerScopeAndScopeIsClearedOnFailure()throws Exception{
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setPasswordHash("hash");when(users.findByUsernameIgnoreCase("user")).thenReturn(Optional.of(user));
        var request=new MockHttpServletRequest("GET","/api/lcs");request.addHeader("X-Tenant-ID",UUID.randomUUID().toString());
        request.getSession().setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of("hash"));request.getSession().setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,de.corporate.lc.user.service.AuthorizationStamp.of(user));request.getSession().setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user",null,List.of()));
        assertThatThrownBy(()->new CredentialSessionFilter(users).doFilter(request,new MockHttpServletResponse(),(req,res)->{
            assertThat(de.corporate.lc.tenant.domain.TenantContext.currentId()).isEqualTo(user.getTenantId());throw new jakarta.servlet.ServletException("synthetic");
        })).isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(de.corporate.lc.tenant.domain.TenantContext.currentId()).isEqualTo(de.corporate.lc.tenant.domain.Tenant.DEFAULT_ID);
    }
    @Test void inconsistentTenantRoleRevokesAnExistingSession()throws Exception{
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setPasswordHash("hash");var role=new de.corporate.lc.user.domain.AppRole();org.springframework.test.util.ReflectionTestUtils.setField(role,"tenantId",UUID.randomUUID());user.setAssignedRole(role);
        when(users.findByUsernameIgnoreCase("user")).thenReturn(Optional.of(user));var request=new MockHttpServletRequest("GET","/api/lcs");request.getSession().setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of("hash"));request.getSession().setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,de.corporate.lc.user.service.AuthorizationStamp.of(user));request.getSession().setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user",null,List.of()));var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new CredentialSessionFilter(users).doFilter(request,response,chain);assertThat(response.getStatus()).isEqualTo(401);verifyNoInteractions(chain);
    }
    @Test void authenticatedContextWithoutVerifiedSessionIsRejected()throws Exception{
        var request=new MockHttpServletRequest("GET","/api/lcs");SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user",null,List.of()));
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);new CredentialSessionFilter(mock(AppUserRepository.class)).doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(401);verifyNoInteractions(chain);
    }
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
        var request=new MockHttpServletRequest("GET","/api/lcs");request.getSession().setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of("hash"));request.getSession().setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,de.corporate.lc.user.service.AuthorizationStamp.of(user));
        request.getSession().setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user",null,List.of()));
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new CredentialSessionFilter(users).doFilter(request,response,chain);verify(chain).doFilter(request,response);
    }
    @Test void absoluteLifetimeRevokesSession()throws Exception{
        var users=mock(AppUserRepository.class);var request=new MockHttpServletRequest("GET","/api/lcs");
        request.getSession().setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis()-28_800_001);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user",null,List.of()));
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new CredentialSessionFilter(users).doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(401);verifyNoInteractions(chain);
    }
    @Test void adminWithoutSecondFactorCannotUseExistingSession()throws Exception{
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setPasswordHash("hash");user.setRole(de.corporate.lc.user.domain.UserRole.ADMIN);
        when(users.findByUsernameIgnoreCase("user")).thenReturn(Optional.of(user));
        var request=new MockHttpServletRequest("GET","/api/lcs");request.getSession().setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());
        request.getSession().setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of("hash"));request.getSession().setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,de.corporate.lc.user.service.AuthorizationStamp.of(user));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user",null,List.of()));
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new CredentialSessionFilter(users).doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(401);verifyNoInteractions(chain);
    }
}
