package de.ostms.lc.customer;

import de.ostms.lc.tenant.repository.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends TenantScopedRepository<Customer,UUID> {
 @Override @Query("select e from Customer e where e.id=:id and "+OWNED)
 Optional<Customer> findById(@Param("id") UUID id);
 @Override @Query("select e from Customer e where "+OWNED)
 List<Customer> findAll();
 @Query("select e from Customer e where e.customerNumber=:number and "+OWNED)
 Optional<Customer> byNumber(@Param("number") String number);
}
