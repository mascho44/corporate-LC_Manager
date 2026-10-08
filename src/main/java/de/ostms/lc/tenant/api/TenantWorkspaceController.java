package de.ostms.lc.tenant.api;
import de.ostms.lc.config.CredentialSessionFilter;
import de.ostms.lc.tenant.service.TenantWorkspaceService;
import de.ostms.lc.user.service.AuthorizationStamp;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Stream;

@RestController @RequestMapping("/api/tenants")
public class TenantWorkspaceController {
 public record Create(String code,String name,String defaultLanguage,boolean bankEnabled,boolean corporateEnabled){}
 private final TenantWorkspaceService service;
 public TenantWorkspaceController(TenantWorkspaceService service){this.service=service;}
 @GetMapping public TenantWorkspaceService.Overview list(Authentication auth){return service.overview(auth);}
 @PostMapping public TenantWorkspaceService.Workspace create(@RequestBody Create request,Authentication auth){return service.create(request.code(),request.name(),request.defaultLanguage(),request.bankEnabled(),request.corporateEnabled(),auth);}
 @PostMapping("/{tenantId}/select") public Map<String,Object> select(@PathVariable UUID tenantId,Authentication auth,HttpServletRequest request,HttpServletResponse response){
  var session=request.getSession(false);if(session==null)throw new org.springframework.security.access.AccessDeniedException("Verified session is required.");
  var selected=service.select(tenantId,auth);
  var authorities=Stream.concat(Stream.of(new SimpleGrantedAuthority("ROLE_"+selected.baseRole().name())),selected.permissions().stream().map(p->new SimpleGrantedAuthority("PERM_"+p.name()))).toList();
  var replacement=UsernamePasswordAuthenticationToken.authenticated(auth.getName(),null,authorities);
  request.changeSessionId();session.setAttribute(CredentialSessionFilter.TENANT,tenantId);session.setAttribute(CredentialSessionFilter.AUTHORIZATION_STAMP,AuthorizationStamp.of(selected));
  var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(replacement);SecurityContextHolder.setContext(context);new HttpSessionSecurityContextRepository().saveContext(context,request,response);
  return Map.of("tenantId",tenantId,"reloadRequired",true);
 }
}
