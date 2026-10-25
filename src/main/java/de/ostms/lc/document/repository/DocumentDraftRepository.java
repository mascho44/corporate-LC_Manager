package de.ostms.lc.document.repository;

import de.ostms.lc.document.domain.DocumentDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DocumentDraftRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<DocumentDraft, UUID> {
    @org.springframework.data.jpa.repository.Query("select d from DocumentDraft d where d.lcId=:lcId and d.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} order by d.updatedAt desc")
    List<DocumentDraft> findByLcIdOrderByUpdatedAtDesc(@org.springframework.data.repository.query.Param("lcId") UUID lcId);
    @Override @org.springframework.data.jpa.repository.Query("select d from DocumentDraft d where d.id=:id and d.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
    Optional<DocumentDraft> findById(@org.springframework.data.repository.query.Param("id") UUID id);
    @Override @org.springframework.data.jpa.repository.Query("select d from DocumentDraft d where d.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
    List<DocumentDraft> findAll();
}
