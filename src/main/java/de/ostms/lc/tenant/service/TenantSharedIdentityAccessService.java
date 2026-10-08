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

/** Internal foreign-identity access administration; no public endpoint or switching. */
@Service
public class TenantSharedIdentityAccessService {
 private final AppUserRepository users;
 private final TenantMembershipRepository memberships;
 private final TenantMembershipSuspensionRepository suspensions;
 private final TenantMembershipService access;
 private final TenantAdministrationLock lock;
 private final AuditService audit;
 public TenantSharedIdentityAccessService(AppUserRepository users,TenantMembershipRepository memberships,TenantMembershipSuspensionRepository suspensions,TenantMembershipService access,TenantAdministrationLock lock,AuditService audit){
  this.users=users;this.memberships=memberships;this.suspensions=suspensions;this.access=access;this.lock=lock;this.audit=audit;
 }
 @Transactional
 public TenantMembershipService.Membership changeAccess(UUID userId,boolean suspended,Authentication authentication){
  if(Tenant.DEFAULT_ID.equals(TenantContext.currentId()))throw new AccessDeniedException("Shared identity administration is not enabled in the bootstrap tenant.");
  if(authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken)throw new AccessDeniedException("Authenticated tenant administration is required.");
  lock.acquire();
  var actor=users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow(()->new AccessDeniedException("Active tenant administration is required."));
  var actorAccess=access.requireActiveAccess(actor.getId());TenantContext.require(actorAccess.tenantId());
  if(!actorAccess.permissions().contains(UserPermission.USER_MANAGE))throw new AccessDeniedException("Tenant user-management permission is required.");
  var membership=memberships.findByUserId(userId).orElseThrow(()->new NoSuchElementException("Membership not found."));
  TenantContext.require(membership.getTenantId());TenantContext.require(membership.getRole().getTenantId());
  if(TenantContext.currentId().equals(membership.getUser().getTenantId()))throw new AccessDeniedException("Home identity administration remains separate.");
  var before=access.forUser(userId).orElseThrow(()->new NoSuchElementException("Membership not found."));TenantContext.require(before.tenantId());
  if(suspended&&actor.getId().equals(userId))throw new IllegalArgumentException("Your own membership cannot be suspended.");
  if(suspended&&before.active()&&membership.getRole().getBaseRole()==UserRole.ADMIN&&memberships.countAccessibleAdministrators()<=1)
   throw new IllegalArgumentException("The last active administrator cannot be suspended.");
  if(!suspended&&(!membership.isActive()||!membership.getUser().isActive()))throw new IllegalArgumentException("The global account and membership must be active before access can be enabled.");
  var state=suspensions.findByUserId(userId).orElseGet(()->new TenantMembershipSuspension(userId));TenantContext.require(state.getTenantId());
  state.setSuspended(suspended);suspensions.save(state);
  var user=membership.getUser();var role=membership.getRole();
  var after=new TenantMembershipService.Membership(membership.getTenantId(),userId,user.getUsername(),role.getId(),role.getName(),Set.copyOf(role.getPermissions()),membership.isActive()&&user.isActive()&&!suspended,suspended,user.isActive());
  audit.recordChangeInTransaction(authentication,"USER_MEMBERSHIP_ACCESS_UPDATED","MEMBERSHIP",userId,"Shared identity access updated in current tenant",AdministrationAuditSnapshot.membership(before),AdministrationAuditSnapshot.membership(after));
  return after;
 }
}
