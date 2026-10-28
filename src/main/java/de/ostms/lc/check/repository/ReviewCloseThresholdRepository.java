package de.ostms.lc.check.repository;

import de.ostms.lc.check.domain.ReviewCloseThreshold;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.query.Param;

public interface ReviewCloseThresholdRepository extends TenantScopedRepository<ReviewCloseThreshold,UUID> {
 @Override @Query("select e from ReviewCloseThreshold e where e.id=:id and "+OWNED)
 Optional<ReviewCloseThreshold> findById(@Param("id") UUID id);
 @Override @Query("select e from ReviewCloseThreshold e where "+OWNED)
 List<ReviewCloseThreshold> findAll();
 @Query("select e from ReviewCloseThreshold e where "+OWNED+" order by e.currency,e.minimumAmount")
 List<ReviewCloseThreshold> ordered();
 @Modifying @Query("delete from ReviewCloseThreshold e where "+OWNED)
 void deleteForCurrentTenant();
}
