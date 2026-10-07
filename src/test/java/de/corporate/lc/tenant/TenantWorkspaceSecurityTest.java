package de.corporate.lc.tenant;
import de.corporate.lc.config.*;
import de.corporate.lc.tenant.api.TenantWorkspaceController;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.service.*;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.AppUserRepository;
import de.corporate.lc.user.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TenantWorkspaceController.class) @Import(SecurityConfig.class)
class TenantWorkspaceSecurityTest {
 @Autowired MockMvc mvc;
 @MockitoBean TenantWorkspaceService workspaces;
 @MockitoBean TenantMembershipService memberships;
 @MockitoBean AppUserDetailsService details;
 @MockitoBean AppUserRepository users;
 final UUID userId=UUID.randomUUID(),target=UUID.randomUUID();
 MockHttpSession session(){
  var user=new AppUser();org.springframework.test.util.ReflectionTestUtils.setField(user,"id",userId);user.setUsername("synthetic-viewer");user.setPasswordHash("hash");user.setRole(UserRole.VIEWER);
  when(users.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));
  when(memberships.requireActiveAccess(userId)).thenAnswer(i->new TenantMembershipService.Access(TenantContext.currentId(),userId,UUID.randomUUID(),UUID.randomUUID(),UserRole.VIEWER,Set.of()));
  var session=new MockHttpSession();session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(user.getUsername(),null,List.of(new SimpleGrantedAuthority("ROLE_VIEWER")))));
  session.setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of("hash"));session.setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());session.setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,AuthorizationStamp.of(user));
  when(workspaces.overview(any())).thenReturn(new TenantWorkspaceService.Overview(Tenant.DEFAULT_ID,List.of(),false));return session;
 }
 CsrfToken token(MockHttpSession session)throws Exception{return (CsrfToken)mvc.perform(get("/api/tenants").session(session)).andExpect(status().isOk()).andReturn().getRequest().getAttribute(CsrfToken.class.getName());}
 @Test void anonymousCannotEnumerateTenants()throws Exception{mvc.perform(get("/api/tenants")).andExpect(status().isUnauthorized());verifyNoInteractions(workspaces);}
 @Test void ordinaryUserCannotCreateTenantWithValidCsrf()throws Exception{
  var session=session();var token=token(session);mvc.perform(post("/api/tenants").session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{\"code\":\"synthetic\",\"name\":\"Synthetic\",\"defaultLanguage\":\"en\"}")).andExpect(status().isForbidden());verify(workspaces,never()).create(any(),any(),any(),anyBoolean(),anyBoolean(),any());
 }
 @Test void selectionRequiresCsrfAndSavesOnlyVerifiedMembership()throws Exception{
  var session=session();var token=token(session);var selected=new TenantMembershipService.Access(target,userId,UUID.randomUUID(),UUID.randomUUID(),UserRole.EDITOR,Set.of(UserPermission.LC_EDIT));when(workspaces.select(eq(target),any())).thenReturn(selected);
  mvc.perform(post("/api/tenants/"+target+"/select").session(session)).andExpect(status().isForbidden());verify(workspaces,never()).select(any(),any());
  mvc.perform(post("/api/tenants/"+target+"/select").session(session).header(token.getHeaderName(),token.getToken())).andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(target.toString()));
  assertThat(session.getAttribute(CredentialSessionFilter.TENANT)).isEqualTo(target);assertThat(session.getAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP)).isEqualTo(AuthorizationStamp.of(selected));
  var context=(SecurityContextImpl)session.getAttribute("SPRING_SECURITY_CONTEXT");assertThat(context.getAuthentication().getAuthorities()).extracting("authority").containsExactlyInAnyOrder("ROLE_EDITOR","PERM_LC_EDIT");
 }
 @Test void rejectedSelectionLeavesSessionUnchanged()throws Exception{
  var session=session();var token=token(session);var before=session.getAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP);when(workspaces.select(eq(target),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("No membership"));
  mvc.perform(post("/api/tenants/"+target+"/select").session(session).header(token.getHeaderName(),token.getToken())).andExpect(status().isForbidden());assertThat(session.getAttribute(CredentialSessionFilter.TENANT)).isNull();assertThat(session.getAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP)).isEqualTo(before);
 }
}
