package de.corporate.lc.audit.repository;
import de.corporate.lc.audit.domain.AuditEvent; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AuditEventRepository extends JpaRepository<AuditEvent,UUID>{List<AuditEvent> findTop200ByOrderByOccurredAtDesc();}
