package de.ostms.lc.ai;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface TenantAiSettingRepository extends TenantScopedRepository<TenantAiSetting,UUID> {
 @Override @Query("select e from TenantAiSetting e where e.id=:id and "+OWNED)
 Optional<TenantAiSetting> findById(@Param("id") UUID id);
 @Query("select e from TenantAiSetting e where "+OWNED)
 Optional<TenantAiSetting> current();
}
