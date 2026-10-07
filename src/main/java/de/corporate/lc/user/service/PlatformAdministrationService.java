package de.corporate.lc.user.service;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.user.domain.AppUser;
import de.corporate.lc.user.repository.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Global identities are deliberately operated in their home scope, never the caller's workspace. */
@Service public class PlatformAdministrationService {
 public record Account(UUID id,String username,String displayName,String email,boolean active,boolean platformAdministrator,boolean totpEnabled,boolean invitationPending){
  public Account(UUID id,String username,String displayName,String email,boolean active,boolean platformAdministrator,boolean totpEnabled){this(id,username,displayName,email,active,platformAdministrator,totpEnabled,false);}
 }
 private final AppUserRepository users;private final TenantAdministrationLock lock;private final AuditService audit;
 public PlatformAdministrationService(AppUserRepository users,TenantAdministrationLock lock,AuditService audit){this.users=users;this.lock=lock;this.audit=audit;}
 private Optional<AppUser> administrator(Authentication auth){
  if(auth==null||!auth.isAuthenticated()||auth instanceof AnonymousAuthenticationToken)return Optional.empty();
  return users.findByUsernameIgnoreCase(auth.getName()).filter(u->Tenant.DEFAULT_ID.equals(u.getTenantId())&&u.isActive()&&!u.isInvitationPending()&&u.isPlatformAdministrator()&&u.isTotpEnabled());
 }
 private AppUser require(Authentication auth){return administrator(auth).orElseThrow(()->new AccessDeniedException("An active platform administrator with two-factor authentication is required."));}
 @Transactional(readOnly=true) public boolean enabled(Authentication auth){try(var scope=TenantContext.open(Tenant.DEFAULT_ID)){return administrator(auth).isPresent();}}
 @Transactional(readOnly=true) public List<Account> accounts(Authentication auth){try(var scope=TenantContext.open(Tenant.DEFAULT_ID)){require(auth);return users.findAllByOrderByUsernameAsc().stream().map(this::view).toList();}}
 @Transactional public Account changeAccess(UUID id,boolean active,Authentication auth){
  try(var scope=TenantContext.open(Tenant.DEFAULT_ID)){
   require(auth);lock.acquire();var actor=require(auth);var target=users.findById(id).orElseThrow(()->new NoSuchElementException("Account not found."));
   if(active&&target.isInvitationPending())throw new IllegalArgumentException("Pending invitations must be accepted before activation.");
   if(!active&&target.getId().equals(actor.getId()))throw new IllegalArgumentException("You cannot suspend your own global account.");
   if(!active&&target.isActive()&&target.isPlatformAdministrator()&&target.isTotpEnabled()&&users.countEnabledPlatformAdministrators()<=1)throw new IllegalArgumentException("The last enabled platform administrator cannot be suspended.");
   if(target.isActive()==active)return view(target);
   String before=snapshot(target);target.setActive(active);users.saveAndFlush(target);
   audit.recordChangeInTransaction(auth,"PLATFORM_ACCOUNT_ACCESS_UPDATED","USER",id,"Global account access updated",before,snapshot(target));return view(target);
  }
 }
 @Transactional public Account changePlatformGrant(UUID id,boolean granted,Authentication auth){
  try(var scope=TenantContext.open(Tenant.DEFAULT_ID)){
   require(auth);lock.acquire();var actor=require(auth);var target=users.findForPasswordReset(id).filter(u->Tenant.DEFAULT_ID.equals(u.getTenantId())).orElseThrow(()->new NoSuchElementException("Account not found."));
   if(target.isPlatformAdministrator()==granted)return view(target);
   if(granted&&(!target.isActive()||target.isInvitationPending()||!target.isTotpEnabled()))throw new IllegalArgumentException("Platform administrators must be active and enrolled in two-factor authentication.");
   if(!granted&&target.getId().equals(actor.getId()))throw new IllegalArgumentException("You cannot revoke your own platform access.");
   if(!granted&&target.isActive()&&target.isTotpEnabled()&&users.countEnabledPlatformAdministrators()<=1)throw new IllegalArgumentException("The last enabled platform administrator cannot be removed.");
   String before="{\"platformAdministrator\":"+target.isPlatformAdministrator()+"}";target.setPlatformAdministrator(granted);users.saveAndFlush(target);
   audit.recordChangeInTransaction(auth,"PLATFORM_ADMINISTRATOR_UPDATED","USER",id,"Platform administrator grant updated",before,"{\"platformAdministrator\":"+granted+"}");return view(target);
  }
 }
 private Account view(AppUser user){return new Account(user.getId(),user.getUsername(),user.getDisplayName(),user.getEmail(),user.isActive(),user.isPlatformAdministrator(),user.isTotpEnabled(),user.isInvitationPending());}
 private String snapshot(AppUser user){return "{\"active\":"+user.isActive()+"}";}
}
