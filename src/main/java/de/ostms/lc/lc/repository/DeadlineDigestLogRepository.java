package de.ostms.lc.lc.repository;

import de.ostms.lc.lc.domain.DeadlineDigestLog;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeadlineDigestLogRepository extends TenantScopedRepository<DeadlineDigestLog,UUID> {
 @Override @Query("select e from DeadlineDigestLog e where e.id=:id and "+OWNED)
 Optional<DeadlineDigestLog> findById(@Param("id") UUID id);
 @Override @Query("select e from DeadlineDigestLog e where "+OWNED)
 List<DeadlineDigestLog> findAll();
 @Query("select count(e)>0 from DeadlineDigestLog e where e.sentOn=:day and "+OWNED)
 boolean sentOn(@Param("day") LocalDate day);
}
