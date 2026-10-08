package de.ostms.lc.charges;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ChargeProfileRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<ChargeProfile,UUID>{
 @Override @org.springframework.data.jpa.repository.Query("select p from ChargeProfile p where p.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") java.util.List<ChargeProfile> findAll();
 @Override @org.springframework.data.jpa.repository.Query("select p from ChargeProfile p where p.id=:id and p.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") java.util.Optional<ChargeProfile> findById(@org.springframework.data.repository.query.Param("id") UUID id);
}
