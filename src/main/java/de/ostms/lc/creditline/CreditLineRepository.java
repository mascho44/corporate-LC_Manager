package de.ostms.lc.creditline;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditLineRepository extends TenantScopedRepository<CreditLine,UUID> {
 @Override @Query("select e from CreditLine e where e.id=:id and "+OWNED)
 Optional<CreditLine> findById(@Param("id") UUID id);
 @Override @Query("select e from CreditLine e where "+OWNED+" order by e.bank, e.currency, e.name")
 List<CreditLine> findAll();
 @Query("select e from CreditLine e where e.name=:name and "+OWNED)
 Optional<CreditLine> byName(@Param("name") String name);
}
