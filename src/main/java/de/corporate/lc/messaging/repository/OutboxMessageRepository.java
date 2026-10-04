package de.corporate.lc.messaging.repository;

import de.corporate.lc.messaging.domain.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {
    List<OutboxMessage> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(String status, LocalDateTime now);
    List<OutboxMessage> findTop100ByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
}
