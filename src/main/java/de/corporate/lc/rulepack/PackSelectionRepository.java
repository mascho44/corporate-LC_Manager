package de.corporate.lc.rulepack;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PackSelectionRepository extends JpaRepository<PackSelection,PackSelection.Key> {
 @Query("select p from PackSelection p where p.id=:id and p.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}") Optional<PackSelection> findById(@org.springframework.data.repository.query.Param("id") String id);
 @Query("select count(p)>0 from PackSelection p where p.id=:id and p.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}") boolean existsById(@org.springframework.data.repository.query.Param("id") String id);
 @Override default Optional<PackSelection> findById(PackSelection.Key key){return de.corporate.lc.tenant.domain.TenantContext.currentId().equals(key.tenantId)?findById(key.id):Optional.empty();}
 @Override default boolean existsById(PackSelection.Key key){return findById(key).isPresent();}
 @Override @Query("select p from PackSelection p where p.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}") List<PackSelection> findAll();
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from PackSelection p where p.id=:id and p.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 Optional<PackSelection> locked(@org.springframework.data.repository.query.Param("id") String id);
}
