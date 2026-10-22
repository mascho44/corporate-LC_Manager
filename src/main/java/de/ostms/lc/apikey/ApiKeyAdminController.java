package de.ostms.lc.apikey;

import de.ostms.lc.audit.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Management of API keys. Needs the settings right (see SecurityConfig). */
@RestController @RequestMapping("/api/api-keys")
public class ApiKeyAdminController {
 public record CreateRequest(String name,List<String> scopes){}
 private final ApiKeyService keys;private final AuditService audit;
 public ApiKeyAdminController(ApiKeyService keys,AuditService audit){this.keys=keys;this.audit=audit;}
 @GetMapping public List<ApiKeyService.View> list(){return keys.list();}
 @GetMapping("/scopes") public Map<String,Object> scopes(){return Map.of("scopes",ApiKeyService.SCOPES);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public ApiKeyService.Created create(@RequestBody CreateRequest request,Authentication authentication){
  var created=keys.create(request==null?null:request.name(),request==null?null:request.scopes(),authentication.getName());
  audit.record(authentication,"API_KEY_CREATED","API_KEY",created.key().id(),created.key().name()+" · "+created.key().prefix()+"… · "+String.join(",",created.key().scopes()));
  return created;
 }
 @DeleteMapping("/{id}")
 public ApiKeyService.View revoke(@PathVariable UUID id,Authentication authentication){
  var view=keys.revoke(id);
  audit.record(authentication,"API_KEY_REVOKED","API_KEY",id,view.name()+" · "+view.prefix()+"…");
  return view;
 }
}
