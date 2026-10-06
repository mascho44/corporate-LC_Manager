package de.corporate.lc.rulepack;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PackSelectionRepository extends JpaRepository<PackSelection,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from PackSelection p where p.id=:id")
 Optional<PackSelection> locked(String id);
}
