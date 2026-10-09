package de.ostms.lc.user.api;
import de.ostms.lc.user.service.PlatformAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/platform") public class PlatformAdministrationController {
 public record Access(@NotNull Boolean active){}
 public record Grant(@NotNull Boolean granted){}
 private final PlatformAdministrationService service;
 @org.springframework.beans.factory.annotation.Autowired private de.ostms.lc.audit.service.AuditService audit;
 public PlatformAdministrationController(PlatformAdministrationService service){this.service=service;}
 @GetMapping("/access") public Map<String,Boolean> access(Authentication auth){return Map.of("enabled",service.enabled(auth));}
 @GetMapping("/session") public Map<String,String> session(Authentication auth,org.springframework.security.web.csrf.CsrfToken csrf){
  service.verifyLiveAccess(auth);return Map.of("username",auth.getName(),"csrfToken",csrf.getToken());
 }
 @PostMapping("/logout") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void logout(Authentication auth,jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response){
  service.verifyLiveAccess(auth);audit.record(auth,"LOGOUT","SESSION",null,"Platform sign-out");
  new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request,response,auth);
 }
 @GetMapping("/users") public List<PlatformAdministrationService.Account> users(Authentication auth){return service.accounts(auth);}
 @PutMapping("/users/{id}/access") public PlatformAdministrationService.Account access(@PathVariable UUID id,@Valid @RequestBody Access request,Authentication auth){return service.changeAccess(id,request.active(),auth);}
 @PutMapping("/users/{id}/platform-grant") public PlatformAdministrationService.Account grant(@PathVariable UUID id,@Valid @RequestBody Grant request,Authentication auth){return service.changePlatformGrant(id,request.granted(),auth);}
}
