package de.corporate.lc.user.service;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.TenantMembershipSuspensionRepository;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates identities, not business access. Bootstrap membership is suspended atomically. */
@Service
public class PlatformAccountCreationService {
 private final PlatformAdministrationService platform;
 private final AppUserRepository users;
 private final AppRoleRepository roles;
 private final TenantMembershipSuspensionRepository suspensions;
 private final TenantAdministrationLock lock;
 private final PasswordEncoder encoder;
 private final AuditService audit;
 public PlatformAccountCreationService(PlatformAdministrationService platform,AppUserRepository users,AppRoleRepository roles,TenantMembershipSuspensionRepository suspensions,TenantAdministrationLock lock,PasswordEncoder encoder,AuditService audit){this.platform=platform;this.users=users;this.roles=roles;this.suspensions=suspensions;this.lock=lock;this.encoder=encoder;this.audit=audit;}
 private void authorize(Authentication auth){if(!platform.enabled(auth))throw new AccessDeniedException("An active platform administrator with two-factor authentication is required.");}
 @Transactional
 public PlatformAdministrationService.Account create(String username,String displayName,String email,String password,Authentication auth){
  return createIdentity(username,displayName,email,password,false,auth);
 }
 @Transactional
 public PlatformAdministrationService.Account createPending(String username,String displayName,String email,String password,Authentication auth){
  return createIdentity(username,displayName,email,password,true,auth);
 }
 private PlatformAdministrationService.Account createIdentity(String username,String displayName,String email,String password,boolean pending,Authentication auth){
  try(var scope=TenantContext.open(Tenant.DEFAULT_ID)){
   authorize(auth);lock.acquire();authorize(auth);
   String name=username==null?"":username.trim().toLowerCase(java.util.Locale.ROOT);
   String display=displayName==null?"":displayName.trim();
   if(!name.matches("[a-z0-9][a-z0-9._@-]{0,99}"))throw new IllegalArgumentException("Username must contain 1–100 letters, numbers, dots, underscores, @ or hyphens.");
   if(display.isEmpty()||display.length()>255||display.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("A display name of 1–255 characters is required.");
   String address=UserEmail.required(email);
   if(password==null||password.length()>200)throw new IllegalArgumentException("Password must contain 10–200 characters.");
   UserService.validatePassword(password);
   if(users.existsByUsernameIgnoreCase(name))throw new IllegalArgumentException("Username already exists.");
   var role=roles.findByBaseRoleAndSystemRoleTrue(UserRole.VIEWER).orElseThrow(()->new IllegalStateException("Bootstrap viewer role is missing."));
   if(!Tenant.DEFAULT_ID.equals(role.getTenantId())||!role.getPermissions().isEmpty())throw new IllegalStateException("Bootstrap viewer role must have no write permissions.");
   var user=new AppUser();user.setUsername(name);user.setDisplayName(display);user.setEmail(address);user.setAssignedRole(role);user.setPasswordHash(encoder.encode(password));user.setActive(!pending);user.setInvitationPending(pending);
   users.saveAndFlush(user);
   var suspension=new TenantMembershipSuspension(user.getId());suspension.setSuspended(true);suspensions.saveAndFlush(suspension);
   audit.recordChangeInTransaction(auth,"PLATFORM_ACCOUNT_CREATED","USER",user.getId(),"Global identity created without tenant access",null,"{\"active\":"+!pending+"}");
   return new PlatformAdministrationService.Account(user.getId(),name,display,address,!pending,false,false,pending);
  }
 }
}
