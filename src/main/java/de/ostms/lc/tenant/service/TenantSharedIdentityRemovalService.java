package de.ostms.lc.tenant.service;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.*;
import de.ostms.lc.user.api.AdministrationAuditSnapshot;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.user.service.TenantAdministrationLock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Removes an identity from the current (shared) tenant: the global account stays, the access to this tenant is gone. Never the own membership, never the last active administrator. */
@Service
public class TenantSharedIdentityRemovalService {
 private final AppUserRepository users;
 private final TenantMembershipRepository memberships;
 private final TenantMembershipService access;
 private final TenantMembershipProvisioningStore store;
 private final TenantAdministrationLock lock;
 private final AuditService audit;
 public TenantSharedIdentityRemovalService(AppUserRepository users,TenantMembershipRepository memberships,TenantMembershipService access,TenantMembershipProvisioningStore store,TenantAdministrationLock lock,AuditService audit){
  this.users=users;this.memberships=memberships;this.access=access;this.store=store;this.lock=lock;this.audit=audit;
 }
 @Transactional
 public void remove(UUID userId,Authentication authentication){
  if(Tenant.homeId().equals(TenantContext.currentId()))throw new AccessDeniedException("Shared identity administration is not enabled in the bootstrap tenant.");
  if(authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken)throw new AccessDeniedException("Authenticated tenant administration is required.");
  lock.acquire();
  var actor=users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow(()->new AccessDeniedException("Active tenant administration is required."));
  var actorAccess=access.requireActiveAccess(actor.getId());TenantContext.require(actorAccess.tenantId());
  if(!actorAccess.permissions().contains(UserPermission.USER_MANAGE))throw new AccessDeniedException("Tenant user-management permission is required.");
  var membership=memberships.findByUserId(userId).orElseThrow(()->new NoSuchElementException("Membership not found."));
  TenantContext.require(membership.getTenantId());TenantContext.require(membership.getRole().getTenantId());
  if(TenantContext.currentId().equals(membership.getUser().getTenantId()))throw new AccessDeniedException("Home identity administration remains separate.");
  if(actor.getId().equals(userId))throw new IllegalArgumentException("Your own membership cannot be removed.");
  var before=access.forUser(userId).orElseThrow(()->new NoSuchElementException("Membership not found."));TenantContext.require(before.tenantId());
  if(before.active()&&membership.getRole().getBaseRole()==UserRole.ADMIN&&memberships.countAccessibleAdministrators()<=1)
   throw new IllegalArgumentException("The last active administrator cannot be removed.");
  store.remove(TenantContext.currentId(),userId);
  audit.recordChangeInTransaction(authentication,"USER_MEMBERSHIP_REMOVED","MEMBERSHIP",userId,"Identity removed from the current tenant",AdministrationAuditSnapshot.membership(before),null);
 }
}
