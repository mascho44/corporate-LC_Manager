package de.corporate.lc.rulepack;
import de.corporate.lc.config.SecurityConfig;
import de.corporate.lc.config.CredentialSessionFilter;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({InternalPackController.class,RuleFactsController.class})
@Import(SecurityConfig.class)
class PackSecurityTest {
 @Autowired MockMvc mvc;
 @MockitoBean de.corporate.lc.tenant.service.TenantMembershipService memberships;
 @MockitoBean InternalPackService service;
 @MockitoBean AppUserDetailsService details;
 @MockitoBean AppUserRepository users;
 @MockitoBean de.corporate.lc.lc.repository.LetterOfCreditRepository lcs;
 @MockitoBean de.corporate.lc.document.repository.LcDocumentRepository documents;
 @MockitoBean de.corporate.lc.audit.service.AuditService audit;
 @MockitoBean de.corporate.lc.check.service.DocumentCheckService checks;
 MockHttpSession session(boolean allowed){
  var user=new AppUser();user.setUsername("synthetic-user");user.setRole(UserRole.EDITOR);user.setActive(true);user.setPasswordHash("synthetic-hash");
  when(users.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));
  var authority=new SimpleGrantedAuthority(allowed?"PERM_SETTINGS_MANAGE":"PERM_DOCUMENT_REVIEW");
  var authentication=new UsernamePasswordAuthenticationToken(user.getUsername(),null,List.of(authority));
  when(memberships.requireActiveAccess(org.mockito.ArgumentMatchers.nullable(UUID.class))).thenReturn(new de.corporate.lc.tenant.service.TenantMembershipService.Access(user.getTenantId(),user.getId(),UUID.randomUUID(),UUID.randomUUID(),user.getRole(),Set.copyOf(user.effectivePermissions())));
  var session=new MockHttpSession();session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(authentication));
  session.setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of(user.getPasswordHash()));
  session.setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,AuthorizationStamp.of(user));session.setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());return session;
 }
 @Test void anonymousAndOrdinaryUserCannotManagePacks()throws Exception{
  mvc.perform(get("/api/settings/rule-packs")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/settings/rule-packs").session(session(false))).andExpect(status().isForbidden());
  mvc.perform(get("/rule-packs.html").session(session(false))).andExpect(status().isForbidden());
  verifyNoInteractions(service);
 }
 @Test void authorizedUserStillNeedsCsrfForMutation()throws Exception{
  var session=session(true);mvc.perform(post("/api/settings/rule-packs").session(session).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  verifyNoInteractions(service);
 }
 @Test void authorizedImportAndBoundedBodyAreEnforced()throws Exception{
  var session=session(true);
  var token=(CsrfToken)mvc.perform(get("/api/settings/rule-packs").session(session)).andReturn().getRequest().getAttribute(CsrfToken.class.getName());
  String value=token.getToken();
  mvc.perform(post("/api/settings/rule-packs").session(session).header(token.getHeaderName(),value).contentType("application/json").content(PackCodecTest.example())).andExpect(status().isOk());
  verify(service).importPack(any(byte[].class),any());
  mvc.perform(post("/api/settings/rule-packs/preview").session(session).header(token.getHeaderName(),value).contentType("application/json").content(new byte[PackCodec.MAX_BYTES+1])).andExpect(status().isBadRequest());
  verify(service,never()).preview(any());
 }
 @Test void supplementaryLcFactsNeedLcEditAndCsrf()throws Exception{
  var id=UUID.randomUUID();var lc=new de.corporate.lc.lc.domain.LetterOfCredit();when(lcs.findById(id)).thenReturn(Optional.of(lc));
  var session=session(false);
  var token=(CsrfToken)mvc.perform(get("/api/lcs/"+id+"/rule-facts").session(session)).andReturn().getRequest().getAttribute(CsrfToken.class.getName());
  String value=token.getToken();
  mvc.perform(put("/api/lcs/"+id+"/rule-facts").session(session).header(token.getHeaderName(),value).contentType("application/json").content("{\"LC_TRANSFERRED\":\"false\"}")).andExpect(status().isForbidden());
  var auth=new UsernamePasswordAuthenticationToken("synthetic-user",null,List.of(new SimpleGrantedAuthority("PERM_LC_EDIT")));
  session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(auth));
  mvc.perform(put("/api/lcs/"+id+"/rule-facts").session(session).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/lcs/"+id+"/rule-facts").session(session).header(token.getHeaderName(),value).contentType("application/json").content("{\"LC_TRANSFERRED\":\"false\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.LC_TRANSFERRED").value("false"));
  verify(checks).invalidateDecisions(id);verify(audit).recordInTransaction(any(),eq("LC_RULE_FACTS_UPDATED"),anyString(),eq(id),contains("SHA-256"));
  mvc.perform(put("/api/lcs/"+id+"/rule-facts").session(session).header(token.getHeaderName(),value).contentType("application/json").content("{\"DOCUMENT_ISSUER\":\"Demo\"}")).andExpect(status().isBadRequest());
 }
 @Test void typedRequirementsRequireEditRightsAndCsrfAndRejectInvalidTypes()throws Exception{
  var id=UUID.randomUUID();var lc=new de.corporate.lc.lc.domain.LetterOfCredit();when(lcs.findById(id)).thenReturn(Optional.of(lc));when(lcs.existsById(id)).thenReturn(true);
  var session=session(false);var token=(CsrfToken)mvc.perform(get("/api/lcs/"+id+"/rule-facts/definitions").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.requirements[0].field").exists()).andReturn().getRequest().getAttribute(CsrfToken.class.getName());
  var route="/api/lcs/"+id+"/rule-requirements/BILL_OF_LADING";
  mvc.perform(put(route).session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(new UsernamePasswordAuthenticationToken("synthetic-user",null,List.of(new SimpleGrantedAuthority("PERM_LC_EDIT")))));
  mvc.perform(put(route).session(session).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(put(route).session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{\"LC_REQUIRED_ORIGINAL_COUNT\":\"3\"}")).andExpect(status().isOk());
  verify(audit).recordInTransaction(any(),eq("LC_RULE_REQUIREMENTS_UPDATED"),anyString(),eq(id),contains("BILL_OF_LADING"));
  mvc.perform(put(route).session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content(new byte[RuleFacts.MAX_BYTES+1])).andExpect(status().isBadRequest());
  mvc.perform(put("/api/lcs/"+id+"/rule-requirements/INVALID").session(session).header(token.getHeaderName(),token.getToken()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
 }
 @Test void documentFactsCannotBeEditedWithOnlyLcEdit()throws Exception{
  var session=session(false);
  var token=(CsrfToken)mvc.perform(get("/api/settings/rule-packs").session(session)).andReturn().getRequest().getAttribute(CsrfToken.class.getName());String value=token.getToken();
  session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(new UsernamePasswordAuthenticationToken("synthetic-user",null,List.of(new SimpleGrantedAuthority("PERM_LC_EDIT")))));
  mvc.perform(put("/api/lcs/"+UUID.randomUUID()+"/documents/"+UUID.randomUUID()+"/rule-facts").session(session).header(token.getHeaderName(),value).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  verifyNoInteractions(documents,checks);
 }
}
