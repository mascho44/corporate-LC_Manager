package de.ostms.lc.sanctions;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SanctionsListRepository extends JpaRepository<SanctionsList,UUID> {
    Optional<SanctionsList> findFirstBySourceOrderByFetchedAtDesc(String source);
}
