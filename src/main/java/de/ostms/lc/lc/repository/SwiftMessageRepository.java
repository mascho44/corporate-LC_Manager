package de.ostms.lc.lc.repository;
import de.ostms.lc.lc.domain.SwiftMessage;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SwiftMessageRepository extends TenantScopedRepository<SwiftMessage,UUID> {
 @Override @Query("select e from SwiftMessage e where e.id=:id and "+OWNED)
 Optional<SwiftMessage> findById(@Param("id") UUID id);
 @Query("select e from SwiftMessage e where e.lcId=:lcId and "+OWNED+" order by e.importedAt desc")
 List<SwiftMessage> forLc(@Param("lcId") UUID lcId);
 @Query("select e from SwiftMessage e where e.lcId is null and "+OWNED+" order by e.importedAt desc")
 List<SwiftMessage> unassigned();
 @Query("select count(e)>0 from SwiftMessage e where e.messageType=:type and e.reference=:reference and e.rawMessage=:raw and "+OWNED)
 boolean existsSame(@Param("type") String type,@Param("reference") String reference,@Param("raw") String raw);
}
