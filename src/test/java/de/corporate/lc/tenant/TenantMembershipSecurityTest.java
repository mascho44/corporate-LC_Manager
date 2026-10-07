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
 @MockitoBean AppUserDetailsService details;
 @MockitoBean AppUserRepository users;
 MockHttpSession session(boolean allowed){
  var user=new AppUser();user.setUsername("synthetic-user");user.setPasswordHash("synthetic-hash");user.setRole(UserRole.EDITOR);when(users.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));
  when(memberships.requireActiveAccess(org.mockito.ArgumentMatchers.nullable(UUID.class))).thenReturn(new de.corporate.lc.tenant.service.TenantMembershipService.Access(user.getTenantId(),user.getId(),UUID.randomUUID(),UUID.randomUUID(),user.getRole(),Set.copyOf(user.effectivePermissions())));
  var session=new MockHttpSession();session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(new UsernamePasswordAuthenticationToken(user.getUsername(),null,List.of(new SimpleGrantedAuthority(allowed?"PERM_USER_MANAGE":"PERM_LC_EDIT")))));session.setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of(user.getPasswordHash()));session.setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,AuthorizationStamp.of(user));session.setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());return session;
 }
 @Test void anonymousAndOrdinaryUsersCannotReadMemberships()throws Exception{
  mvc.perform(get("/api/users/memberships")).andExpect(status().isUnauthorized());mvc.perform(get("/api/users/memberships").session(session(false))).andExpect(status().isForbidden());verify(memberships,never()).list();verifyNoInteractions(tenants);
 }
 @Test void authorizedOverviewIsRedactedAndSwitchingRemainsDisabled()throws Exception{
  when(tenants.findById(Tenant.DEFAULT_ID)).thenReturn(Optional.of(new Tenant()));when(memberships.list()).thenReturn(List.of(new TenantMembershipService.Membership(Tenant.DEFAULT_ID,UUID.randomUUID(),"synthetic-user",UUID.randomUUID(),"Synthetic role",Set.of(UserPermission.USER_MANAGE),true)));
  mvc.perform(get("/api/users/memberships").session(session(true))).andExpect(status().isOk()).andExpect(jsonPath("$.switchingEnabled").value(false)).andExpect(jsonPath("$.tenantId").value(Tenant.DEFAULT_ID.toString())).andExpect(jsonPath("$.memberships[0].permissions[0]").value("USER_MANAGE")).andExpect(jsonPath("$.memberships[0].passwordHash").doesNotExist()).andExpect(jsonPath("$.memberships[0].totpSecretEncrypted").doesNotExist());verify(tenants).findById(Tenant.DEFAULT_ID);
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
