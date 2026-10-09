package de.ostms.lc.user.api;

import de.ostms.lc.user.service.PlatformOverviewService;
import de.ostms.lc.user.service.PlatformOverviewStore;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/platform")
public class PlatformOverviewController {
 private final PlatformOverviewService service;
 private final de.ostms.lc.tenant.service.PlatformMembershipService membershipAdmin;
 public PlatformOverviewController(PlatformOverviewService service,de.ostms.lc.tenant.service.PlatformMembershipService membershipAdmin){this.service=service;this.membershipAdmin=membershipAdmin;}
 public record RoleChange(@jakarta.validation.constraints.NotNull java.util.UUID roleId){}
 public record AccessChange(@jakarta.validation.constraints.NotNull Boolean suspended){}
 @PutMapping("/memberships/{tenantId}/{userId}/role") public de.ostms.lc.tenant.service.TenantMembershipService.Membership role(@PathVariable java.util.UUID tenantId,@PathVariable java.util.UUID userId,@jakarta.validation.Valid @RequestBody RoleChange request,Authentication auth){return membershipAdmin.changeRole(tenantId,userId,request.roleId(),auth);}
 @PutMapping("/memberships/{tenantId}/{userId}/access") public de.ostms.lc.tenant.service.TenantMembershipService.Membership access(@PathVariable java.util.UUID tenantId,@PathVariable java.util.UUID userId,@jakarta.validation.Valid @RequestBody AccessChange request,Authentication auth){return membershipAdmin.changeAccess(tenantId,userId,request.suspended(),auth);}
 @GetMapping("/memberships") public List<PlatformOverviewStore.Membership> memberships(Authentication auth){return service.memberships(auth);}
 @GetMapping("/audit") public List<PlatformOverviewStore.AuditRow> audit(@RequestParam(required=false) String tenant,@RequestParam(required=false) Integer limit,Authentication auth){return service.audit(tenant,limit,auth);}
}
