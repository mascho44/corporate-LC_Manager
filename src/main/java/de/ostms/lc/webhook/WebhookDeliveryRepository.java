package de.ostms.lc.webhook;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryRepository extends TenantScopedRepository<WebhookDelivery,UUID> {
 @Override @Query("select e from WebhookDelivery e where e.id=:id and "+OWNED)
 Optional<WebhookDelivery> findById(@Param("id") UUID id);
 @Query("select e from WebhookDelivery e where e.status='PENDING' and e.nextAttemptAt<=:now and "+OWNED+" order by e.createdAt")
 List<WebhookDelivery> due(@Param("now") LocalDateTime now,Pageable page);
 @Query("select e from WebhookDelivery e where e.subscriptionId=:subscription and "+OWNED+" order by e.createdAt desc")
 List<WebhookDelivery> recent(@Param("subscription") UUID subscription,Pageable page);
 @Modifying @org.springframework.transaction.annotation.Transactional @Query("delete from WebhookDelivery e where e.subscriptionId=:subscription and "+OWNED)
 void deleteOf(@Param("subscription") UUID subscription);
 @Modifying @org.springframework.transaction.annotation.Transactional @Query("delete from WebhookDelivery e where ((e.status='DELIVERED' and e.createdAt<:delivered) or (e.status='DEAD' and e.createdAt<:dead)) and "+OWNED)
 int purge(@Param("delivered") LocalDateTime delivered,@Param("dead") LocalDateTime dead);
 @Query("select count(e) from WebhookDelivery e where e.status=:status and "+OWNED)
 long countByStatus(@Param("status") String status);
}
