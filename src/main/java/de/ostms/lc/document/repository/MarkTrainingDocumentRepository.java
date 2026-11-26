package de.ostms.lc.document.repository;

import de.ostms.lc.document.domain.MarkTrainingDocument;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarkTrainingDocumentRepository extends TenantScopedRepository<MarkTrainingDocument,UUID> {
 record Row(UUID id,String filename,int pageCount,String uploadedBy){}
 @Override @Query("select e from MarkTrainingDocument e where e.id=:id and "+OWNED)
 Optional<MarkTrainingDocument> findById(@Param("id") UUID id);
 /** Without the file content. */
 @Query("select new de.ostms.lc.document.repository.MarkTrainingDocumentRepository$Row(e.id,e.filename,e.pageCount,e.uploadedBy) from MarkTrainingDocument e where "+OWNED+" order by e.uploadedAt desc")
 List<Row> rows();
}
