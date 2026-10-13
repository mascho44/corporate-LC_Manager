package de.ostms.lc.lc.repository;

import de.ostms.lc.lc.domain.ProcurementLeadTime;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcurementLeadTimeRepository extends TenantScopedRepository<ProcurementLeadTime,UUID> {
 @Override @Query("select e from ProcurementLeadTime e where e.id=:id and "+OWNED)
 Optional<ProcurementLeadTime> findById(@Param("id") UUID id);
 @Override @Query("select e from ProcurementLeadTime e where "+OWNED)
 List<ProcurementLeadTime> findAll();
 @Query("select e from ProcurementLeadTime e where e.documentType=:type and "+OWNED)
 Optional<ProcurementLeadTime> byType(@Param("type") de.ostms.lc.document.domain.DocumentType type);
}
