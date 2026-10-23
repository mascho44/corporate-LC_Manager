package de.ostms.lc.guarantee;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuaranteeDetailRepository extends TenantScopedRepository<GuaranteeDetail,UUID> {
 @Override @Query("select e from GuaranteeDetail e where e.id=:id and "+OWNED)
 Optional<GuaranteeDetail> findById(@Param("id") UUID id);
 @Query("select e from GuaranteeDetail e where e.lcId=:lcId and "+OWNED)
 Optional<GuaranteeDetail> byLc(@Param("lcId") UUID lcId);
 @Override @Query("select e from GuaranteeDetail e where "+OWNED)
 List<GuaranteeDetail> findAll();
}
