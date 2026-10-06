package de.corporate.lc.document.repository;

import de.corporate.lc.document.domain.DocumentInboxItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentInboxRepository extends JpaRepository<DocumentInboxItem, UUID> {
    List<DocumentInboxItem> findTop100ByStatusOrderByReceivedAtDesc(String status);
    @org.springframework.data.jpa.repository.Query("select item.id from DocumentInboxItem item where item.status = 'OPEN' and (item.extractionStatus = 'QUEUED' or (item.extractionStatus = 'PROCESSING' and (item.extractionStartedAt is null or item.extractionStartedAt < :expired))) order by item.receivedAt asc")
    List<UUID> findExtractionCandidates(@org.springframework.data.repository.query.Param("expired") java.time.LocalDateTime expired,org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select item from DocumentInboxItem item where item.id = :id")
    java.util.Optional<DocumentInboxItem> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
