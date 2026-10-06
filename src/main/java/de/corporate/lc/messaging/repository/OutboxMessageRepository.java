package de.corporate.lc.messaging.repository;

import de.corporate.lc.messaging.domain.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {
    default List<OutboxMessage> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(String status,LocalDateTime now){return findPending(status,now,org.springframework.data.domain.PageRequest.of(0,50));}
    @org.springframework.data.jpa.repository.Query("select m from OutboxMessage m where m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} and m.status=:status and m.nextAttemptAt<=:now order by m.createdAt asc")
    List<OutboxMessage> findPending(@org.springframework.data.repository.query.Param("status") String status,@org.springframework.data.repository.query.Param("now") LocalDateTime now,org.springframework.data.domain.Pageable pageable);
    default List<OutboxMessage> findTop100ByStatusOrderByCreatedAtDesc(String status){return findStatus(status,org.springframework.data.domain.PageRequest.of(0,100));}
    @org.springframework.data.jpa.repository.Query("select m from OutboxMessage m where m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} and m.status=:status order by m.createdAt desc")
    List<OutboxMessage> findStatus(@org.springframework.data.repository.query.Param("status") String status,org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select count(m) from OutboxMessage m where m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} and m.status=:status")
    long countByStatus(@org.springframework.data.repository.query.Param("status") String status);
    @Override @org.springframework.data.jpa.repository.Query("select m from OutboxMessage m where m.id=:id and m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
    java.util.Optional<OutboxMessage> findById(@org.springframework.data.repository.query.Param("id") UUID id);
}
