package de.ostms.lc.charges;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ChargeEstimateRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<ChargeEstimate,UUID>{
 @org.springframework.data.jpa.repository.Query("select e from ChargeEstimate e where e.lcId=:lcId and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} order by e.createdAt desc") List<ChargeEstimate> findByLcIdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("lcId") UUID lcId);
 @Override @org.springframework.data.jpa.repository.Query("select e from ChargeEstimate e where e.id=:id and e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") Optional<ChargeEstimate> findById(@org.springframework.data.repository.query.Param("id") UUID id);
 @Override @org.springframework.data.jpa.repository.Query("select e from ChargeEstimate e where e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") List<ChargeEstimate> findAll();
}
