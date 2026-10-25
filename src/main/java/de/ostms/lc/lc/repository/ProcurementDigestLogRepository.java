package de.ostms.lc.lc.repository;

import de.ostms.lc.lc.domain.ProcurementDigestLog;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcurementDigestLogRepository extends TenantScopedRepository<ProcurementDigestLog,UUID> {
 @Override @Query("select e from ProcurementDigestLog e where e.id=:id and "+OWNED)
 Optional<ProcurementDigestLog> findById(@Param("id") UUID id);
 @Override @Query("select e from ProcurementDigestLog e where "+OWNED)
 List<ProcurementDigestLog> findAll();
 @Query("select count(e)>0 from ProcurementDigestLog e where e.sentOn=:day and "+OWNED)
 boolean sentOn(@Param("day") LocalDate day);
}
