package de.corporate.lc.tenant.service;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.TenantMembershipRepository;
import de.corporate.lc.user.domain.UserPermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Internal read model; not a tenant-provisioning or switching API. */
@Service @Transactional(readOnly=true)
public class TenantMembershipService {
 public record Membership(UUID tenantId,UUID userId,String username,UUID roleId,String roleName,Set<UserPermission> permissions,boolean active){}
 private final TenantMembershipRepository memberships;
 public TenantMembershipService(TenantMembershipRepository memberships){this.memberships=memberships;}
 public List<Membership> list(){return memberships.findAll().stream().map(this::view).toList();}
 public Optional<Membership> forUser(UUID userId){return memberships.findByUserId(userId).map(this::view);}
 private Membership view(TenantMembership m){
  TenantContext.require(m.getTenantId());TenantContext.require(m.getRole().getTenantId());TenantContext.require(m.getUser().getTenantId());
  return new Membership(m.getTenantId(),m.getUser().getId(),m.getUser().getUsername(),m.getRole().getId(),m.getRole().getName(),Set.copyOf(m.getRole().getPermissions()),m.isActive()&&m.getUser().isActive());
 }
}
