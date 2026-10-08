package de.ostms.lc.user.service;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.service.TenantMembershipService;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.config.CredentialSessionFilter;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.FilterChain;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MembershipAuthenticationTest {
 final AppUserRepository users=mock(AppUserRepository.class);
 final TenantMembershipService memberships=mock(TenantMembershipService.class);
 final AppUser user=new AppUser();
 @BeforeEach void setup(){
  user.setUsername("synthetic-user");user.setPasswordHash("hash");user.setRole(UserRole.ADMIN);
  org.springframework.test.util.ReflectionTestUtils.setField(user,"id",UUID.randomUUID());
  when(users.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void loginUsesMembershipRightsInsteadOfHomeAdministratorRights(){
  var access=new TenantMembershipService.Access(Tenant.DEFAULT_ID,user.getId(),UUID.randomUUID(),UUID.randomUUID(),UserRole.VIEWER,Set.of(UserPermission.DOCUMENT_REVIEW));
  when(memberships.requireActiveAccess(user.getId())).thenAnswer(call->{assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);return access;});
  var details=new AppUserDetailsService(users,memberships).loadUserByUsername(user.getUsername());
  assertThat(details.getAuthorities()).extracting("authority").containsExactlyInAnyOrder("ROLE_VIEWER","PERM_DOCUMENT_REVIEW");
  assertThat(AuthorizationStamp.of(details.getAuthorities())).isEqualTo(AuthorizationStamp.of(access));
 }
 @Test void loginRejectsMissingOrInactiveMembership(){
  when(memberships.requireActiveAccess(user.getId())).thenThrow(new AccessDeniedException("synthetic"));
  assertThatThrownBy(()->new AppUserDetailsService(users,memberships).loadUserByUsername(user.getUsername())).isInstanceOf(DisabledException.class);
 }
 @Test void revokedMembershipInvalidatesExistingSession()throws Exception{
  user.setRole(UserRole.VIEWER);
  when(memberships.requireActiveAccess(user.getId())).thenThrow(new AccessDeniedException("synthetic"));
  var request=new MockHttpServletRequest("GET","/api/lcs");var session=request.getSession();
  session.setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of("hash"));
  session.setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());
  session.setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,AuthorizationStamp.of(user));
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.getUsername(),null,List.of()));
  var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
  new CredentialSessionFilter(users,memberships).doFilter(request,response,chain);
  assertThat(response.getStatus()).isEqualTo(401);assertThat(((MockHttpSession)session).isInvalid()).isTrue();verifyNoInteractions(chain);
  assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);
 }
}
