package de.ostms.lc.lc.repository;
import de.ostms.lc.lc.domain.AutomationRule;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutomationRuleRepository extends TenantScopedRepository<AutomationRule,UUID> {
 @Override @Query("select e from AutomationRule e where e.id=:id and "+OWNED)
 Optional<AutomationRule> findById(@Param("id") UUID id);
 @Query("select e from AutomationRule e where e.trigger=:trigger and "+OWNED)
 Optional<AutomationRule> forTrigger(@Param("trigger") String trigger);
 @Query("select e from AutomationRule e where "+OWNED)
 List<AutomationRule> rules();
}
