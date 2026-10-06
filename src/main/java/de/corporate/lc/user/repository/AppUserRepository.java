package de.corporate.lc.user.repository;
import de.corporate.lc.user.domain.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AppUserRepository extends de.corporate.lc.tenant.repository.TenantScopedRepository<AppUser,UUID>{
 // Global identity lookup is reserved for authentication/password reset; administration is scoped below.
 Optional<AppUser> findByUsernameIgnoreCase(String username);boolean existsByUsernameIgnoreCase(String username);
 @org.springframework.data.jpa.repository.Query("select count(u) from AppUser u left join u.assignedRole r where coalesce(r.baseRole,u.role)=:role and u.active=true and u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 long countByRoleAndActiveTrue(UserRole role);
 @org.springframework.data.jpa.repository.Query("select count(u) from AppUser u where u.assignedRole.id=:roleId and u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 long countByAssignedRoleId(UUID roleId);
 @org.springframework.data.jpa.repository.Query("select count(u) from AppUser u where u.assignedRole.id=:roleId and u.active=true and u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 long countByAssignedRoleIdAndActiveTrue(UUID roleId);
 @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by u.username")
 List<AppUser> findAllByOrderByUsernameAsc();
 @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.active=true and u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by u.displayName")
 List<AppUser> findAllByActiveTrueOrderByDisplayNameAsc();
 @Override @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id=:id and u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 Optional<AppUser> findById(UUID id);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id=:id")
 Optional<AppUser> findForPasswordReset(@org.springframework.data.repository.query.Param("id") UUID id);
}
