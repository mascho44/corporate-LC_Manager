package de.ostms.lc.guarantee;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface GuaranteeRequestRepository extends TenantScopedRepository<GuaranteeRequest,UUID> {
 @Override @Query("select e from GuaranteeRequest e where e.id=:id and "+OWNED)
 Optional<GuaranteeRequest> findById(@Param("id") UUID id);
 @Query("select e from GuaranteeRequest e where e.lcId=:lcId and "+OWNED)
 Optional<GuaranteeRequest> byLc(@Param("lcId") UUID lcId);
}
