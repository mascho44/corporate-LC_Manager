package de.ostms.lc.capture;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaptureTokenRepository extends JpaRepository<CaptureToken,UUID> {
 Optional<CaptureToken> findByTokenHash(String tokenHash);
 @Query("select t from CaptureToken t where t.tenantId=:tenantId order by t.createdAt desc") List<CaptureToken> forTenant(@Param("tenantId") UUID tenantId);
 @Query("select count(t) from CaptureToken t where t.tenantId=:tenantId and t.revokedAt is null") long activeCount(@Param("tenantId") UUID tenantId);
}
