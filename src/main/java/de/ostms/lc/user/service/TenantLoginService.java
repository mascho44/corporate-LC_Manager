package de.ostms.lc.user.service;
import de.ostms.lc.tenant.service.TenantLoginResolver;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;

@Service public class TenantLoginService {
 public record Verified(UUID tenantId,UserDetails user){}
 private final TenantLoginResolver resolver;private final AppUserDetailsService details;private final PasswordEncoder encoder;private final String dummyHash;
 @org.springframework.beans.factory.annotation.Autowired private de.ostms.lc.user.repository.AppUserRepository identities;
 @org.springframework.beans.factory.annotation.Autowired private de.ostms.lc.tenant.repository.TenantMembershipRepository memberships;
 @org.springframework.beans.factory.annotation.Autowired private de.ostms.lc.tenant.repository.TenantRepository tenants;
 public TenantLoginService(TenantLoginResolver resolver,AppUserDetailsService details,PasswordEncoder encoder){this.resolver=resolver;this.details=details;this.encoder=encoder;dummyHash=encoder.encode(UUID.randomUUID().toString());}
/** True if the identity has the administrator base role in any workspace; such identities must use two-factor authentication, wherever they sign in first. */
 public boolean administersAnyTenant(String username){
  if(identities==null||memberships==null||username==null)return false;
  return identities.findByUsernameIgnoreCase(username).map(i->memberships.findWorkspaceMemberships(i.getId()).stream().anyMatch(m->m.getRole().getBaseRole()==de.ostms.lc.user.domain.UserRole.ADMIN)).orElse(false);
 }
 public Verified verify(String username,String password,String code){
  UUID tenant;UserDetails user;
  try{tenant=resolver.resolve(code);try{user=details.loadForTenant(username,tenant);}catch(AuthenticationException denied){
   if(code!=null&&!code.isBlank()||identities==null)throw denied;
   var identity=identities.findByUsernameIgnoreCase(username).orElseThrow(()->denied);
   tenant=memberships.findWorkspaceMemberships(identity.getId()).stream().map(m->m.getTenantId()).filter(id->tenants.findById(id).map(de.ostms.lc.tenant.domain.Tenant::isActive).orElse(false)).sorted().findFirst().orElseThrow(()->denied);
   user=details.loadForTenant(username,tenant);
  }}catch(AuthenticationException denied){
   // No active membership anywhere: only a platform administrator with two-factor authentication may continue, and only without a tenant workspace.
   UserDetails platformOnly=null;
   if(code==null||code.isBlank()){try{platformOnly=details.loadPlatformOnly(username);}catch(AuthenticationException notPlatform){platformOnly=null;}}
   if(platformOnly==null){encoder.matches(password==null?"":password,dummyHash);throw new BadCredentialsException("Login failed.");}
   tenant=de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID;user=platformOnly;
  }
  if(!encoder.matches(password==null?"":password,user.getPassword())||!user.isEnabled()||!user.isAccountNonLocked()||!user.isAccountNonExpired()||!user.isCredentialsNonExpired())throw new BadCredentialsException("Login failed.");
  return new Verified(tenant,user);
 }
}
