package de.corporate.lc.user.repository;
import de.corporate.lc.user.domain.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AppUserRepository extends JpaRepository<AppUser,UUID>{Optional<AppUser> findByUsernameIgnoreCase(String username);boolean existsByUsernameIgnoreCase(String username);long countByRoleAndActiveTrue(UserRole role);List<AppUser> findAllByOrderByUsernameAsc();List<AppUser> findAllByActiveTrueOrderByDisplayNameAsc();}
