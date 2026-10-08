package de.ostms.lc.tenant.service;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.TenantMembershipRepository;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import de.ostms.lc.user.service.TenantAdministrationLock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Tenant-local role/access administration. Home-role projection remains bootstrap-owned. */
@Service public class TenantMembershipAdministrationService {
 private final TenantMembershipRepository memberships;
 private final AppUserRepository users;
 private final AppRoleRepository roles;
 private final TenantAdministrationLock administration;
 private final de.ostms.lc.tenant.repository.TenantMembershipSuspensionRepository suspensions;
 public TenantMembershipAdministrationService(TenantMembershipRepository memberships,AppUserRepository users,AppRoleRepository roles,TenantAdministrationLock administration,de.ostms.lc.tenant.repository.TenantMembershipSuspensionRepository suspensions){
  this.memberships=memberships;this.users=users;this.roles=roles;this.administration=administration;this.suspensions=suspensions;
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public TenantMembershipService.Membership getForAdministration(UUID userId){
  administration.acquire();var membership=one(userId);return view(membership,membership.getRole());
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public TenantMembershipService.Membership updateRole(UUID userId,UUID roleId,String currentUsername){
  administration.acquire();var membership=one(userId);var user=membership.getUser();
  var role=roles.findById(roleId).orElseThrow(()->new NoSuchElementException("Role not found."));
  TenantContext.require(role.getTenantId());
  if(membership.isActive()&&user.isActive()&&!suspended(userId)&&user.getRole()==UserRole.ADMIN&&role.getBaseRole()!=UserRole.ADMIN&&users.countByRoleAndActiveTrue(UserRole.ADMIN)<=1)
   throw new IllegalArgumentException("The last active administrator cannot be demoted.");
  if(user.getUsername().equalsIgnoreCase(currentUsername)&&!role.getPermissions().contains(UserPermission.USER_MANAGE))
   throw new IllegalArgumentException("Your own membership must retain user-management permission.");
  user.setAssignedRole(role);
  // PostgreSQL V60 sync updates only the home membership; no direct membership write.
  users.flush();
  return view(membership,role);
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public TenantMembershipService.Membership updateSuspension(UUID userId,boolean suspended,String currentUsername){
  administration.acquire();var membership=one(userId);var user=membership.getUser();
  if(suspended&&user.getUsername().equalsIgnoreCase(currentUsername))throw new IllegalArgumentException("Your own membership cannot be suspended.");
  if(suspended&&membership.isActive()&&user.isActive()&&!suspended(userId)&&membership.getRole().getBaseRole()==UserRole.ADMIN&&users.countByRoleAndActiveTrue(UserRole.ADMIN)<=1)
   throw new IllegalArgumentException("The last active administrator cannot be suspended.");
  if(!suspended&&(!user.isActive()||!membership.isActive()))throw new IllegalArgumentException("The global account must be active before membership access can be enabled.");
  var state=suspensions.findByUserId(userId).orElseGet(()->new TenantMembershipSuspension(userId));
  TenantContext.require(state.getTenantId());state.setSuspended(suspended);suspensions.save(state);
  return view(membership,membership.getRole(),suspended);
 }
 private boolean suspended(UUID userId){return suspensions.findByUserId(userId).map(state->{TenantContext.require(state.getTenantId());return state.isSuspended();}).orElse(false);}
 private TenantMembership one(UUID userId){
  if(!Tenant.DEFAULT_ID.equals(TenantContext.currentId()))throw new AccessDeniedException("Membership editing is not enabled for this tenant.");
  var membership=memberships.findByUserId(userId).orElseThrow(()->new NoSuchElementException("Membership not found."));
  TenantContext.require(membership.getTenantId());TenantContext.require(membership.getRole().getTenantId());TenantContext.require(membership.getUser().getTenantId());
  membership.getUser().validateRoleTenant();
  return membership;
 }
 private TenantMembershipService.Membership view(TenantMembership membership,AppRole role){
  return view(membership,role,suspended(membership.getUser().getId()));
 }
 private TenantMembershipService.Membership view(TenantMembership membership,AppRole role,boolean suspended){
  var user=membership.getUser();
  return new TenantMembershipService.Membership(membership.getTenantId(),user.getId(),user.getUsername(),role.getId(),role.getName(),Set.copyOf(role.getPermissions()),membership.isActive()&&user.isActive()&&!suspended,suspended,user.isActive());
 }
}
