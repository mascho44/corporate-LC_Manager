package de.ostms.lc.user.api;
import de.ostms.lc.user.service.PlatformAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/platform") public class PlatformAdministrationController {
 public record Access(@NotNull Boolean active){}
 @org.springframework.beans.factory.annotation.Autowired(required=false) private de.ostms.lc.config.AccessDenialLog denials;
 /** The latest refused requests (method, path, user, reason), for finding the cause of a "forbidden" without server access. */
 @GetMapping("/denials") public java.util.List<de.ostms.lc.config.AccessDenialLog.Entry> denials(Authentication auth){service.verifyLiveAccess(auth);return denials==null?java.util.List.of():denials.recent();}
 public record Grant(@NotNull Boolean granted){}
 public record PasswordChange(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=200) String currentPassword,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=200) String newPassword){}
 @org.springframework.beans.factory.annotation.Autowired(required=false) private de.ostms.lc.user.service.IdentityCredentialService credentials;
 /** Own-password change for platform accounts, which belong to no tenant and therefore cannot use the workspace endpoint. The credential stamp changes, so the session ends. */
 @PostMapping("/password") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void changePassword(@Valid @RequestBody PasswordChange body,Authentication auth){
  service.verifyLiveAccess(auth);
  if(credentials==null)throw new IllegalStateException("Credential service unavailable.");
  try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID)){
   credentials.changeOwnPassword(auth.getName(),body.currentPassword(),body.newPassword());
   audit.record(auth,"PASSWORD_CHANGED","USER",auth.getName(),"Passwort geändert (Plattformverwaltung)");
  }
 }
 private final PlatformAdministrationService service;
 @org.springframework.beans.factory.annotation.Autowired private de.ostms.lc.audit.service.AuditService audit;
 public PlatformAdministrationController(PlatformAdministrationService service){this.service=service;}
 /** enabled: the identity may use platform administration; tenantless: its session carries no tenant authority (only platform resources work, the workspace chooser must be skipped). */
 @GetMapping("/access") public Map<String,Boolean> access(Authentication auth){
  boolean tenantless=auth!=null&&auth.getAuthorities().stream().anyMatch(a->de.ostms.lc.user.service.AppUserDetailsService.PLATFORM_ONLY.equals(a.getAuthority()));
  return Map.of("enabled",service.enabled(auth),"tenantless",tenantless);
 }
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
