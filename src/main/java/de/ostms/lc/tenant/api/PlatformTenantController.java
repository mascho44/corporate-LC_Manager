package de.ostms.lc.tenant.api;
import de.ostms.lc.tenant.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.*;
@RestController @RequestMapping("/api/platform/tenants") public class PlatformTenantController {
 /** Name and email are only needed for a new account that is invited; an existing username needs neither. */
 public record Administrator(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=100) String username,@jakarta.validation.constraints.Size(max=255) String displayName,@jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max=255) String email){}
 public record Create(String code,String name,String defaultLanguage,@NotNull Boolean bankEnabled,@NotNull Boolean corporateEnabled,@Valid Administrator administrator){}
 public record Update(@NotNull Boolean active,@NotNull Boolean bankEnabled,@NotNull Boolean corporateEnabled){}
 public record Archive(@NotNull Boolean archived){}
 @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void purge(@PathVariable UUID id,@RequestBody PlatformTenantService.PurgeConfirmation confirmation,Authentication auth){service.purge(id,confirmation,auth);}
 @PutMapping("/{id}/archive") public PlatformTenantService.View archive(@PathVariable UUID id,@Valid @RequestBody Archive body,Authentication auth){return service.archive(id,body.archived(),auth);}
 private final PlatformTenantService service;private final TenantInventoryService inventory;
 public PlatformTenantController(PlatformTenantService service,TenantInventoryService inventory){this.service=service;this.inventory=inventory;}
 @GetMapping("/{id}/inventory") public TenantInventoryService.Preview inventory(@PathVariable UUID id,Authentication auth){return inventory.preview(id,auth);}
 @GetMapping public List<PlatformTenantService.View> list(Authentication auth){return service.list(auth);}
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public TenantWorkspaceService.Workspace create(@Valid @RequestBody Create body,Authentication auth){return service.create(body.code(),body.name(),body.defaultLanguage(),body.bankEnabled(),body.corporateEnabled(),body.administrator()==null?null:new TenantWorkspaceService.AdminInvite(body.administrator().username(),body.administrator().displayName(),body.administrator().email()),auth);}
 @PutMapping("/{id}") public PlatformTenantService.View update(@PathVariable UUID id,@Valid @RequestBody Update body,Authentication auth){return service.update(id,body.active(),body.bankEnabled(),body.corporateEnabled(),auth);}
}
