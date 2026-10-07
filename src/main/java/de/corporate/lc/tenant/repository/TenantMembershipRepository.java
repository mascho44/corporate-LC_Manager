package de.corporate.lc.tenant.repository;
import de.corporate.lc.tenant.domain.TenantMembership;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
public interface TenantMembershipRepository extends TenantScopedRepository<TenantMembership,UUID>{
 @Override @Query("select m from TenantMembership m where m.id=:id and m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 Optional<TenantMembership> findById(UUID id);
 @Query("select m from TenantMembership m where m.user.id=:userId and m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 Optional<TenantMembership> findByUserId(UUID userId);
 @Query("select count(m) from TenantMembership m where m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} and m.active=true and m.user.active=true and m.role.baseRole=de.corporate.lc.user.domain.UserRole.ADMIN and not exists(select s.id from TenantMembershipSuspension s where s.tenantId=m.tenantId and s.userId=m.user.id and s.suspended=true)")
 long countAccessibleAdministrators();
 @Query("select count(m) from TenantMembership m where m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} and m.active=true and m.user.active=true and not exists(select s.id from TenantMembershipSuspension s where s.tenantId=m.tenantId and s.userId=m.user.id and s.suspended=true)")
 long countAccessibleMembers();
 // Only the authenticated identity's chooser may use this deliberate global lookup.
 @Query("select m from TenantMembership m where m.user.id=:userId and m.active=true and m.user.active=true and not exists(select s.id from TenantMembershipSuspension s where s.tenantId=m.tenantId and s.userId=m.user.id and s.suspended=true)")
 List<TenantMembership> findWorkspaceMemberships(UUID userId);
 @Query("select count(m) from TenantMembership m where m.role.id=:roleId and m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 long countAssignedToRole(UUID roleId);
 @Query("select count(m) from TenantMembership m where m.role.id=:roleId and m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} and m.active=true and m.user.active=true and not exists(select s.id from TenantMembershipSuspension s where s.tenantId=m.tenantId and s.userId=m.user.id and s.suspended=true)")
 long countAccessibleAssignedToRole(UUID roleId);
}
