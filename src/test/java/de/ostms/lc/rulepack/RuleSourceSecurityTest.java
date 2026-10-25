package de.ostms.lc.rulepack;

import de.ostms.lc.config.CredentialSessionFilter;
import de.ostms.lc.config.SecurityConfig;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.user.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RuleSourceController.class)
@Import(SecurityConfig.class)
class RuleSourceSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean de.ostms.lc.tenant.service.TenantMembershipService memberships;
    @MockitoBean RuleSourceService service;
    @MockitoBean AppUserDetailsService details;
    @MockitoBean AppUserRepository users;

    MockHttpSession session(String permission) {
        var user = new AppUser();
        user.setUsername("synthetic-user");
        user.setRole(UserRole.EDITOR);
        user.setActive(true);
        user.setPasswordHash("synthetic-hash");
        when(users.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));
        var authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of(new SimpleGrantedAuthority(permission)));
        when(memberships.requireActiveAccess(org.mockito.ArgumentMatchers.nullable(UUID.class)))
            .thenReturn(new de.ostms.lc.tenant.service.TenantMembershipService.Access(user.getTenantId(), user.getId(), UUID.randomUUID(), UUID.randomUUID(), user.getRole(), Set.copyOf(user.effectivePermissions())));
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(authentication));
        session.setAttribute(CredentialSessionFilter.STAMP, CredentialStamp.of(user.getPasswordHash()));
        session.setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP, AuthorizationStamp.of(user));
        session.setAttribute(CredentialSessionFilter.AUTHENTICATED_AT, System.currentTimeMillis());
        return session;
    }

    CsrfToken token(MockHttpSession session) throws Exception {
        return (CsrfToken) mvc.perform(get("/api/settings/rule-source").session(session)).andReturn().getRequest().getAttribute(CsrfToken.class.getName());
    }

    @Test
    void tenantRuleSourceNeedsSettingsManagement() throws Exception {
        mvc.perform(get("/api/settings/rule-source")).andExpect(status().isUnauthorized());
        var ordinary = session("PERM_DOCUMENT_REVIEW");
        mvc.perform(get("/api/settings/rule-source").session(ordinary)).andExpect(status().isForbidden());
        var token = token(ordinary);
        mvc.perform(put("/api/settings/rule-source").session(ordinary).header(token.getHeaderName(), token.getToken())
            .contentType("application/json").content("{\"mode\":\"IMPORTED\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void authorizedAdministratorChangesTheTenantModeOnlyWithCsrf() throws Exception {
        when(service.tenantMode()).thenReturn(RuleSourceMode.BOTH);
        when(service.setTenantMode(any(), any())).thenReturn(RuleSourceMode.IMPORTED);
        var session = session("PERM_SETTINGS_MANAGE");
        mvc.perform(put("/api/settings/rule-source").session(session).contentType("application/json").content("{\"mode\":\"IMPORTED\"}"))
            .andExpect(status().isForbidden());
        verify(service, never()).setTenantMode(any(), any());
        var token = token(session);
        mvc.perform(put("/api/settings/rule-source").session(session).header(token.getHeaderName(), token.getToken())
            .contentType("application/json").content("{\"mode\":\"IMPORTED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("IMPORTED"));
        verify(service).setTenantMode(eq("IMPORTED"), any());
    }

    @Test
    void lcSelectionNeedsLcEditToChangeButAnyUserMayRead() throws Exception {
        var lc = UUID.randomUUID();
        when(service.view(lc)).thenReturn(new RuleSourceService.View(RuleSourceMode.BOTH, null, RuleSourceMode.BOTH, List.of(), List.of()));
        var reader = session("PERM_DOCUMENT_REVIEW");
        mvc.perform(get("/api/lcs/" + lc + "/rule-source").session(reader)).andExpect(status().isOk());
        var token = token(reader);
        mvc.perform(put("/api/lcs/" + lc + "/rule-source").session(reader).header(token.getHeaderName(), token.getToken())
            .contentType("application/json").content("{\"override\":\"EMBEDDED\",\"packIds\":[]}")).andExpect(status().isForbidden());
        verify(service, never()).updateLc(any(), any(), any(), any());

        var editor = session("PERM_LC_EDIT");
        var editorToken = token(editor);
        mvc.perform(put("/api/lcs/" + lc + "/rule-source").session(editor).header(editorToken.getHeaderName(), editorToken.getToken())
            .contentType("application/json").content("{\"override\":\"EMBEDDED\",\"packIds\":[]}")).andExpect(status().isOk());
        verify(service).updateLc(eq(lc), eq("EMBEDDED"), eq(List.of()), any());
    }
}
