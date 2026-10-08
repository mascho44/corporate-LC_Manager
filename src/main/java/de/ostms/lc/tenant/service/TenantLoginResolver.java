package de.ostms.lc.tenant.service;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.DisabledException;
import java.util.UUID;
/** Private credential-stage lookup; never exposes a public workspace directory. */
@Service public class TenantLoginResolver {
 private final TenantRepository tenants;
 public TenantLoginResolver(TenantRepository tenants){this.tenants=tenants;}
 @Transactional(readOnly=true) public UUID resolve(String code){
  if(code==null||code.isBlank())return Tenant.DEFAULT_ID;
  code=code.trim();if(code.length()>100)throw new DisabledException("Login unavailable.");
  return tenants.findByCodeIgnoreCase(code).map(Tenant::getId).orElseThrow(()->new DisabledException("Login unavailable."));
 }
}
