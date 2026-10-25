package de.ostms.lc.payment;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LcPaymentRepository extends TenantScopedRepository<LcPayment,UUID> {
 @Override @Query("select e from LcPayment e where e.id=:id and "+OWNED)
 Optional<LcPayment> findById(@Param("id") UUID id);
 @Query("select e from LcPayment e where e.lcId=:lcId and "+OWNED+" order by e.receivedOn, e.recordedAt")
 List<LcPayment> forLc(@Param("lcId") UUID lcId);
}
