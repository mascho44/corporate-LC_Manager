package de.ostms.lc.check.repository;

import de.ostms.lc.check.domain.LcReviewClosure;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LcReviewClosureRepository extends TenantScopedRepository<LcReviewClosure,UUID> {
 @Override @Query("select e from LcReviewClosure e where e.id=:id and "+OWNED)
 Optional<LcReviewClosure> findById(@Param("id") UUID id);
 @Override @Query("select e from LcReviewClosure e where "+OWNED)
 List<LcReviewClosure> findAll();
 @Query("select e from LcReviewClosure e where e.lcId=:lcId and "+OWNED)
 Optional<LcReviewClosure> forLc(@Param("lcId") UUID lcId);
}
