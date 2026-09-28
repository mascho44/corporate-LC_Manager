package de.corporate.lc.check.repository;
import de.corporate.lc.check.domain.DocumentCheckDecision;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface DocumentCheckDecisionRepository extends JpaRepository<DocumentCheckDecision,UUID>{List<DocumentCheckDecision> findByLcId(UUID lcId);Optional<DocumentCheckDecision> findByLcIdAndFindingCodeAndDocumentName(UUID lcId,String code,String documentName);void deleteAllByLcId(UUID lcId);}
