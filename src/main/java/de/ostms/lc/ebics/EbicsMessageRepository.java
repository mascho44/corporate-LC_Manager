package de.ostms.lc.ebics;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EbicsMessageRepository extends TenantScopedRepository<EbicsMessage,UUID> {
 @Override @Query("select e from EbicsMessage e where e.id=:id and "+OWNED)
 Optional<EbicsMessage> findById(@Param("id") UUID id);
 @Query("select e from EbicsMessage e where e.sha256=:sha and "+OWNED)
 Optional<EbicsMessage> findBySha(@Param("sha") String sha);
 @Query("select count(e) from EbicsMessage e where e.status='NEW' and "+OWNED)
 long countNew();
 @Query("select e from EbicsMessage e where "+OWNED+" order by e.receivedAt desc")
 List<EbicsMessage> newestFirst();
}
