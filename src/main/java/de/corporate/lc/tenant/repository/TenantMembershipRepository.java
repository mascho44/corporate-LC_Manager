package de.corporate.lc.tenant.repository;
import de.corporate.lc.tenant.domain.TenantMembership;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
public interface TenantMembershipRepository extends TenantScopedRepository<TenantMembership,UUID>{
 @Override @Query("select m from TenantMembership m where m.id=:id and m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 Optional<TenantMembership> findById(UUID id);
 @Query("select m from TenantMembership m where m.user.id=:userId and m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 Optional<TenantMembership> findByUserId(UUID userId);
}
