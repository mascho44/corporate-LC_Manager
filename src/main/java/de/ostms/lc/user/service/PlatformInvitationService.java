package de.ostms.lc.user.service;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.*;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
public class PlatformInvitationService {
 public record RoleChoice(UUID id,String name,Set<UserPermission> permissions){}
 public record TenantChoice(UUID id,String name,String code,List<RoleChoice> roles){}
 public record InvitationView(UUID userId,String username,String email,UUID tenantId,String tenantName,UUID roleId,String roleName,Instant expiresAt,String status,int attempts,Instant nextAttemptAt,String errorCode){}
 public record MailRequested(UUID userId,String token,String username,String email,String tenantCode,String tenantName){@Override public String toString(){return "InvitationMailRequested[redacted]";}}
 private final PlatformAdministrationService platform;private final PlatformAccountCreationService creation;
 private final AppUserRepository users;private final AppRoleRepository roles;private final TenantRepository tenants;
 private final PlatformInvitationRepository invitations;private final TenantMembershipSuspensionRepository suspensions;
 private final TenantMembershipProvisioningStore memberships;private final TenantAdministrationLock lock;
 private final PasswordEncoder encoder;private final AuditService audit;private final ApplicationEventPublisher events;
 private final boolean mailEnabled;private final String mailFrom;private final Clock clock=Clock.systemUTC();private final SecureRandom random=new SecureRandom();
 public PlatformInvitationService(PlatformAdministrationService platform,PlatformAccountCreationService creation,AppUserRepository users,AppRoleRepository roles,TenantRepository tenants,PlatformInvitationRepository invitations,TenantMembershipSuspensionRepository suspensions,TenantMembershipProvisioningStore memberships,TenantAdministrationLock lock,PasswordEncoder encoder,AuditService audit,ApplicationEventPublisher events,@Value("${app.mail.enabled:false}") boolean mailEnabled,@Value("${app.mail.from:}") String mailFrom){this.platform=platform;this.creation=creation;this.users=users;this.roles=roles;this.tenants=tenants;this.invitations=invitations;this.suspensions=suspensions;this.memberships=memberships;this.lock=lock;this.encoder=encoder;this.audit=audit;this.events=events;this.mailEnabled=mailEnabled;this.mailFrom=mailFrom;}
 private AppUser authorize(Authentication auth){if(!platform.enabled(auth))throw new AccessDeniedException("An active platform administrator with two-factor authentication is required.");return users.findByUsernameIgnoreCase(auth.getName()).orElseThrow(()->new AccessDeniedException("Platform access unavailable."));}
 @Transactional(readOnly=true) public List<TenantChoice> choices(Authentication auth){
  try(var home=TenantContext.open(Tenant.DEFAULT_ID)){authorize(auth);return tenants.findAll().stream().filter(Tenant::isActive).sorted(Comparator.comparing(Tenant::getName)).map(tenant->{try(var scope=TenantContext.open(tenant.getId())){return new TenantChoice(tenant.getId(),tenant.getName(),tenant.getCode(),roles.findAllByOrderByNameAsc().stream().map(role->new RoleChoice(role.getId(),role.getName(),Set.copyOf(role.getPermissions()))).toList());}}).toList();}
 }
 @Transactional(readOnly=true) public List<InvitationView> list(Authentication auth){try(var home=TenantContext.open(Tenant.DEFAULT_ID)){authorize(auth);return invitations.findAll().stream().map(this::view).toList();}}
 @Transactional public InvitationView invite(String username,String displayName,String email,UUID tenantId,UUID roleId,Authentication auth){
  try(var home=TenantContext.open(Tenant.DEFAULT_ID)){
   authorize(auth);lock.acquire();var actor=authorize(auth);requireMail();var target=target(tenantId,roleId);
   String name=username==null?"":username.trim().toLowerCase(Locale.ROOT);String address=UserEmail.required(email);
   var existing=users.findByUsernameIgnoreCase(name);AppUser user;
   if(existing.isPresent()){
    user=users.findForPasswordReset(existing.get().getId()).orElseThrow(PlatformInvitationService::invalid);
    if(!Tenant.DEFAULT_ID.equals(user.getTenantId())||!user.isInvitationPending()||user.isActive()||!address.equals(user.getEmail())||!Objects.equals(displayName==null?"":displayName.trim(),user.getDisplayName()))throw new IllegalArgumentException("Username already exists. Pending invitations must match the existing identity.");
   }else{var created=creation.createPending(name,displayName,address,"InviteOnly1-"+secret(),auth);user=users.findById(created.id()).orElseThrow();}
   return issue(user,actor,target,auth);
  }
 }
 @Transactional public InvitationView resend(UUID userId,Authentication auth){
  try(var home=TenantContext.open(Tenant.DEFAULT_ID)){
   authorize(auth);lock.acquire();var actor=authorize(auth);requireMail();var saved=invitations.findByUserId(userId).orElseThrow(()->new NoSuchElementException("Invitation not found."));var target=target(saved.getTenantId(),saved.getRoleId());
   var user=users.findForPasswordReset(userId).orElseThrow(PlatformInvitationService::invalid);if(!Tenant.DEFAULT_ID.equals(user.getTenantId())||!user.isInvitationPending()||user.isActive())throw invalid();return issue(user,actor,target,auth);
  }
 }
 @Transactional public void revoke(UUID userId,Authentication auth){
  try(var home=TenantContext.open(Tenant.DEFAULT_ID)){authorize(auth);lock.acquire();authorize(auth);var saved=invitations.findByUserId(userId).orElseThrow(()->new NoSuchElementException("Invitation not found."));var user=users.findForPasswordReset(userId).orElseThrow(PlatformInvitationService::invalid);if(!user.isInvitationPending()||user.isActive())throw invalid();invitations.deleteForUser(userId);audit.recordChangeInTransaction(auth,"PLATFORM_INVITATION_REVOKED","INVITATION",userId,"Invitation revoked; identity remains blocked",snapshot(saved),"{\"status\":\"REVOKED\"}");}
 }
 private record Target(Tenant tenant,AppRole role){}
 private Target target(UUID tenantId,UUID roleId){
  if(tenantId==null||roleId==null)throw new IllegalArgumentException("Tenant and role are required.");
  try(var scope=TenantContext.open(tenantId)){lock.acquire();var tenant=tenants.findById(tenantId).filter(Tenant::isActive).orElseThrow(()->new AccessDeniedException("Tenant unavailable."));var role=roles.findById(roleId).orElseThrow(()->new NoSuchElementException("Role not found in target tenant."));TenantContext.require(role.getTenantId());return new Target(tenant,role);}
 }
 private InvitationView issue(AppUser user,AppUser actor,Target target,Authentication auth){
  invitations.deleteForUser(user.getId());invitations.flush();String token=secret();
  var saved=new PlatformInvitation(CredentialStamp.of(token),user.getId(),actor.getId(),target.tenant().getId(),target.role().getId(),roleStamp(target.role()),CredentialStamp.of(user.getPasswordHash()),user.getEmail(),clock.instant().plus(Duration.ofHours(24)));
  invitations.saveAndFlush(saved);audit.recordChangeInTransaction(auth,"PLATFORM_INVITATION_ISSUED","INVITATION",user.getId(),"Invitation issued; membership deferred until acceptance",null,snapshot(saved));
  events.publishEvent(new MailRequested(user.getId(),token,user.getUsername(),user.getEmail(),target.tenant().getCode(),target.tenant().getName()));return view(saved);
 }
 /** Public possession-of-secret endpoint: no user directory or tenant details are returned. */
 @Transactional public void accept(String token,String password){
  if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw invalid();UserService.validatePassword(password);if(password.length()>200||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Password must contain at most 72 UTF-8 bytes and 200 characters.");
  String hash=CredentialStamp.of(token);
  try(var home=TenantContext.open(Tenant.DEFAULT_ID)){
   var initial=invitations.findById(hash).orElseThrow(PlatformInvitationService::invalid);lock.acquire();
   if(!invitations.existsById(hash))throw invalid();var saved=invitations.findById(hash).orElseThrow(PlatformInvitationService::invalid);
   if(!saved.getExpiresAt().isAfter(clock.instant()))throw invalid();
   var target=target(saved.getTenantId(),saved.getRoleId());if(!roleStamp(target.role()).equals(saved.getRoleStamp()))throw invalid();
   var user=users.findForPasswordReset(saved.getUserId()).orElseThrow(PlatformInvitationService::invalid);
   var issuer=users.findById(saved.getIssuerId()).orElseThrow(PlatformInvitationService::invalid);
   if(!issuer.isActive()||issuer.isInvitationPending()||!issuer.isPlatformAdministrator()||!issuer.isTotpEnabled())throw invalid();
   if(!Tenant.DEFAULT_ID.equals(user.getTenantId())||!user.isInvitationPending()||user.isActive()||!saved.getEmail().equals(user.getEmail())||!saved.getCredentialStamp().equals(CredentialStamp.of(user.getPasswordHash())))throw invalid();
   user.setPasswordHash(encoder.encode(password));user.setInvitationPending(false);user.setActive(true);
   if(Tenant.DEFAULT_ID.equals(saved.getTenantId()))user.setAssignedRole(target.role());users.saveAndFlush(user);
   if(Tenant.DEFAULT_ID.equals(saved.getTenantId())){var state=suspensions.findByUserId(user.getId()).orElseThrow(PlatformInvitationService::invalid);state.setSuspended(false);suspensions.saveAndFlush(state);}
   else{try(var scope=TenantContext.open(saved.getTenantId())){memberships.create(saved.getTenantId(),user.getId(),saved.getRoleId());}}
   invitations.deleteForUser(user.getId());audit.recordChangeInTransaction(UsernamePasswordAuthenticationToken.authenticated(user.getUsername(),null,List.of()),"PLATFORM_INVITATION_ACCEPTED","INVITATION",user.getId(),"Password set and explicitly selected tenant membership activated",snapshot(saved),"{\"status\":\"ACCEPTED\"}");
  }
 }
 private InvitationView view(PlatformInvitation saved){
  var user=users.findById(saved.getUserId()).orElseThrow();var tenant=tenants.findById(saved.getTenantId()).orElseThrow();String roleName;
  try(var scope=TenantContext.open(saved.getTenantId())){roleName=roles.findById(saved.getRoleId()).map(AppRole::getName).orElse("Unavailable role");}
  return new InvitationView(user.getId(),user.getUsername(),user.getEmail(),tenant.getId(),tenant.getName(),saved.getRoleId(),roleName,saved.getExpiresAt(),saved.getExpiresAt().isAfter(clock.instant())?saved.getDeliveryStatus():"EXPIRED",saved.getDeliveryAttempts(),saved.getNextDeliveryAttempt(),saved.getDeliveryError());
 }
 private void requireMail(){if(!mailEnabled||mailFrom.isBlank())throw new IllegalArgumentException("Invitation email delivery is not configured.");}
 private String secret(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 static String roleStamp(AppRole role){return CredentialStamp.of(role.getId()+":"+role.getBaseRole()+":"+role.getPermissions().stream().map(Enum::name).sorted().toList());}
 private String snapshot(PlatformInvitation saved){return "{\"tenantId\":\""+saved.getTenantId()+"\",\"roleId\":\""+saved.getRoleId()+"\",\"status\":\"PENDING\"}";}
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invitation is invalid, revoked or expired. Please request a new invitation from your administrator.");}
}
