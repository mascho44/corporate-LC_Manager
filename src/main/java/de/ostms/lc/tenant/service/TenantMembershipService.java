package de.ostms.lc.tenant.service;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.TenantMembershipRepository;
import de.ostms.lc.user.domain.UserPermission;
import de.ostms.lc.user.domain.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Internal read model; not a tenant-provisioning or switching API. */
@Service @Transactional(readOnly=true)
public class TenantMembershipService {
 public record Membership(UUID tenantId,UUID userId,String username,UUID roleId,String roleName,Set<UserPermission> permissions,boolean active,boolean suspended,boolean identityActive){
  public Membership(UUID tenantId,UUID userId,String username,UUID roleId,String roleName,Set<UserPermission> permissions,boolean active){this(tenantId,userId,username,roleId,roleName,permissions,active,false,active);}
 }
 public record Access(UUID tenantId,UUID userId,UUID membershipId,UUID roleId,UserRole baseRole,Set<UserPermission> permissions){}
 private final TenantMembershipRepository memberships;
 private final de.ostms.lc.tenant.repository.TenantMembershipSuspensionRepository suspensions;
 private final de.ostms.lc.tenant.repository.TenantRepository tenants;
 public TenantMembershipService(TenantMembershipRepository memberships,de.ostms.lc.tenant.repository.TenantMembershipSuspensionRepository suspensions,de.ostms.lc.tenant.repository.TenantRepository tenants){this.memberships=memberships;this.suspensions=suspensions;this.tenants=tenants;}
 public List<Membership> list(){return memberships.findAll().stream().map(this::view).toList();}
 public Optional<Membership> forUser(UUID userId){return memberships.findByUserId(userId).map(this::view);}
 /** User identity is global; authorization comes only from the selected tenant membership. */
 public Access requireActiveAccess(UUID userId){
  if(!tenants.findById(TenantContext.currentId()).map(Tenant::isActive).orElse(false))throw new AccessDeniedException("Tenant access is suspended.");
  if(userId==null)throw new AccessDeniedException("Active tenant membership is required.");
  var membership=memberships.findByUserId(userId).orElseThrow(()->new AccessDeniedException("Active tenant membership is required."));
  validate(membership);
  if(!membership.isActive()||!membership.getUser().isActive()||suspended(userId))throw new AccessDeniedException("Active tenant membership is required.");
  var role=membership.getRole();return new Access(membership.getTenantId(),membership.getUser().getId(),membership.getId(),role.getId(),role.getBaseRole(),Set.copyOf(role.getPermissions()));
 }
 private boolean suspended(UUID userId){return suspensions.findByUserId(userId).map(s->{TenantContext.require(s.getTenantId());return s.isSuspended();}).orElse(false);}
 private void validate(TenantMembership m){
  TenantContext.require(m.getTenantId());TenantContext.require(m.getRole().getTenantId());
 }
 private Membership view(TenantMembership m){
  validate(m);boolean suspended=suspended(m.getUser().getId());
  return new Membership(m.getTenantId(),m.getUser().getId(),m.getUser().getUsername(),m.getRole().getId(),m.getRole().getName(),Set.copyOf(m.getRole().getPermissions()),m.isActive()&&m.getUser().isActive()&&!suspended,suspended,m.getUser().isActive());
 }
}
