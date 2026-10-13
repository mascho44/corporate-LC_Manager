package de.ostms.lc.customer;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InboxCustomerAssignmentRepository extends TenantScopedRepository<InboxCustomerAssignment,UUID> {
 @Override @Query("select e from InboxCustomerAssignment e where e.id=:id and "+OWNED)
 Optional<InboxCustomerAssignment> findById(@Param("id") UUID id);
 @Override @Query("select e from InboxCustomerAssignment e where "+OWNED)
 List<InboxCustomerAssignment> findAll();
 @Query("select e from InboxCustomerAssignment e where e.inboxItemId=:item and "+OWNED)
 Optional<InboxCustomerAssignment> forItem(@Param("item") UUID item);
}
