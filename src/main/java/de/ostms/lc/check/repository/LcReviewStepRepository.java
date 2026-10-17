package de.ostms.lc.check.repository;

import de.ostms.lc.check.domain.LcReviewStep;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LcReviewStepRepository extends TenantScopedRepository<LcReviewStep,UUID> {
 @Override @Query("select e from LcReviewStep e where e.id=:id and "+OWNED)
 Optional<LcReviewStep> findById(@Param("id") UUID id);
 @Override @Query("select e from LcReviewStep e where "+OWNED)
 List<LcReviewStep> findAll();
 @Query("select e from LcReviewStep e where e.lcId=:lcId and "+OWNED+" order by e.completedAt")
 List<LcReviewStep> forLc(@Param("lcId") UUID lcId);
 @Query("select e from LcReviewStep e where e.lcId=:lcId and e.stepKey=:key and "+OWNED)
 Optional<LcReviewStep> byKey(@Param("lcId") UUID lcId,@Param("key") String key);
}
