package de.ostms.lc.document.repository;

import de.ostms.lc.document.domain.LcDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LcDocumentRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<LcDocument, UUID> {
    @org.springframework.data.jpa.repository.Query("select d from LcDocument d where d.letterOfCredit.id=:lcId and d.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} and d.letterOfCredit.tenantId=d.tenantId order by d.uploadedAt desc")
    List<LcDocument> findByLetterOfCreditIdOrderByUploadedAtDesc(UUID lcId);
    @org.springframework.data.jpa.repository.Query("select count(d) from LcDocument d where d.letterOfCredit.id=:lcId and d.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} and d.letterOfCredit.tenantId=d.tenantId")
    long countByLetterOfCreditId(UUID lcId);
    @Override @org.springframework.data.jpa.repository.Query("select d from LcDocument d where d.id=:id and d.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} and d.letterOfCredit.tenantId=d.tenantId")
    java.util.Optional<LcDocument> findById(UUID id);
}
