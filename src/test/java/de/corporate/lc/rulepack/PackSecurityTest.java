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

@WebMvcTest(InternalPackController.class)
@Import(SecurityConfig.class)
class PackSecurityTest {
 @Autowired MockMvc mvc;
 @MockitoBean InternalPackService service;
 @MockitoBean AppUserDetailsService details;
 @MockitoBean AppUserRepository users;
 MockHttpSession session(boolean allowed){
  var user=new AppUser();user.setUsername("synthetic-user");user.setRole(UserRole.EDITOR);user.setActive(true);user.setPasswordHash("synthetic-hash");
  when(users.findByUsernameIgnoreCase(user.getUsername())).thenReturn(Optional.of(user));
  var authority=new SimpleGrantedAuthority(allowed?"PERM_SETTINGS_MANAGE":"PERM_DOCUMENT_REVIEW");
  var authentication=new UsernamePasswordAuthenticationToken(user.getUsername(),null,List.of(authority));
  var session=new MockHttpSession();session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(authentication));
  session.setAttribute(CredentialSessionFilter.STAMP,CredentialStamp.of(user.getPasswordHash()));
  session.setAttribute(CredentialSessionFilter.AUTHENTICATED_AT,System.currentTimeMillis());return session;
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
}
