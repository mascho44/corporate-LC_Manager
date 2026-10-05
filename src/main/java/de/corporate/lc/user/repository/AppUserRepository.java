package de.corporate.lc.user.repository;
import de.corporate.lc.user.domain.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AppUserRepository extends JpaRepository<AppUser,UUID>{Optional<AppUser> findByUsernameIgnoreCase(String username);boolean existsByUsernameIgnoreCase(String username);long countByRoleAndActiveTrue(UserRole role);long countByAssignedRoleId(UUID roleId);List<AppUser> findAllByOrderByUsernameAsc();List<AppUser> findAllByActiveTrueOrderByDisplayNameAsc();
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id=:id")
 Optional<AppUser> findForPasswordReset(@org.springframework.data.repository.query.Param("id") UUID id);
}
