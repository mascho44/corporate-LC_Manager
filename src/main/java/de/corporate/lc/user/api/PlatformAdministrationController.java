package de.corporate.lc.user.api;
import de.corporate.lc.user.service.PlatformAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/platform") public class PlatformAdministrationController {
 public record Access(@NotNull Boolean active){}
 private final PlatformAdministrationService service;
 public PlatformAdministrationController(PlatformAdministrationService service){this.service=service;}
 @GetMapping("/access") public Map<String,Boolean> access(Authentication auth){return Map.of("enabled",service.enabled(auth));}
 @GetMapping("/users") public List<PlatformAdministrationService.Account> users(Authentication auth){return service.accounts(auth);}
 @PutMapping("/users/{id}/access") public PlatformAdministrationService.Account access(@PathVariable UUID id,@Valid @RequestBody Access request,Authentication auth){return service.changeAccess(id,request.active(),auth);}
}
