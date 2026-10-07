package de.corporate.lc.tenant.api;
import de.corporate.lc.tenant.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.*;
@RestController @RequestMapping("/api/platform/tenants") public class PlatformTenantController {
 public record Create(String code,String name,String defaultLanguage,@NotNull Boolean bankEnabled,@NotNull Boolean corporateEnabled){}
 public record Update(@NotNull Boolean active,@NotNull Boolean bankEnabled,@NotNull Boolean corporateEnabled){}
 private final PlatformTenantService service;
 public PlatformTenantController(PlatformTenantService service){this.service=service;}
 @GetMapping public List<PlatformTenantService.View> list(Authentication auth){return service.list(auth);}
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public TenantWorkspaceService.Workspace create(@Valid @RequestBody Create body,Authentication auth){return service.create(body.code(),body.name(),body.defaultLanguage(),body.bankEnabled(),body.corporateEnabled(),auth);}
 @PutMapping("/{id}") public PlatformTenantService.View update(@PathVariable UUID id,@Valid @RequestBody Update body,Authentication auth){return service.update(id,body.active(),body.bankEnabled(),body.corporateEnabled(),auth);}
}
