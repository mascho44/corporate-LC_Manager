package de.corporate.lc.email.repository;
import de.corporate.lc.email.domain.EmailDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
public interface EmailDeliveryRepository extends de.corporate.lc.tenant.repository.TenantScopedRepository<EmailDelivery,UUID>{
 @Query("select e from EmailDelivery e where e.letterOfCredit.id=:lcId and e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} and e.letterOfCredit.tenantId=e.tenantId order by e.sentAt desc")
 List<EmailDelivery> findByLetterOfCreditIdOrderBySentAtDesc(UUID lcId);
 @Override @Query("select e from EmailDelivery e where e.id=:id and e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} and e.letterOfCredit.tenantId=e.tenantId")
 Optional<EmailDelivery> findById(UUID id);
 @Override @Query("select e from EmailDelivery e where e.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} and e.letterOfCredit.tenantId=e.tenantId")
 List<EmailDelivery> findAll();
}
