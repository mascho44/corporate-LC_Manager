package de.ostms.lc.sla;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlaRuleRepository extends TenantScopedRepository<SlaRule,UUID> {
 @Override @Query("select e from SlaRule e where e.id=:id and "+OWNED)
 Optional<SlaRule> findById(@Param("id") UUID id);
 @Override @Query("select e from SlaRule e where "+OWNED+" order by e.name")
 List<SlaRule> findAll();
 @Query("select e from SlaRule e where e.name=:name and "+OWNED)
 Optional<SlaRule> byName(@Param("name") String name);
}
