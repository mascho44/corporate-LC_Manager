package de.ostms.lc.tenant.service;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.*;
import de.ostms.lc.user.api.AdministrationAuditSnapshot;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import de.ostms.lc.user.service.PlatformAdministrationService;
import de.ostms.lc.user.service.TenantAdministrationLock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * Platform-level role and access changes for identities that belong to another tenant (shared identities).
 * Same safeguards as the tenant-local services: no home identities, no bootstrap tenant, the last active
 * administrator stays, and every change is audited inside the affected tenant. Live platform access (with
 * verified two-factor) is required on every call.
 */
@Service
public class PlatformMembershipService {
 private final PlatformAdministrationService platform;
 private final AppUserRepository users;
 private final TenantRepository tenants;
 private final AppRoleRepository roles;
 private final TenantMembershipRepository memberships;
 private final TenantMembershipSuspensionRepository suspensions;
 private final TenantMembershipService access;
 private final TenantMembershipProvisioningStore store;
 private final TenantAdministrationLock lock;
 private final AuditService audit;
 public PlatformMembershipService(PlatformAdministrationService platform,AppUserRepository users,TenantRepository tenants,AppRoleRepository roles,TenantMembershipRepository memberships,TenantMembershipSuspensionRepository suspensions,TenantMembershipService access,TenantMembershipProvisioningStore store,TenantAdministrationLock lock,AuditService audit){
  this.platform=platform;this.users=users;this.tenants=tenants;this.roles=roles;this.memberships=memberships;this.suspensions=suspensions;this.access=access;this.store=store;this.lock=lock;this.audit=audit;
 }

 private void requireManageable(UUID tenantId){
  if(Tenant.DEFAULT_ID.equals(tenantId))throw new AccessDeniedException("Shared identity administration is not enabled in the bootstrap tenant.");
  var tenant=tenants.findById(tenantId).orElseThrow(()->new NoSuchElementException("Tenant not found."));
  if(tenant.isArchived())throw new IllegalArgumentException("Archived tenants cannot be changed. Restore the tenant first.");
 }

 /** Assigns an existing, active foreign identity (exact username) to a tenant role; never creates identities. */
 @Transactional
 public TenantMembershipService.Membership assignExistingIdentity(UUID tenantId,String username,UUID roleId,Authentication authentication){
  platform.verifyLiveAccess(authentication);requireManageable(tenantId);
  if(username==null||username.isBlank()||roleId==null)throw new IllegalArgumentException("Username and tenant role are required.");
  try(var scope=TenantContext.open(tenantId)){
   lock.acquire();
   var role=roles.findById(roleId).orElseThrow(()->new NoSuchElementException("Role not found."));TenantContext.require(role.getTenantId());
   var user=users.findByUsernameIgnoreCase(username.trim()).orElseThrow(()->new IllegalArgumentException("An eligible existing identity is required."));
   if(!user.isActive()||user.isInvitationPending()||tenantId.equals(user.getTenantId()))throw new IllegalArgumentException("An eligible existing identity is required.");
   if(access.forUser(user.getId()).isPresent())throw new IllegalArgumentException("This identity already has a tenant membership.");
   store.create(tenantId,user.getId(),role.getId());
   var result=new TenantMembershipService.Membership(tenantId,user.getId(),user.getUsername(),role.getId(),role.getName(),Set.copyOf(role.getPermissions()),true,false,true);
   audit.recordChangeInTransaction(authentication,"PLATFORM_MEMBERSHIP_CREATED","MEMBERSHIP",user.getId(),"Existing identity assigned by platform administration",null,AdministrationAuditSnapshot.membership(result));
   return result;
  }
 }

 @Transactional
 public TenantMembershipService.Membership changeRole(UUID tenantId,UUID userId,UUID roleId,Authentication authentication){
  platform.verifyLiveAccess(authentication);requireManageable(tenantId);
  try(var scope=TenantContext.open(tenantId)){
   lock.acquire();
   var membership=memberships.findByUserId(userId).orElseThrow(()->new NoSuchElementException("Membership not found."));
   TenantContext.require(membership.getTenantId());TenantContext.require(membership.getRole().getTenantId());
   if(tenantId.equals(membership.getUser().getTenantId()))throw new AccessDeniedException("Home identities are administered in their own tenant.");
   var role=roles.findById(roleId).orElseThrow(()->new NoSuchElementException("Role not found."));TenantContext.require(role.getTenantId());
   var before=access.forUser(userId).orElseThrow(()->new NoSuchElementException("Membership not found."));
   if(before.roleId().equals(roleId))return before;
   if(before.active()&&membership.getRole().getBaseRole()==UserRole.ADMIN&&role.getBaseRole()!=UserRole.ADMIN&&memberships.countAccessibleAdministrators()<=1)
    throw new IllegalArgumentException("The last active administrator of the tenant cannot be demoted.");
   store.updateRole(tenantId,userId,roleId);
   var user=membership.getUser();
   var after=new TenantMembershipService.Membership(tenantId,userId,user.getUsername(),role.getId(),role.getName(),Set.copyOf(role.getPermissions()),before.active(),before.suspended(),before.identityActive());
   audit.recordChangeInTransaction(authentication,"PLATFORM_MEMBERSHIP_ROLE_UPDATED","MEMBERSHIP",userId,"Role changed by platform administration",AdministrationAuditSnapshot.membership(before),AdministrationAuditSnapshot.membership(after));
   return after;
  }
 }

 @Transactional
 public TenantMembershipService.Membership changeAccess(UUID tenantId,UUID userId,boolean suspended,Authentication authentication){
  platform.verifyLiveAccess(authentication);requireManageable(tenantId);
  try(var scope=TenantContext.open(tenantId)){
   lock.acquire();
   var membership=memberships.findByUserId(userId).orElseThrow(()->new NoSuchElementException("Membership not found."));
   TenantContext.require(membership.getTenantId());TenantContext.require(membership.getRole().getTenantId());
   if(tenantId.equals(membership.getUser().getTenantId()))throw new AccessDeniedException("Home identities are administered in their own tenant.");
   var before=access.forUser(userId).orElseThrow(()->new NoSuchElementException("Membership not found."));
   if(before.suspended()==suspended)return before;
   if(suspended&&before.active()&&membership.getRole().getBaseRole()==UserRole.ADMIN&&memberships.countAccessibleAdministrators()<=1)
    throw new IllegalArgumentException("The last active administrator of the tenant cannot be suspended.");
   if(!suspended&&(!membership.isActive()||!membership.getUser().isActive()))throw new IllegalArgumentException("The global account and membership must be active before access can be enabled.");
   var state=suspensions.findByUserId(userId).orElseGet(()->new TenantMembershipSuspension(userId));TenantContext.require(state.getTenantId());
   state.setSuspended(suspended);suspensions.save(state);
   var user=membership.getUser();var role=membership.getRole();
   var after=new TenantMembershipService.Membership(tenantId,userId,user.getUsername(),role.getId(),role.getName(),Set.copyOf(role.getPermissions()),membership.isActive()&&user.isActive()&&!suspended,suspended,user.isActive());
   audit.recordChangeInTransaction(authentication,"PLATFORM_MEMBERSHIP_ACCESS_UPDATED","MEMBERSHIP",userId,"Membership access changed by platform administration",AdministrationAuditSnapshot.membership(before),AdministrationAuditSnapshot.membership(after));
   return after;
  }
 }
}
