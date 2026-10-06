package de.corporate.lc.document.repository;
import de.corporate.lc.document.domain.DocumentComparison;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DocumentComparisonRepository extends de.corporate.lc.tenant.repository.TenantScopedRepository<DocumentComparison,UUID>{
 @org.springframework.data.jpa.repository.Query("select c from DocumentComparison c where c.lcId=:id and c.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by c.createdAt desc")
 List<DocumentComparison> findByLcIdOrderByCreatedAtDesc(UUID id);
 @Override @org.springframework.data.jpa.repository.Query("select c from DocumentComparison c where c.id=:id and c.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 java.util.Optional<DocumentComparison> findById(UUID id);
 @Override @org.springframework.data.jpa.repository.Query("select c from DocumentComparison c where c.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 List<DocumentComparison> findAll();
}
