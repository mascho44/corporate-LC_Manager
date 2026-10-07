package de.corporate.lc.tenant.api;
import de.corporate.lc.tenant.domain.TenantContext;
import de.corporate.lc.tenant.repository.TenantRepository;
import de.corporate.lc.tenant.service.TenantMembershipService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Tenant-scoped overview and role-only administration under USER_MANAGE. */
@RestController @RequestMapping("/api/users/memberships")
public class TenantMembershipController {
 public record TenantSettings(String code,String defaultLanguage,boolean bankEnabled,boolean corporateEnabled){}
 public record Overview(UUID tenantId,String tenantName,boolean switchingEnabled,TenantSettings settings,List<TenantMembershipService.Membership> memberships,boolean roleEditingEnabled,boolean accessEditingEnabled,List<RoleChoice> roleChoices){}
 public record RoleChoice(UUID id,String name,Set<de.corporate.lc.user.domain.UserPermission> permissions){}
 public record AccessState(@jakarta.validation.constraints.NotNull Boolean suspended){}
 public record RoleAssignment(@jakarta.validation.constraints.NotNull UUID roleId){}
 private final TenantMembershipService memberships;
 private final TenantRepository tenants;
 private final de.corporate.lc.user.service.RoleService roles;
 private final de.corporate.lc.tenant.service.TenantMembershipAdministrationService administration;
 private final de.corporate.lc.audit.service.AuditService audit;
 public TenantMembershipController(TenantMembershipService memberships,TenantRepository tenants,de.corporate.lc.user.service.RoleService roles,de.corporate.lc.tenant.service.TenantMembershipAdministrationService administration,de.corporate.lc.audit.service.AuditService audit){this.memberships=memberships;this.tenants=tenants;this.roles=roles;this.administration=administration;this.audit=audit;}
 @GetMapping public Overview overview(){
  var id=TenantContext.currentId();var tenant=tenants.findById(id).orElseThrow(()->new NoSuchElementException("Tenant not found."));
  return new Overview(id,tenant.getName(),true,new TenantSettings(tenant.getCode(),tenant.getDefaultLanguage(),tenant.isBankEnabled(),tenant.isCorporateEnabled()),memberships.list(),true,true,roles.all().stream().map(r->new RoleChoice(r.id(),r.name(),r.permissions())).toList());
 }
 @org.springframework.transaction.annotation.Transactional @PutMapping("/{userId}/role")
 public TenantMembershipService.Membership assignRole(@PathVariable UUID userId,@jakarta.validation.Valid @RequestBody RoleAssignment request,org.springframework.security.core.Authentication authentication){
  var before=administration.getForAdministration(userId);
  var after=administration.updateRole(userId,request.roleId(),authentication.getName());
  audit.recordChangeInTransaction(authentication,"USER_MEMBERSHIP_ROLE_UPDATED","MEMBERSHIP",userId,"Tenant membership role updated",de.corporate.lc.user.api.AdministrationAuditSnapshot.membership(before),de.corporate.lc.user.api.AdministrationAuditSnapshot.membership(after));
  return after;
 }
 @org.springframework.transaction.annotation.Transactional @PutMapping("/{userId}/access")
 public TenantMembershipService.Membership changeAccess(@PathVariable UUID userId,@jakarta.validation.Valid @RequestBody AccessState request,org.springframework.security.core.Authentication authentication){
  var before=administration.getForAdministration(userId);
  var after=administration.updateSuspension(userId,request.suspended(),authentication.getName());
  audit.recordChangeInTransaction(authentication,"USER_MEMBERSHIP_ACCESS_UPDATED","MEMBERSHIP",userId,"Tenant membership access updated",de.corporate.lc.user.api.AdministrationAuditSnapshot.membership(before),de.corporate.lc.user.api.AdministrationAuditSnapshot.membership(after));
  return after;
 }
}
