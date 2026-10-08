package de.ostms.lc.tenant.service;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.TenantMembershipProvisioningStore;
import de.ostms.lc.user.api.AdministrationAuditSnapshot;
import de.ostms.lc.user.domain.UserPermission;
import de.ostms.lc.user.repository.*;
import de.ostms.lc.user.service.TenantAdministrationLock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Internal preparation only. No controller exposes provisioning or tenant selection. */
@Service
public class TenantMembershipProvisioningService {
 private final AppUserRepository users;
 private final AppRoleRepository roles;
 private final TenantMembershipService memberships;
 private final TenantMembershipProvisioningStore store;
 private final TenantAdministrationLock lock;
 private final AuditService audit;
 public TenantMembershipProvisioningService(AppUserRepository users,AppRoleRepository roles,TenantMembershipService memberships,TenantMembershipProvisioningStore store,TenantAdministrationLock lock,AuditService audit){
  this.users=users;this.roles=roles;this.memberships=memberships;this.store=store;this.lock=lock;this.audit=audit;
 }
 @Transactional
 public TenantMembershipService.Membership assignExistingIdentity(String username,UUID roleId,Authentication authentication){
  UUID tenantId=TenantContext.currentId();
  if(Tenant.DEFAULT_ID.equals(tenantId))throw new AccessDeniedException("Provisioning is not enabled in the bootstrap tenant.");
  if(authentication==null||!authentication.isAuthenticated()||authentication instanceof AnonymousAuthenticationToken)
   throw new AccessDeniedException("Authenticated tenant administration is required.");
  lock.acquire();
  var actor=users.findByUsernameIgnoreCase(authentication.getName()).orElseThrow(()->new AccessDeniedException("Active tenant administration is required."));
  var access=memberships.requireActiveAccess(actor.getId());TenantContext.require(access.tenantId());
  if(!access.permissions().contains(UserPermission.USER_MANAGE))throw new AccessDeniedException("Tenant user-management permission is required.");
  if(username==null||username.isBlank()||roleId==null)throw new IllegalArgumentException("Username and tenant role are required.");
  var role=roles.findById(roleId).orElseThrow(()->new NoSuchElementException("Role not found."));TenantContext.require(role.getTenantId());
  // Exact identity lookup only; this service never exposes a global user directory.
  var user=users.findByUsernameIgnoreCase(username.trim()).orElseThrow(()->new IllegalArgumentException("An eligible existing identity is required."));
  if(!user.isActive()||tenantId.equals(user.getTenantId()))throw new IllegalArgumentException("An eligible existing identity is required.");
  if(memberships.forUser(user.getId()).isPresent())throw new IllegalArgumentException("This identity already has a tenant membership.");
  store.create(tenantId,user.getId(),role.getId());
  var result=new TenantMembershipService.Membership(tenantId,user.getId(),user.getUsername(),role.getId(),role.getName(),Set.copyOf(role.getPermissions()),true,false,true);
  audit.recordChangeInTransaction(authentication,"USER_MEMBERSHIP_CREATED","MEMBERSHIP",user.getId(),"Existing identity assigned to current tenant",null,AdministrationAuditSnapshot.membership(result));
  return result;
 }
}
