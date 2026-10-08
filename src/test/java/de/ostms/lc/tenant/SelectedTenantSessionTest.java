package de.ostms.lc.tenant;
import de.ostms.lc.config.CredentialSessionFilter;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.service.TenantMembershipService;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.user.service.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SelectedTenantSessionTest {
 final UUID tenant=UUID.randomUUID(),userId=UUID.randomUUID();final AppUser user=new AppUser();
 final AppUserRepository users=mock(AppUserRepository.class);final TenantMembershipService memberships=mock(TenantMembershipService.class);
 TenantMembershipService.Access selected;
 @BeforeEach void setup(){ReflectionTestUtils.setField(user,"id",userId);user.setUsername("synthetic-user");user.setPasswordHash("hash");user.setRole(UserRole.VIEWER);
  selected=new TenantMembershipService.Access(tenant,userId,UUID.randomUUID(),UUID.randomUUID(),UserRole.VIEWER,Set.of(UserPermission.LC_EDIT));
  when(users.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));when(memberships.requireActiveAccess(userId)).thenAnswer(invocation->{assertThat(TenantContext.currentId()).isEqualTo(tenant);return selected;});
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.getUsername(),null,List.of()));
 }
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
 MockHttpServletRequest request(String path){var request=new MockHttpServletRequest("GET",path);var session=request.getSession();session.setAttribute(CredentialSessionFilter.TENANT,tenant);session.setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of("hash"));session.setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());session.setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,AuthorizationStamp.of(selected));return request;}
 @Test void businessRequestUsesSelectedMembershipAndRestoresContext()throws Exception{
  var req=request("/api/lcs");new CredentialSessionFilter(users,memberships).doFilter(req,new MockHttpServletResponse(),(r,s)->assertThat(TenantContext.currentId()).isEqualTo(tenant));assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);
 }
 @Test void identitySelfServiceUsesHomeScopeButChecksSelectedAccessFirst()throws Exception{
  new CredentialSessionFilter(users,memberships).doFilter(request("/api/profile"),new MockHttpServletResponse(),(r,s)->assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID));verify(memberships).requireActiveAccess(userId);
 }
 @Test void suspendedOrMissingMembershipInvalidatesSession()throws Exception{
  doThrow(new AccessDeniedException("Suspended")).when(memberships).requireActiveAccess(userId);var req=request("/api/lcs");var session=(MockHttpSession)req.getSession();var response=new MockHttpServletResponse();
  new CredentialSessionFilter(users,memberships).doFilter(req,response,(r,s)->fail("Unauthorized chain executed"));assertThat(response.getStatus()).isEqualTo(401);assertThat(session.isInvalid()).isTrue();
 }
 @Test void changedMembershipPermissionsInvalidateSession()throws Exception{
  var req=request("/api/lcs");selected=new TenantMembershipService.Access(tenant,userId,UUID.randomUUID(),UUID.randomUUID(),UserRole.VIEWER,Set.of());var response=new MockHttpServletResponse();
  new CredentialSessionFilter(users,memberships).doFilter(req,response,(r,s)->fail("Stale permissions accepted"));assertThat(response.getStatus()).isEqualTo(401);
 }
 @Test void malformedSelectedTenantDoesNotFallBackToDefault()throws Exception{
  var req=request("/api/lcs");req.getSession().setAttribute(CredentialSessionFilter.TENANT,"untrusted-string");var response=new MockHttpServletResponse();new CredentialSessionFilter(users,memberships).doFilter(req,response,(r,s)->fail("Malformed selection accepted"));assertThat(response.getStatus()).isEqualTo(401);verifyNoInteractions(memberships);
 }
}
