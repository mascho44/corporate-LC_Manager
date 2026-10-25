package de.ostms.lc.ebics;
import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface EbicsConnectionRepository extends TenantScopedRepository<EbicsConnection,UUID> {
 @Override @Query("select e from EbicsConnection e where e.id=:id and "+OWNED)
 Optional<EbicsConnection> findById(@Param("id") UUID id);
 default Optional<EbicsConnection> findCurrent(){return findAll().stream().findFirst();}
}
