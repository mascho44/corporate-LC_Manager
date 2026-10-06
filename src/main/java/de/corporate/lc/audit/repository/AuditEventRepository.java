package de.corporate.lc.audit.repository;
import de.corporate.lc.audit.domain.AuditEvent; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AuditEventRepository extends de.corporate.lc.tenant.repository.TenantScopedRepository<AuditEvent,UUID>{
 default List<AuditEvent> findTop200ByOrderByOccurredAtDesc(){return findRecent(org.springframework.data.domain.PageRequest.of(0,200));}
 @org.springframework.data.jpa.repository.Query("select e from AuditEvent e where e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by e.occurredAt desc")
 List<AuditEvent> findRecent(org.springframework.data.domain.Pageable pageable);
 default List<AuditEvent> findTop100ByEntityIdOrderByOccurredAtDesc(String entityId){return findRecentForEntity(entityId,org.springframework.data.domain.PageRequest.of(0,100));}
 @org.springframework.data.jpa.repository.Query("select e from AuditEvent e where e.entityId=:entityId and e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by e.occurredAt desc")
 List<AuditEvent> findRecentForEntity(@org.springframework.data.repository.query.Param("entityId") String entityId,org.springframework.data.domain.Pageable pageable);
 @org.springframework.data.jpa.repository.Query("select e from AuditEvent e where e.entityId in :entityIds and e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by e.occurredAt asc")
 List<AuditEvent> findByEntityIdInOrderByOccurredAtAsc(@org.springframework.data.repository.query.Param("entityIds") Collection<String> entityIds);
 @Override @org.springframework.data.jpa.repository.Query("select e from AuditEvent e where e.id=:id and e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 Optional<AuditEvent> findById(@org.springframework.data.repository.query.Param("id") UUID id);
 @Override @org.springframework.data.jpa.repository.Query("select e from AuditEvent e where e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 List<AuditEvent> findAll();
}
