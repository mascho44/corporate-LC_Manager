package de.ostms.lc.intake;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntakeChannelRepository extends TenantScopedRepository<IntakeChannelConfig,UUID> {
 @Override @Query("select e from IntakeChannelConfig e where e.id=:id and "+OWNED)
 Optional<IntakeChannelConfig> findById(@Param("id") UUID id);
 @Override @Query("select e from IntakeChannelConfig e where "+OWNED+" order by e.name")
 List<IntakeChannelConfig> findAll();
 @Query("select e from IntakeChannelConfig e where e.name=:name and "+OWNED)
 Optional<IntakeChannelConfig> byName(@Param("name") String name);
}
