package de.corporate.lc.rulepack;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PackVersionRepository extends JpaRepository<StoredPackVersion,UUID> {
 boolean existsByPackIdAndVersion(String packId,String version);
 List<StoredPackVersion> findAllByOrderByImportedAtDesc();
}
