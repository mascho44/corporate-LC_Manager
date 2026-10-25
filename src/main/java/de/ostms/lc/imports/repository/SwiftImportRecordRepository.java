package de.ostms.lc.imports.repository;
import de.ostms.lc.imports.domain.SwiftImportRecord; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SwiftImportRecordRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<SwiftImportRecord,UUID>{
 default List<SwiftImportRecord> findTop20ByOrderByImportedAtDesc(){return findRecent(org.springframework.data.domain.PageRequest.of(0,20));}
 @org.springframework.data.jpa.repository.Query("select r from SwiftImportRecord r where r.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} order by r.importedAt desc") List<SwiftImportRecord> findRecent(org.springframework.data.domain.Pageable pageable);
 @org.springframework.data.jpa.repository.Query("select count(r) from SwiftImportRecord r where r.status=:status and r.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") long countByStatus(@org.springframework.data.repository.query.Param("status") String status);
 @Override @org.springframework.data.jpa.repository.Query("select r from SwiftImportRecord r where r.id=:id and r.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") Optional<SwiftImportRecord> findById(@org.springframework.data.repository.query.Param("id") UUID id);
 @Override @org.springframework.data.jpa.repository.Query("select r from SwiftImportRecord r where r.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}") List<SwiftImportRecord> findAll();
}
