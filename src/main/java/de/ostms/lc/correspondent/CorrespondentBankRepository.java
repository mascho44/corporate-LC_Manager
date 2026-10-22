package de.ostms.lc.correspondent;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CorrespondentBankRepository extends TenantScopedRepository<CorrespondentBank,UUID> {
 @Override @Query("select e from CorrespondentBank e where e.id=:id and "+OWNED)
 Optional<CorrespondentBank> findById(@Param("id") UUID id);
 @Override @Query("select e from CorrespondentBank e where "+OWNED+" order by e.name")
 List<CorrespondentBank> findAll();
 @Query("select e from CorrespondentBank e where e.bic=:bic and "+OWNED)
 Optional<CorrespondentBank> byBic(@Param("bic") String bic);
}
