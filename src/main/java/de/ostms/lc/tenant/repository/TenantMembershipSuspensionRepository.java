package de.ostms.lc.tenant.repository;
import de.ostms.lc.tenant.domain.TenantMembershipSuspension;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
public interface TenantMembershipSuspensionRepository extends TenantScopedRepository<TenantMembershipSuspension,UUID> {
 @Override @Query("select s from TenantMembershipSuspension s where s.id=:id and s.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
 Optional<TenantMembershipSuspension> findById(UUID id);
 @Query("select s from TenantMembershipSuspension s where s.userId=:userId and s.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
 Optional<TenantMembershipSuspension> findByUserId(UUID userId);
}
