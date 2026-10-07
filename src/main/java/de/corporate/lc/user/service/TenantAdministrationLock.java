package de.corporate.lc.user.service;
import de.corporate.lc.tenant.domain.TenantContext;
import de.corporate.lc.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.access.AccessDeniedException;

/** Serialize administration decisions per tenant until their enclosing transaction completes. */
@Service public class TenantAdministrationLock {
 private final TenantRepository tenants;
 public TenantAdministrationLock(TenantRepository tenants){this.tenants=tenants;}
 @Transactional(propagation=Propagation.MANDATORY) public void acquire(){
  tenants.findForAdministration(TenantContext.currentId()).orElseThrow(()->new AccessDeniedException("Tenant administration context is unavailable."));
 }
}
