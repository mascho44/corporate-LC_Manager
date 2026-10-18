package de.ostms.lc.user.service;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.service.TenantMembershipService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.stream.Stream;

@Service public class AppUserDetailsService implements UserDetailsService {
 private final AppUserRepository repo;private final TenantMembershipService memberships;
 public AppUserDetailsService(AppUserRepository repo,TenantMembershipService memberships){this.repo=repo;this.memberships=memberships;}
 /** Legacy/framework login always uses DEFAULT, regardless of the caller's thread scope. */
 @Override public UserDetails loadUserByUsername(String username){return loadForTenant(username,Tenant.homeId());}
 /** Explicit credential-stage scope, never inferred from a thread or an arbitrary header. */
public static final String PLATFORM_ONLY="PLATFORM_ONLY";
 /** A platform administrator with two-factor authentication may sign in without any tenant membership. The session carries no tenant authority and only reaches platform resources (CredentialSessionFilter). */
 public UserDetails loadPlatformOnly(String username){
  try(var scope=TenantContext.open(Tenant.homeId())){
   var u=repo.findByUsernameIgnoreCase(username).orElseThrow(()->new UsernameNotFoundException("User not found"));
   if(!u.isActive()||u.isInvitationPending()||!Tenant.homeId().equals(u.getTenantId())||!u.isPlatformAdministrator()||!u.isTotpEnabled()||!repo.hasLivePlatformAccess(u.getId()))
    throw new org.springframework.security.authentication.DisabledException("Platform access is not enabled.");
   return User.withUsername(u.getUsername()).password(u.getPasswordHash()).authorities(new SimpleGrantedAuthority(PLATFORM_ONLY)).build();
  }
 }
 public UserDetails loadForTenant(String username,UUID tenantId){
  if(tenantId==null)throw new org.springframework.security.authentication.DisabledException("Tenant access is not enabled.");
  try(var scope=TenantContext.open(tenantId)){
   var u=repo.findByUsernameIgnoreCase(username).orElseThrow(()->new UsernameNotFoundException("User not found"));
   if(u.isInvitationPending())throw new org.springframework.security.authentication.DisabledException("Invitation must be accepted.");
   u.validateRoleTenant();
   if(!Tenant.homeId().equals(u.getTenantId()))throw new org.springframework.security.authentication.DisabledException("Tenant access is not enabled.");
   TenantMembershipService.Access access;
   try{access=memberships.requireActiveAccess(u.getId());}catch(org.springframework.security.access.AccessDeniedException denied){throw new org.springframework.security.authentication.DisabledException("Tenant access is not enabled.",denied);}
   var authorities=Stream.concat(Stream.of(new SimpleGrantedAuthority("ROLE_"+access.baseRole().name())),access.permissions().stream().map(p->new SimpleGrantedAuthority("PERM_"+p.name()))).toList();
   return User.withUsername(u.getUsername()).password(u.getPasswordHash()).authorities(authorities).disabled(!u.isActive()).build();
  }
 }
}
