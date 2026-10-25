package de.ostms.lc.webhook;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookSubscriptionRepository extends TenantScopedRepository<WebhookSubscription,UUID> {
 @Override @Query("select e from WebhookSubscription e where e.id=:id and "+OWNED)
 Optional<WebhookSubscription> findById(@Param("id") UUID id);
 @Override @Query("select e from WebhookSubscription e where "+OWNED+" order by e.name")
 List<WebhookSubscription> findAll();
 @Query("select e from WebhookSubscription e where e.active=true and "+OWNED)
 List<WebhookSubscription> active();
}
