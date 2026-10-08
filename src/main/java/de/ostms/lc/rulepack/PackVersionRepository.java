package de.ostms.lc.rulepack;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PackVersionRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<StoredPackVersion,UUID> {
 @org.springframework.data.jpa.repository.Query("select count(v)>0 from StoredPackVersion v where v.packId=:packId and v.version=:version and v.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") boolean existsByPackIdAndVersion(@org.springframework.data.repository.query.Param("packId") String packId,@org.springframework.data.repository.query.Param("version") String version);
 @org.springframework.data.jpa.repository.Query("select v from StoredPackVersion v where v.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} order by v.importedAt desc") List<StoredPackVersion> findAllByOrderByImportedAtDesc();
 @Override @org.springframework.data.jpa.repository.Query("select v from StoredPackVersion v where v.id=:id and v.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") Optional<StoredPackVersion> findById(@org.springframework.data.repository.query.Param("id") UUID id);
 @Override @org.springframework.data.jpa.repository.Query("select v from StoredPackVersion v where v.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") List<StoredPackVersion> findAll();
}
