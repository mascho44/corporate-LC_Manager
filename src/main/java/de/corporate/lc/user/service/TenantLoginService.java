package de.corporate.lc.user.service;
import de.corporate.lc.tenant.service.TenantLoginResolver;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;

@Service public class TenantLoginService {
 public record Verified(UUID tenantId,UserDetails user){}
 private final TenantLoginResolver resolver;private final AppUserDetailsService details;private final PasswordEncoder encoder;private final String dummyHash;
 public TenantLoginService(TenantLoginResolver resolver,AppUserDetailsService details,PasswordEncoder encoder){this.resolver=resolver;this.details=details;this.encoder=encoder;dummyHash=encoder.encode(UUID.randomUUID().toString());}
 public Verified verify(String username,String password,String code){
  UUID tenant;UserDetails user;
  try{tenant=resolver.resolve(code);user=details.loadForTenant(username,tenant);}catch(AuthenticationException denied){encoder.matches(password==null?"":password,dummyHash);throw new BadCredentialsException("Login failed.");}
  if(!encoder.matches(password==null?"":password,user.getPassword())||!user.isEnabled()||!user.isAccountNonLocked()||!user.isAccountNonExpired()||!user.isCredentialsNonExpired())throw new BadCredentialsException("Login failed.");
  return new Verified(tenant,user);
 }
}
