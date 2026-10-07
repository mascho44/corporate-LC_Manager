package de.corporate.lc.tenant;
import de.corporate.lc.config.*;
import de.corporate.lc.tenant.api.TenantMembershipController;
import de.corporate.lc.tenant.domain.Tenant;
import de.corporate.lc.tenant.repository.TenantRepository;
import de.corporate.lc.tenant.service.TenantMembershipService;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.AppUserRepository;
import de.corporate.lc.user.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TenantMembershipController.class) @Import(SecurityConfig.class)
class TenantMembershipSecurityTest {
 @Autowired MockMvc mvc;
 @MockitoBean TenantMembershipService memberships;
 @MockitoBean TenantRepository tenants;
 @MockitoBean de.corporate.lc.tenant.service.TenantMembershipAdministrationService administration;
 @MockitoBean RoleService roles;
 @MockitoBean de.corporate.lc.audit.service.AuditService audit;
 @MockitoBean AppUserDetailsService details;
 @MockitoBean AppUserRepository users;
 @Test void roleChoicesIncludeOnlyRoleMetadataAndPermissions()throws Exception{
  when(tenants.findById(Tenant.DEFAULT_ID)).thenReturn(Optional.of(new Tenant()));
  when(roles.all()).thenReturn(List.of(new de.corporate.lc.user.api.RoleView(UUID.randomUUID(),"Synthetic reviewer",de.corporate.lc.user.domain.UserRole.VIEWER,false,Set.of(UserPermission.DOCUMENT_REVIEW))));
  mvc.perform(get("/api/users/memberships").session(session(true))).andExpect(status().isOk()).andExpect(jsonPath("$.roleChoices[0].permissions[0]").value("DOCUMENT_REVIEW")).andExpect(jsonPath("$.roleChoices[0].passwordHash").doesNotExist());
 }
 @Test void membershipSuspensionIsProtectedValidatedAndAudited()throws Exception{
  var id=UUID.randomUUID();var roleId=UUID.randomUUID();
  when(tenants.findById(Tenant.DEFAULT_ID)).thenReturn(Optional.of(new Tenant()));
  var before=new TenantMembershipService.Membership(Tenant.DEFAULT_ID,id,"other-user",roleId,"Viewer",Set.of(),true);
  var after=new TenantMembershipService.Membership(Tenant.DEFAULT_ID,id,"other-user",roleId,"Viewer",Set.of(),false,true,true);
  when(administration.getForAdministration(id)).thenReturn(before);when(administration.updateSuspension(id,true,"synthetic-user")).thenReturn(after);
  var allowed=session(true);var token=(org.springframework.security.web.csrf.CsrfToken)mvc.perform(get("/api/users/memberships").session(allowed)).andExpect(status().isOk()).andExpect(jsonPath("$.accessEditingEnabled").value(true)).andReturn().getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
  var route="/api/users/memberships/"+id+"/access";
  mvc.perform(put(route).session(allowed).contentType("application/json").content("{\"suspended\":true}")).andExpect(status().isForbidden());verifyNoInteractions(administration,audit);
  mvc.perform(put(route).session(allowed).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());verifyNoInteractions(administration,audit);
  mvc.perform(put(route).session(allowed).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{\"suspended\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.identityActive").value(true)).andExpect(jsonPath("$.suspended").value(true));
  verify(audit).recordChangeInTransaction(any(),eq("USER_MEMBERSHIP_ACCESS_UPDATED"),eq("MEMBERSHIP"),eq(id),anyString(),contains("\"suspended\":false"),contains("\"suspended\":true"));
 }
 @Test void ordinaryUserCannotChangeAccessWithValidCsrf()throws Exception{
  var ordinary=session(false);var token=(org.springframework.security.web.csrf.CsrfToken)mvc.perform(get("/api/users/memberships").session(ordinary)).andExpect(status().isForbidden()).andReturn().getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
  mvc.perform(put("/api/users/memberships/"+UUID.randomUUID()+"/access").session(ordinary).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{\"suspended\":false}")).andExpect(status().isForbidden());verifyNoInteractions(administration,audit);
 }
 @Test void membershipRoleUpdateRequiresPermissionAndCsrf()throws Exception{
  var id=UUID.randomUUID();var roleId=UUID.randomUUID();
  when(tenants.findById(Tenant.DEFAULT_ID)).thenReturn(Optional.of(new Tenant()));
  var before=new TenantMembershipService.Membership(Tenant.DEFAULT_ID,id,"synthetic-user",UUID.randomUUID(),"Old role",Set.of(UserPermission.LC_EDIT),true);
  var after=new TenantMembershipService.Membership(Tenant.DEFAULT_ID,id,"synthetic-user",roleId,"New role",Set.of(UserPermission.DOCUMENT_REVIEW),true);
  when(administration.getForAdministration(id)).thenReturn(before);when(administration.updateRole(id,roleId,"synthetic-user")).thenReturn(after);
  var allowed=session(true);
  var token=(org.springframework.security.web.csrf.CsrfToken)mvc.perform(get("/api/users/memberships").session(allowed)).andExpect(status().isOk()).andExpect(jsonPath("$.roleEditingEnabled").value(true)).andReturn().getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
  var body="{\"roleId\":\""+roleId+"\"}";var route="/api/users/memberships/"+id+"/role";
  mvc.perform(put(route).session(allowed).contentType("application/json").content(body)).andExpect(status().isForbidden());verifyNoInteractions(administration,audit);
  mvc.perform(put(route).session(allowed).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());verifyNoInteractions(administration,audit);
  mvc.perform(put(route).session(allowed).header(token.getHeaderName(),token.getToken()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.roleId").value(roleId.toString())).andExpect(jsonPath("$.passwordHash").doesNotExist());
  verify(audit).recordChangeInTransaction(any(),eq("USER_MEMBERSHIP_ROLE_UPDATED"),eq("MEMBERSHIP"),eq(id),anyString(),contains("LC_EDIT"),contains("DOCUMENT_REVIEW"));
 }
 @Test void ordinaryUserCannotChangeMembershipRoleEvenWithValidCsrf()throws Exception{
  var ordinary=session(false);var token=(org.springframework.security.web.csrf.CsrfToken)mvc.perform(get("/api/users/memberships").session(ordinary)).andExpect(status().isForbidden()).andReturn().getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
  mvc.perform(put("/api/users/memberships/"+UUID.randomUUID()+"/role").session(ordinary).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{\"roleId\":\""+UUID.randomUUID()+"\"}")).andExpect(status().isForbidden());
  verifyNoInteractions(administration,audit);
 }
 MockHttpSession session(boolean allowed){
  var user=new AppUser();user.setUsername("synthetic-user");user.setPasswordHash("synthetic-hash");user.setRole(UserRole.EDITOR);when(users.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));
  when(memberships.requireActiveAccess(org.mockito.ArgumentMatchers.nullable(UUID.class))).thenReturn(new de.corporate.lc.tenant.service.TenantMembershipService.Access(user.getTenantId(),user.getId(),UUID.randomUUID(),UUID.randomUUID(),user.getRole(),Set.copyOf(user.effectivePermissions())));
  var session=new MockHttpSession();session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(new UsernamePasswordAuthenticationToken(user.getUsername(),null,List.of(new SimpleGrantedAuthority(allowed?"PERM_USER_MANAGE":"PERM_LC_EDIT")))));session.setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of(user.getPasswordHash()));session.setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,AuthorizationStamp.of(user));session.setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());return session;
 }
 @Test void anonymousAndOrdinaryUsersCannotReadMemberships()throws Exception{
  mvc.perform(get("/api/users/memberships")).andExpect(status().isUnauthorized());mvc.perform(get("/api/users/memberships").session(session(false))).andExpect(status().isForbidden());verify(memberships,never()).list();verifyNoInteractions(tenants);
 }
 @Test void authorizedOverviewIsRedactedAndSwitchingEnabled()throws Exception{
  when(tenants.findById(Tenant.DEFAULT_ID)).thenReturn(Optional.of(new Tenant()));when(memberships.list()).thenReturn(List.of(new TenantMembershipService.Membership(Tenant.DEFAULT_ID,UUID.randomUUID(),"synthetic-user",UUID.randomUUID(),"Synthetic role",Set.of(UserPermission.USER_MANAGE),true)));
  mvc.perform(get("/api/users/memberships").session(session(true))).andExpect(status().isOk()).andExpect(jsonPath("$.switchingEnabled").value(true)).andExpect(jsonPath("$.tenantId").value(Tenant.DEFAULT_ID.toString())).andExpect(jsonPath("$.memberships[0].permissions[0]").value("USER_MANAGE")).andExpect(jsonPath("$.memberships[0].passwordHash").doesNotExist()).andExpect(jsonPath("$.memberships[0].totpSecretEncrypted").doesNotExist());verify(tenants).findById(Tenant.DEFAULT_ID);
 }
 @Test void tenantSettingsCannotBeSelectedByClientParameters()throws Exception{
  when(tenants.findById(Tenant.DEFAULT_ID)).thenReturn(Optional.of(new Tenant()));when(memberships.list()).thenReturn(List.of());
  mvc.perform(get("/api/users/memberships").param("tenantId",UUID.randomUUID().toString()).session(session(true)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.settings.code").value("default"))
   .andExpect(jsonPath("$.settings.defaultLanguage").value("en"))
   .andExpect(jsonPath("$.settings.bankEnabled").value(true))
   .andExpect(jsonPath("$.settings.corporateEnabled").value(false));
  verify(tenants).findById(Tenant.DEFAULT_ID);verifyNoMoreInteractions(tenants);
 }
}
