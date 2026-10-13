package de.ostms.lc.capture;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InboxCaptureInfoRepository extends TenantScopedRepository<InboxCaptureInfo,UUID> {
 @Override @Query("select e from InboxCaptureInfo e where e.id=:id and "+OWNED)
 Optional<InboxCaptureInfo> findById(@Param("id") UUID id);
 @Override @Query("select e from InboxCaptureInfo e where "+OWNED)
 List<InboxCaptureInfo> findAll();
 @Query("select e from InboxCaptureInfo e where e.sha256=:sha and "+OWNED)
 Optional<InboxCaptureInfo> bySha(@Param("sha") String sha);
}
