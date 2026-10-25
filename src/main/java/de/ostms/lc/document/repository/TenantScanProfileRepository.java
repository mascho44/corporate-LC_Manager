package de.ostms.lc.document.repository;
import de.ostms.lc.document.domain.TenantScanProfile;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface TenantScanProfileRepository extends TenantScopedRepository<TenantScanProfile,UUID> {
 @Override @Query("select e from TenantScanProfile e where e.id=:id and "+OWNED)
 Optional<TenantScanProfile> findById(@Param("id") UUID id);
 @Query("select e from TenantScanProfile e where "+OWNED)
 Optional<TenantScanProfile> current();
}
