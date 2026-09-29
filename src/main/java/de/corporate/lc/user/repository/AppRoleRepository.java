package de.corporate.lc.user.repository;
import de.corporate.lc.user.domain.*;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface AppRoleRepository extends JpaRepository<AppRole,UUID>{List<AppRole> findAllByOrderByNameAsc();boolean existsByNameIgnoreCase(String name);Optional<AppRole> findByBaseRoleAndSystemRoleTrue(UserRole role);}
