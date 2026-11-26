package de.ostms.lc.document.repository;

import de.ostms.lc.document.domain.DocumentMark;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentMarkRepository extends TenantScopedRepository<DocumentMark,UUID> {
 @Override @Query("select e from DocumentMark e where e.id=:id and "+OWNED)
 Optional<DocumentMark> findById(@Param("id") UUID id);
 @Override @Query("select e from DocumentMark e where "+OWNED)
 List<DocumentMark> findAll();
 @Query("select e from DocumentMark e where e.documentId=:documentId and "+OWNED+" order by e.page, e.y, e.x")
 List<DocumentMark> forDocument(@Param("documentId") UUID documentId);
 @Query("select e from DocumentMark e where e.trainingDocumentId=:id and "+OWNED+" order by e.page, e.y, e.x")
 List<DocumentMark> forTrainingDocument(@Param("id") UUID id);
}
