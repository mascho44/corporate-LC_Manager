package de.ostms.lc.user.service;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.service.TenantMembershipService;
import de.ostms.lc.user.domain.UserPermission;
import de.ostms.lc.user.repository.AppRoleRepository;
import de.ostms.lc.user.repository.AppUserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Invitations issued by the administrators of a tenant for their own tenant. Everything is limited to the current tenant; existing accounts are never revealed. */
@Service
public class TenantInvitationService {
 public record Overview(List<PlatformInvitationService.RoleChoice> roles,List<PlatformInvitationService.InvitationView> invitations){}
 public record Result(boolean invited,String message,PlatformInvitationService.InvitationView invitation){}
 private final PlatformInvitationService invitations;private final TenantMembershipService memberships;private final AppUserRepository users;private final AppRoleRepository roles;
 public TenantInvitationService(PlatformInvitationService invitations,TenantMembershipService memberships,AppUserRepository users,AppRoleRepository roles){this.invitations=invitations;this.memberships=memberships;this.users=users;this.roles=roles;}

 /** The current tenant, after checking that the caller manages users in it. The bootstrap tenant only homes the accounts and has no invitations. */
 private UUID requireManager(Authentication auth){
  UUID tenant=TenantContext.currentId();
  if(Tenant.DEFAULT_ID.equals(tenant))throw new AccessDeniedException("Einladungen gibt es nur in Mandanten.");
  if(auth==null||!auth.isAuthenticated())throw new AccessDeniedException("Anmeldung erforderlich.");
  var user=users.findByUsernameIgnoreCase(auth.getName()).orElseThrow(()->new AccessDeniedException("Konto nicht verfügbar."));
  var access=memberships.requireActiveAccess(user.getId());TenantContext.require(access.tenantId());
  if(!access.permissions().contains(UserPermission.USER_MANAGE))throw new AccessDeniedException("Die Berechtigung zur Benutzerverwaltung fehlt.");
  return tenant;
 }
 @Transactional(readOnly=true) public Overview overview(Authentication auth){
  UUID tenant=requireManager(auth);
  var choices=roles.findAllByOrderByNameAsc().stream().map(r->new PlatformInvitationService.RoleChoice(r.getId(),r.getName(),Set.copyOf(r.getPermissions()))).toList();
  return new Overview(choices,invitations.listForTenant(tenant));
 }
 @Transactional public Result invite(String displayName,String email,UUID roleId,Authentication auth){
  UUID tenant=requireManager(auth);
  var result=invitations.inviteForTenant(displayName,email,tenant,roleId,auth);
  return result.map(v->new Result(true,"Die Einladung wurde per E-Mail versendet.",v)).orElseGet(()->new Result(false,"Für diese Adresse existiert bereits ein Konto. Bitte den Plattformadministrator bitten, es dem Mandanten zuzuweisen.",null));
 }
 @Transactional public PlatformInvitationService.InvitationView resend(UUID userId,Authentication auth){return invitations.resendForTenant(userId,requireManager(auth),auth);}
 @Transactional public void revoke(UUID userId,Authentication auth){invitations.revokeForTenant(userId,requireManager(auth),auth);}
}
