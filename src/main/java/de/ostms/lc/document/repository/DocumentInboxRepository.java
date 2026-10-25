package de.ostms.lc.document.repository;

import de.ostms.lc.document.domain.DocumentInboxItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentInboxRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<DocumentInboxItem, UUID> {
    default List<DocumentInboxItem> findTop100ByStatusOrderByReceivedAtDesc(String status){return findScopedStatus(status,org.springframework.data.domain.PageRequest.of(0,100));}
    @org.springframework.data.jpa.repository.Query("select item from DocumentInboxItem item where item.status=:status and item.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} order by item.receivedAt desc")
    List<DocumentInboxItem> findScopedStatus(@org.springframework.data.repository.query.Param("status") String status,org.springframework.data.domain.Pageable pageable);
    @Override @org.springframework.data.jpa.repository.Query("select item from DocumentInboxItem item where item.id=:id and item.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
    java.util.Optional<DocumentInboxItem> findById(UUID id);
    @org.springframework.data.jpa.repository.Query("select item.id from DocumentInboxItem item where item.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} and item.status = 'OPEN' and (item.extractionStatus = 'QUEUED' or (item.extractionStatus = 'PROCESSING' and (item.extractionStartedAt is null or item.extractionStartedAt < :expired))) order by item.receivedAt asc")
    List<UUID> findExtractionCandidates(@org.springframework.data.repository.query.Param("expired") java.time.LocalDateTime expired,org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select item from DocumentInboxItem item where item.id = :id and item.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
    java.util.Optional<DocumentInboxItem> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
