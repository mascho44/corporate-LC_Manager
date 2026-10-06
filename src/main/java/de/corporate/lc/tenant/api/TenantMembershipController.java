package de.corporate.lc.tenant.api;
import de.corporate.lc.tenant.domain.TenantContext;
import de.corporate.lc.tenant.repository.TenantRepository;
import de.corporate.lc.tenant.service.TenantMembershipService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Read-only administration under the existing USER_MANAGE authorization boundary. */
@RestController @RequestMapping("/api/users/memberships")
public class TenantMembershipController {
 public record TenantSettings(String code,String defaultLanguage,boolean bankEnabled,boolean corporateEnabled){}
 public record Overview(UUID tenantId,String tenantName,boolean switchingEnabled,TenantSettings settings,List<TenantMembershipService.Membership> memberships){}
 private final TenantMembershipService memberships;
 private final TenantRepository tenants;
 public TenantMembershipController(TenantMembershipService memberships,TenantRepository tenants){this.memberships=memberships;this.tenants=tenants;}
 @GetMapping public Overview overview(){
  var id=TenantContext.currentId();var tenant=tenants.findById(id).orElseThrow(()->new NoSuchElementException("Tenant not found."));
  return new Overview(id,tenant.getName(),false,new TenantSettings(tenant.getCode(),tenant.getDefaultLanguage(),tenant.isBankEnabled(),tenant.isCorporateEnabled()),memberships.list());
 }
}
