package de.corporate.lc.imports.repository;
import de.corporate.lc.imports.domain.SwiftImportRecord; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SwiftImportRecordRepository extends JpaRepository<SwiftImportRecord,UUID>{List<SwiftImportRecord> findTop20ByOrderByImportedAtDesc();long countByStatus(String status);}
