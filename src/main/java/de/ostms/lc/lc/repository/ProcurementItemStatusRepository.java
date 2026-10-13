package de.ostms.lc.lc.repository;

import de.ostms.lc.lc.domain.ProcurementItemStatus;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcurementItemStatusRepository extends TenantScopedRepository<ProcurementItemStatus,UUID> {
 @Override @Query("select e from ProcurementItemStatus e where e.id=:id and "+OWNED)
 Optional<ProcurementItemStatus> findById(@Param("id") UUID id);
 @Override @Query("select e from ProcurementItemStatus e where "+OWNED)
 List<ProcurementItemStatus> findAll();
 @Query("select e from ProcurementItemStatus e where e.lcId=:lcId and "+OWNED)
 List<ProcurementItemStatus> forLc(@Param("lcId") UUID lcId);
 @Query("select e from ProcurementItemStatus e where e.lcId=:lcId and e.requirement=:requirement and "+OWNED)
 Optional<ProcurementItemStatus> one(@Param("lcId") UUID lcId,@Param("requirement") String requirement);
}
