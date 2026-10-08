package de.ostms.lc.tenant;
import de.ostms.lc.config.*;
import de.ostms.lc.tenant.api.PlatformTenantController;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.service.*;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.user.service.*;
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
import org.springframework.security.web.csrf.CsrfToken;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(PlatformTenantController.class) @Import(SecurityConfig.class)
class PlatformTenantSecurityTest {
 @Autowired MockMvc mvc;@MockitoBean PlatformTenantService tenants;@MockitoBean AppUserDetailsService details;@MockitoBean AppUserRepository users;@MockitoBean TenantMembershipService memberships;
 final UUID id=UUID.randomUUID();
 @MockitoBean TenantInventoryService inventory;
 @Test void anonymousCannotInspectTenantInventory()throws Exception{mvc.perform(get("/api/platform/tenants/"+id+"/inventory")).andExpect(status().isUnauthorized());verifyNoInteractions(inventory);}
 @Test void inventoryGetNeedsNoCsrfAndHonorsServiceDenial()throws Exception{var s=session();doThrow(new org.springframework.security.access.AccessDeniedException("Platform access required")).when(inventory).preview(eq(id),any());mvc.perform(get("/api/platform/tenants/"+id+"/inventory").session(s)).andExpect(status().isForbidden());verify(inventory).preview(eq(id),any());}
 MockHttpSession session(){var user=new AppUser();org.springframework.test.util.ReflectionTestUtils.setField(user,"id",id);user.setUsername("synthetic-platform");user.setPasswordHash("synthetic-hash");user.setRole(UserRole.VIEWER);user.setTotpEnabled(true);user.setPlatformAdministrator(true);when(users.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));when(memberships.requireActiveAccess(id)).thenReturn(new TenantMembershipService.Access(Tenant.DEFAULT_ID,id,UUID.randomUUID(),UUID.randomUUID(),UserRole.VIEWER,Set.of()));var s=new MockHttpSession();s.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(user.getUsername(),null,List.of(new SimpleGrantedAuthority("ROLE_VIEWER")))));s.setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of(user.getPasswordHash()));s.setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());s.setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,AuthorizationStamp.of(user));s.setAttribute(CredentialSessionFilter.TOTP_VERIFIED,true);when(tenants.list(any())).thenReturn(List.of());return s;}
 @Test void anonymousCannotListGlobalTenants()throws Exception{mvc.perform(get("/api/platform/tenants")).andExpect(status().isUnauthorized());verifyNoInteractions(tenants);}
 @Test void mutationRequiresCsrfAndAllFlagsWithoutLocalAdminRole()throws Exception{var s=session();var token=(CsrfToken)mvc.perform(get("/api/platform/tenants").session(s)).andExpect(status().isOk()).andReturn().getRequest().getAttribute(CsrfToken.class.getName());String url="/api/platform/tenants/"+id;String body="{\"active\":true,\"bankEnabled\":true,\"corporateEnabled\":false}";mvc.perform(put(url).session(s).contentType("application/json").content(body)).andExpect(status().isForbidden());mvc.perform(put(url).session(s).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{\"active\":true}")).andExpect(status().isBadRequest());verify(tenants,never()).update(any(),anyBoolean(),anyBoolean(),anyBoolean(),any());mvc.perform(put(url).session(s).header(token.getHeaderName(),token.getToken()).contentType("application/json").content(body)).andExpect(status().isOk());verify(tenants).update(eq(id),eq(true),eq(true),eq(false),any());}
}
