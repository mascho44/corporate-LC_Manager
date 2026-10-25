package de.ostms.lc.tenant.api;
import de.ostms.lc.tenant.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

@RestController @RequestMapping("/api/users/memberships/shared")
public class TenantSharedMembershipController {
 public record Assignment(@NotBlank String username,@NotNull UUID roleId){}
 public record Role(@NotNull UUID roleId){}
 public record Access(@NotNull Boolean suspended){}
 private final TenantMembershipProvisioningService provisioning;
 private final TenantSharedIdentityRoleService roles;
 private final TenantSharedIdentityAccessService access;
 private final TenantSharedIdentityRemovalService removal;
 public TenantSharedMembershipController(TenantMembershipProvisioningService provisioning,TenantSharedIdentityRoleService roles,TenantSharedIdentityAccessService access,TenantSharedIdentityRemovalService removal){this.provisioning=provisioning;this.roles=roles;this.access=access;this.removal=removal;}
 @DeleteMapping("/{userId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void remove(@PathVariable UUID userId,Authentication auth){removal.remove(userId,auth);}
 @PostMapping public TenantMembershipService.Membership assign(@Valid @RequestBody Assignment request,Authentication auth){return provisioning.assignExistingIdentity(request.username(),request.roleId(),auth);}
 @PutMapping("/{userId}/role") public TenantMembershipService.Membership role(@PathVariable UUID userId,@Valid @RequestBody Role request,Authentication auth){return roles.changeRole(userId,request.roleId(),auth);}
 @PutMapping("/{userId}/access") public TenantMembershipService.Membership access(@PathVariable UUID userId,@Valid @RequestBody Access request,Authentication auth){return access.changeAccess(userId,request.suspended(),auth);}
}
