package de.ostms.lc.tenant;
import de.ostms.lc.config.*;
import de.ostms.lc.tenant.api.TenantWorkspaceController;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.service.*;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.user.service.*;
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

@WebMvcTest({de.ostms.lc.user.api.PlatformInvitationController.class,de.ostms.lc.user.api.InvitationAcceptanceController.class,de.ostms.lc.user.api.PlatformAccountCreationController.class,de.ostms.lc.user.api.PlatformAdministrationController.class,TenantWorkspaceController.class,de.ostms.lc.tenant.api.TenantSettingsController.class,de.ostms.lc.tenant.api.TenantReadinessController.class}) @Import(SecurityConfig.class)
class TenantWorkspaceSecurityTest {
 @Autowired MockMvc mvc;
 @MockitoBean TenantWorkspaceService workspaces;
 @MockitoBean TenantSettingsService settings;
 @MockitoBean TenantReadinessService readiness;
 @MockitoBean PlatformAdministrationService platform;
 @MockitoBean de.ostms.lc.audit.service.AuditService audit;
 @MockitoBean PlatformAccountCreationService accountCreation;
 @MockitoBean PlatformInvitationService invitations;
 @MockitoBean PasswordResetLimiter invitationLimiter;
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
 @Test void anonymousCannotReadSettings()throws Exception{mvc.perform(get("/api/tenants/current/settings")).andExpect(status().isUnauthorized());verifyNoInteractions(settings);}
 @Test void anonymousCannotReadGlobalAccounts()throws Exception{mvc.perform(get("/api/platform/users")).andExpect(status().isUnauthorized());verifyNoInteractions(platform);}
 @Test void platformSessionCannotBeOpenedWithOnlyLocalPermissions()throws Exception{
  doThrow(new org.springframework.security.access.AccessDeniedException("No global grant")).when(platform).verifyLiveAccess(any());
  mvc.perform(get("/api/platform/session").session(session())).andExpect(status().isForbidden());
 }
 @Test void globalSessionOnlyReturnsIdentityAndCsrfAndLogoutRequiresCsrf()throws Exception{
  var session=session();var token=token(session);
  mvc.perform(get("/api/platform/session").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("synthetic-viewer")).andExpect(jsonPath("$.csrfToken").isString()).andExpect(jsonPath("$.permissions").doesNotExist()).andExpect(jsonPath("$.tenantId").doesNotExist());
  mvc.perform(post("/api/platform/logout").session(session)).andExpect(status().isForbidden());verifyNoInteractions(audit);
  mvc.perform(post("/api/platform/logout").session(session).header(token.getHeaderName(),token.getToken())).andExpect(status().isNoContent());
  verify(audit).record(any(org.springframework.security.core.Authentication.class),eq("LOGOUT"),eq("SESSION"),isNull(),eq("Platform sign-out"));
 }
 @Test void invitationAndGrantMutationsRequireCsrfAndValidateRequiredFields()throws Exception{
  var session=session();var token=token(session);String body="{\"username\":\"new\",\"displayName\":\"New\",\"email\":\"new@example.invalid\",\"tenantId\":\""+target+"\",\"roleId\":\""+target+"\"}";
  mvc.perform(post("/api/platform/invitations").session(session).contentType("application/json").content(body)).andExpect(status().isForbidden());verifyNoInteractions(invitations);
  mvc.perform(post("/api/platform/invitations").session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content(body.replace("new@example.invalid",""))).andExpect(status().isBadRequest());verifyNoInteractions(invitations);
  mvc.perform(post("/api/platform/invitations").session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content(body)).andExpect(status().isCreated());verify(invitations).invite(eq("new"),eq("New"),eq("new@example.invalid"),eq(target),eq(target),any());
  String url="/api/platform/users/"+target+"/platform-grant";
  mvc.perform(put(url).session(session).contentType("application/json").content("{\"granted\":true}")).andExpect(status().isForbidden());verify(platform,never()).changePlatformGrant(any(),anyBoolean(),any());
  mvc.perform(put(url).session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
  mvc.perform(put(url).session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{\"granted\":true}")).andExpect(status().isOk());verify(platform).changePlatformGrant(eq(target),eq(true),any());
 }
 @Test void anonymousAcceptanceRequiresSecretAndIsRateLimited()throws Exception{
  when(invitationLimiter.allow(eq("invite-accept"),any(),eq(""))).thenReturn(true,false);String body="{\"token\":\""+"A".repeat(43)+"\",\"password\":\"Synthetic123!\"}";
  mvc.perform(post("/api/auth/invitation/accept").contentType("application/json").content(body)).andExpect(status().isOk());verify(invitations).accept("A".repeat(43),"Synthetic123!");
  mvc.perform(post("/api/auth/invitation/accept").contentType("application/json").content(body)).andExpect(status().isBadRequest());verify(invitations,times(1)).accept(any(),any());
  mvc.perform(post("/api/auth/invitation/accept").contentType("application/json").content(body.replace("A".repeat(43),"invalid"))).andExpect(status().isBadRequest());verify(invitations,times(1)).accept(any(),any());
 }
 @Test void accountCreationRequiresCsrfAndValidEmail()throws Exception{
  var session=session();var token=token(session);String body="{\"username\":\"new\",\"displayName\":\"New\",\"email\":\"new@example.invalid\",\"password\":\"Synthetic123!\"}";
  mvc.perform(post("/api/platform/users").session(session).contentType("application/json").content(body)).andExpect(status().isForbidden());verifyNoInteractions(accountCreation);
  mvc.perform(post("/api/platform/users").session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content(body.replace("new@example.invalid",""))).andExpect(status().isBadRequest());verifyNoInteractions(accountCreation);
  mvc.perform(post("/api/platform/users").session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content(body)).andExpect(status().isCreated());verify(accountCreation).create(eq("new"),eq("New"),eq("new@example.invalid"),eq("Synthetic123!"),any());
 }
 @Test void accountCreationServiceDenialIsForbidden()throws Exception{
  when(accountCreation.create(any(),any(),any(),any(),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));var session=session();var token=token(session);
  mvc.perform(post("/api/platform/users").session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{\"username\":\"new\",\"displayName\":\"New\",\"email\":\"new@example.invalid\",\"password\":\"Synthetic123!\"}")).andExpect(status().isForbidden());
 }
 @Test void localRoleCannotOverridePlatformServiceDenial()throws Exception{when(platform.accounts(any())).thenThrow(new org.springframework.security.access.AccessDeniedException("Platform access denied"));mvc.perform(get("/api/platform/users").session(session())).andExpect(status().isForbidden());}
 @Test void globalAccessUpdateRequiresCsrfAndRequiredBoolean()throws Exception{
  var session=session();var token=token(session);String url="/api/platform/users/"+target+"/access";
  mvc.perform(put(url).session(session).contentType("application/json").content("{\"active\":false}")).andExpect(status().isForbidden());verifyNoInteractions(platform);
  mvc.perform(put(url).session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());verifyNoInteractions(platform);
  mvc.perform(put(url).session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{\"active\":false}")).andExpect(status().isOk());verify(platform).changeAccess(eq(target),eq(false),any());
 }
 @Test void memberCanReadProfileMetadataWithoutEditingPermission()throws Exception{
  when(settings.get(any())).thenReturn(new TenantSettingsService.Settings(Tenant.DEFAULT_ID,"default","Synthetic","en",false,false,true));
  mvc.perform(get("/api/tenants/current/settings").session(session())).andExpect(status().isOk()).andExpect(jsonPath("$.editingEnabled").value(false)).andExpect(jsonPath("$.bankEnabled").value(false)).andExpect(jsonPath("$.corporateEnabled").value(true));
 }
 @Test void anonymousCannotReadSetupCounts()throws Exception{mvc.perform(get("/api/tenants/current/readiness")).andExpect(status().isUnauthorized());verifyNoInteractions(readiness);}
 @Test void ordinaryUserCannotReadSetupCounts()throws Exception{mvc.perform(get("/api/tenants/current/readiness").session(session())).andExpect(status().isForbidden());verifyNoInteractions(readiness);}
 @Test void settingsUpdateRequiresCsrfAndPassesOnlyPresentationFields()throws Exception{
  var session=session();var token=token(session);var body="{\"name\":\"Synthetic renamed\",\"defaultLanguage\":\"de\"}";
  mvc.perform(put("/api/tenants/current/settings").session(session).contentType("application/json").content(body)).andExpect(status().isForbidden());verifyNoInteractions(settings);
  when(settings.update(eq("Synthetic renamed"),eq("de"),any())).thenReturn(new TenantSettingsService.Settings(Tenant.DEFAULT_ID,"default","Synthetic renamed","de",true,true,false));
  mvc.perform(put("/api/tenants/current/settings").session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Synthetic renamed"));
 }
 @Test void settingsServiceDenialReturnsForbidden()throws Exception{
  var session=session();var token=token(session);when(settings.update(any(),any(),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("Not an administrator"));
  mvc.perform(put("/api/tenants/current/settings").session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{\"name\":\"Blocked\",\"defaultLanguage\":\"en\"}")).andExpect(status().isForbidden());
 }
}
