package de.corporate.lc.user.repository;
import de.corporate.lc.user.domain.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AppUserRepository extends de.corporate.lc.tenant.repository.TenantScopedRepository<AppUser,UUID>{
 // Own-identity 2FA protection only, not a cross-tenant administration directory.
 @org.springframework.data.jpa.repository.Query("select count(m)>0 from TenantMembership m where m.user.id=:userId and m.active=true and m.user.active=true and m.role.baseRole=de.corporate.lc.user.domain.UserRole.ADMIN and not exists(select s.id from TenantMembershipSuspension s where s.tenantId=m.tenantId and s.userId=m.user.id and s.suspended=true)")
 boolean hasActiveAdministratorMembership(UUID userId);
 @org.springframework.data.jpa.repository.Query("select m.user from TenantMembership m where m.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} and m.active=true and m.user.active=true and not exists(select s.id from TenantMembershipSuspension s where s.tenantId=m.tenantId and s.userId=m.user.id and s.suspended=true) order by m.user.displayName")
 List<AppUser> findAssignableMembershipUsers();
 // Global identity lookup is reserved for authentication/password reset; administration is scoped below.
 Optional<AppUser> findByUsernameIgnoreCase(String username);boolean existsByUsernameIgnoreCase(String username);
 // Internal identity mutation guard; never expose cross-tenant membership details.
 @org.springframework.data.jpa.repository.Query("select count(m) from TenantMembership m where m.user.id=:userId and m.tenantId<>:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 long countForeignMemberships(UUID userId);
 @org.springframework.data.jpa.repository.Query("select count(u) from AppUser u left join u.assignedRole r where coalesce(r.baseRole,u.role)=:role and u.active=true and not exists(select s.id from TenantMembershipSuspension s where s.tenantId=u.tenantId and s.userId=u.id and s.suspended=true) and u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 long countByRoleAndActiveTrue(UserRole role);
 @org.springframework.data.jpa.repository.Query("select count(u) from AppUser u where u.assignedRole.id=:roleId and u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 long countByAssignedRoleId(UUID roleId);
 @org.springframework.data.jpa.repository.Query("select count(u) from AppUser u where u.assignedRole.id=:roleId and u.active=true and not exists(select s.id from TenantMembershipSuspension s where s.tenantId=u.tenantId and s.userId=u.id and s.suspended=true) and u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 long countByAssignedRoleIdAndActiveTrue(UUID roleId);
 @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by u.username")
 List<AppUser> findAllByOrderByUsernameAsc();
 @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.active=true and not exists(select s.id from TenantMembershipSuspension s where s.tenantId=u.tenantId and s.userId=u.id and s.suspended=true) and u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by u.displayName")
 List<AppUser> findAllByActiveTrueOrderByDisplayNameAsc();
 @org.springframework.data.jpa.repository.Query("select count(u)>0 from AppUser u where lower(u.username)=lower(:username) and u.active=true and not exists(select s.id from TenantMembershipSuspension s where s.tenantId=u.tenantId and s.userId=u.id and s.suspended=true) and u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 boolean existsAssignableUsername(String username);
 @Override @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id=:id and u.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
 Optional<AppUser> findById(UUID id);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id=:id")
 Optional<AppUser> findForPasswordReset(@org.springframework.data.repository.query.Param("id") UUID id);
}
