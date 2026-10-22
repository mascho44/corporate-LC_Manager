package de.ostms.lc.apikey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey,UUID> {
 Optional<ApiKey> findByKeyHash(String keyHash);
 @Query("select k from ApiKey k where k.tenantId=:tenantId order by k.createdAt desc") List<ApiKey> forTenant(@Param("tenantId") UUID tenantId);
 @Query("select count(k) from ApiKey k where k.tenantId=:tenantId and k.revokedAt is null") long activeCount(@Param("tenantId") UUID tenantId);
}
