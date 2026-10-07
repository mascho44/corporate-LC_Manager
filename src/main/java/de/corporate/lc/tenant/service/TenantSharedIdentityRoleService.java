package de.corporate.lc.tenant.service;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.*;
import de.corporate.lc.user.api.AdministrationAuditSnapshot;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.*;
import de.corporate.lc.user.service.TenantAdministrationLock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Internal tenant-local role changes. Never change a shared identity's home role. */
@Service
public class TenantSharedIdentityRoleService {
 private final AppUserRepository users;
 private final AppRoleRepository roles;
 private final TenantMembershipRepository memberships;
 private final TenantMembershipService access;
 private final TenantMembershipProvisioningStore store;
 private final TenantAdministrationLock lock;
 private final AuditService audit;
 public TenantSharedIdentityRoleService(AppUserRepository users,AppRoleRepository roles,TenantMembershipRepository memberships,TenantMembershipService access,TenantMembershipProvisioningStore store,TenantAdministrationLock lock,AuditService audit){
  this.users=users;this.roles=roles;this.memberships=memberships;this.access=access;this.store=store;this.lock=lock;this.audit=audit;
 }
 @Transactional
 public TenantMembershipService.Membership changeRole(UUID userId,UUID roleId,Authentication authentication){
  var tenantId=TenantContext.currentId();
  if(Tenant.DEFAULT_ID.equals(tenantId))throw new AccessDeniedException("Shared identity role administration is not enabled in the bootstrap tenant.");
  if(authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken)throw new AccessDeniedException("Authenticated tenant administration is required.");
  lock.acquire();
  var actor=users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow(()->new AccessDeniedException("Active tenant administration is required."));
  var actorAccess=access.requireActiveAccess(actor.getId());TenantContext.require(actorAccess.tenantId());
  if(!actorAccess.permissions().contains(UserPermission.USER_MANAGE))throw new AccessDeniedException("Tenant user-management permission is required.");
  var membership=memberships.findByUserId(userId).orElseThrow(()->new NoSuchElementException("Membership not found."));
  TenantContext.require(membership.getTenantId());TenantContext.require(membership.getRole().getTenantId());
  if(tenantId.equals(membership.getUser().getTenantId()))throw new AccessDeniedException("Home identity administration remains separate.");
  var role=roles.findById(roleId).orElseThrow(()->new NoSuchElementException("Role not found."));TenantContext.require(role.getTenantId());
  var before=access.forUser(userId).orElseThrow(()->new NoSuchElementException("Membership not found."));TenantContext.require(before.tenantId());
  if(before.active()&&membership.getRole().getBaseRole()==UserRole.ADMIN&&role.getBaseRole()!=UserRole.ADMIN&&memberships.countAccessibleAdministrators()<=1)
   throw new IllegalArgumentException("The last active administrator cannot be demoted.");
  if(actor.getId().equals(userId)&&!role.getPermissions().contains(UserPermission.USER_MANAGE))throw new IllegalArgumentException("Your own membership must retain user-management permission.");
  store.updateRole(tenantId,userId,roleId);
  // The immutable read model may still hold its old role in this transaction.
  var user=membership.getUser();
  var after=new TenantMembershipService.Membership(tenantId,userId,user.getUsername(),role.getId(),role.getName(),Set.copyOf(role.getPermissions()),before.active(),before.suspended(),before.identityActive());
  audit.recordChangeInTransaction(authentication,"USER_MEMBERSHIP_ROLE_UPDATED","MEMBERSHIP",userId,"Shared identity role updated in current tenant",AdministrationAuditSnapshot.membership(before),AdministrationAuditSnapshot.membership(after));
  return after;
 }
}
