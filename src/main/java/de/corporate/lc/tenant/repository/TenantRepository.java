package de.corporate.lc.tenant.repository;
import de.corporate.lc.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface TenantRepository extends JpaRepository<Tenant,UUID> {
 boolean existsByCodeIgnoreCase(String code);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select t from Tenant t where t.id=:id")
 java.util.Optional<Tenant> findForAdministration(@org.springframework.data.repository.query.Param("id") UUID id);
}
