package de.ostms.lc.apikey;

import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiKeyTest {
 private final ApiKeyRepository keys=mock(ApiKeyRepository.class);private final TenantRepository tenants=mock(TenantRepository.class);
 private final ApiKeyService service=new ApiKeyService(keys,tenants);
 private final UUID tenantId=UUID.randomUUID();

 @Test void createsAKeyWithScopesAndShowsTheSecretOnceStoringOnlyItsHash() {
  when(keys.save(any())).thenAnswer(i->i.getArgument(0));
  try(var scope=TenantContext.open(tenantId)){
   var created=service.create(" ERP ",List.of("read","document_upload","READ"),"admin");
   assertThat(created.secret()).startsWith("lca_").hasSizeGreaterThan(40);
   assertThat(created.key().scopes()).containsExactly("READ","DOCUMENT_UPLOAD");
   assertThat(created.key().prefix()).isEqualTo(created.secret().substring(0,8));
   var saved=org.mockito.ArgumentCaptor.forClass(ApiKey.class);verify(keys).save(saved.capture());
   assertThat(saved.getValue().getName()).isEqualTo("ERP");
   assertThat(created.key().toString()).doesNotContain(created.secret());
   assertThat(saved.getValue().getKeyPrefix()).hasSize(8);
  }
 }
 @Test void rejectsBadInput() {
  try(var scope=TenantContext.open(tenantId)){
   assertThatThrownBy(()->service.create(" ",List.of("READ"),"a")).hasMessageContaining("Namen");
   assertThatThrownBy(()->service.create("x",List.of(),"a")).hasMessageContaining("mindestens einen");
   assertThatThrownBy(()->service.create("x",List.of("ADMIN"),"a")).hasMessageContaining("Unbekannter");
   when(keys.activeCount(tenantId)).thenReturn(20L);
   assertThatThrownBy(()->service.create("x",List.of("READ"),"a")).hasMessageContaining("20");
  }
 }

 private ApiKey storedKey(String secret,List<String> scopes){return new ApiKey(tenantId,"ERP",ApiKeyService.hash(secret),secret.substring(0,8),scopes,"admin");}
 private Tenant activeTenant(){var t=mock(Tenant.class);when(t.isActive()).thenReturn(true);return t;}

 @Test void authenticatesOnlyKnownUnrevokedKeysOfActiveTenants() {
  var key=storedKey("lca_secret1",List.of("READ"));when(keys.findByKeyHash(ApiKeyService.hash("lca_secret1"))).thenReturn(Optional.of(key));
  var active=activeTenant();when(tenants.findById(tenantId)).thenReturn(Optional.of(active));
  var identity=service.authenticate("lca_secret1").orElseThrow();
  assertThat(identity.tenantId()).isEqualTo(tenantId);assertThat(identity.scopes()).containsExactly("READ");assertThat(key.getLastUsedAt()).isNotNull();
  assertThat(service.authenticate("lca_unknown")).isEmpty();
  assertThat(service.authenticate("lcc_wrongprefix")).isEmpty();
  assertThat(service.authenticate(null)).isEmpty();
  key.revoke();assertThat(service.authenticate("lca_secret1")).isEmpty();
  var other=storedKey("lca_secret2",List.of("READ"));when(keys.findByKeyHash(ApiKeyService.hash("lca_secret2"))).thenReturn(Optional.of(other));
  var inactive=mock(Tenant.class);when(inactive.isActive()).thenReturn(false);when(tenants.findById(tenantId)).thenReturn(Optional.of(inactive));
  assertThat(service.authenticate("lca_secret2")).isEmpty();
 }

 private MockHttpServletRequest request(String method,String path,String bearer){var r=new MockHttpServletRequest(method,path);r.setRequestURI(path);if(bearer!=null)r.addHeader("Authorization","Bearer "+bearer);return r;}

 @Test void theFilterGivesAKeyOnlyItsScopesAndOnlyUnderApiV1() throws Exception {
  var key=storedKey("lca_secret1",List.of("READ","LC_CREATE"));when(keys.findByKeyHash(ApiKeyService.hash("lca_secret1"))).thenReturn(Optional.of(key));var active=activeTenant();when(tenants.findById(tenantId)).thenReturn(Optional.of(active));
  var filter=new ApiKeyFilter(()->service);
  var seen=new AtomicReference<String>();FilterChain chain=(req,res)->{var a=SecurityContextHolder.getContext().getAuthentication();seen.set(a.getName()+"|"+a.getAuthorities()+"|"+TenantContext.currentId());};
  var ok=new MockHttpServletResponse();filter.doFilter(request("GET","/api/v1/lcs","lca_secret1"),ok,chain);
  assertThat(ok.getStatus()).isEqualTo(200);assertThat(seen.get()).contains("api:ERP").contains("PERM_API_READ").contains("PERM_API_LC_CREATE").doesNotContain("DOCUMENT_UPLOAD").endsWith(tenantId.toString());
  assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  var elsewhere=new MockHttpServletResponse();seen.set(null);filter.doFilter(request("GET","/api/lcs","lca_secret1"),elsewhere,chain);
  assertThat(elsewhere.getStatus()).isEqualTo(403);assertThat(seen.get()).isNull();
  var invalid=new MockHttpServletResponse();filter.doFilter(request("GET","/api/v1/lcs","lca_nope"),invalid,chain);
  assertThat(invalid.getStatus()).isEqualTo(401);assertThat(seen.get()).isNull();
  // a key without the scope of the call is refused with 403 and the scope named
  var denied=new MockHttpServletResponse();seen.set(null);filter.doFilter(request("POST","/api/v1/lcs/"+UUID.randomUUID()+"/documents","lca_secret1"),denied,chain);
  assertThat(denied.getStatus()).isEqualTo(403);assertThat(denied.getContentAsString()).contains("DOCUMENT_UPLOAD");assertThat(seen.get()).isNull();
  var unknown=new MockHttpServletResponse();filter.doFilter(request("DELETE","/api/v1/lcs/x","lca_secret1"),unknown,chain);assertThat(unknown.getStatus()).isEqualTo(403);
  assertThat(ApiKeyFilter.requiredScope("GET","/api/v1/lcs/1/findings")).isEqualTo("READ");assertThat(ApiKeyFilter.requiredScope("POST","/api/v1/lcs")).isEqualTo("LC_CREATE");assertThat(ApiKeyFilter.requiredScope("PUT","/api/v1/lcs")).isNull();
  // without an API key the request passes through untouched
  var plain=new MockHttpServletResponse();var passed=new AtomicReference<Boolean>(false);filter.doFilter(request("GET","/api/lcs",null),plain,(a,b)->passed.set(true));
  assertThat(passed.get()).isTrue();assertThat(ApiKeyFilter.isApiKeyRequest(request("POST","/api/v1/lcs","lca_x"))).isTrue();assertThat(ApiKeyFilter.isApiKeyRequest(request("POST","/api/lcs",null))).isFalse();
 }
}
