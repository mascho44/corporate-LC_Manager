package de.corporate.lc.tenant.service;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.TenantMembershipRepository;
import de.corporate.lc.user.domain.UserPermission;
import de.corporate.lc.user.domain.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Internal read model; not a tenant-provisioning or switching API. */
@Service @Transactional(readOnly=true)
public class TenantMembershipService {
 public record Membership(UUID tenantId,UUID userId,String username,UUID roleId,String roleName,Set<UserPermission> permissions,boolean active){}
 public record Access(UUID tenantId,UUID userId,UUID membershipId,UUID roleId,UserRole baseRole,Set<UserPermission> permissions){}
 private final TenantMembershipRepository memberships;
 public TenantMembershipService(TenantMembershipRepository memberships){this.memberships=memberships;}
 public List<Membership> list(){return memberships.findAll().stream().map(this::view).toList();}
 public Optional<Membership> forUser(UUID userId){return memberships.findByUserId(userId).map(this::view);}
 /** User identity is global; authorization comes only from the selected tenant membership. */
 public Access requireActiveAccess(UUID userId){
  if(userId==null)throw new AccessDeniedException("Active tenant membership is required.");
  var membership=memberships.findByUserId(userId).orElseThrow(()->new AccessDeniedException("Active tenant membership is required."));
  validate(membership);
  if(!membership.isActive()||!membership.getUser().isActive())throw new AccessDeniedException("Active tenant membership is required.");
  var role=membership.getRole();return new Access(membership.getTenantId(),membership.getUser().getId(),membership.getId(),role.getId(),role.getBaseRole(),Set.copyOf(role.getPermissions()));
 }
 private void validate(TenantMembership m){
  TenantContext.require(m.getTenantId());TenantContext.require(m.getRole().getTenantId());
 }
 private Membership view(TenantMembership m){
  validate(m);
  return new Membership(m.getTenantId(),m.getUser().getId(),m.getUser().getUsername(),m.getRole().getId(),m.getRole().getName(),Set.copyOf(m.getRole().getPermissions()),m.isActive()&&m.getUser().isActive());
 }
}
