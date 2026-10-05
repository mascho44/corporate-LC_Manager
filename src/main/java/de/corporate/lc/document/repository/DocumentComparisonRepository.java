package de.corporate.lc.document.repository;
import de.corporate.lc.document.domain.DocumentComparison;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DocumentComparisonRepository extends JpaRepository<DocumentComparison,UUID>{List<DocumentComparison> findByLcIdOrderByCreatedAtDesc(UUID id);}
