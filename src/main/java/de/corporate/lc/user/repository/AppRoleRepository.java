package de.corporate.lc.user.repository;
import de.corporate.lc.user.domain.*;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface AppRoleRepository extends de.corporate.lc.tenant.repository.TenantScopedRepository<AppRole,UUID>{
 @org.springframework.data.jpa.repository.Query("select r from AppRole r where r.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by r.name")
 List<AppRole> findAllByOrderByNameAsc();
 @org.springframework.data.jpa.repository.Query("select count(r)>0 from AppRole r where lower(r.name)=lower(:name) and r.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 boolean existsByNameIgnoreCase(String name);
 @org.springframework.data.jpa.repository.Query("select r from AppRole r where r.baseRole=:role and r.systemRole=true and r.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 Optional<AppRole> findByBaseRoleAndSystemRoleTrue(UserRole role);
 @Override @org.springframework.data.jpa.repository.Query("select r from AppRole r where r.id=:id and r.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 Optional<AppRole> findById(UUID id);
}
