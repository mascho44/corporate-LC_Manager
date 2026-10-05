package de.corporate.lc.check.repository;
import de.corporate.lc.check.domain.DocumentCheckDecision;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface DocumentCheckDecisionRepository extends JpaRepository<DocumentCheckDecision,UUID>{List<DocumentCheckDecision> findByLcId(UUID lcId);Optional<DocumentCheckDecision> findByLcIdAndFindingCodeAndDocumentNameAndFindingFingerprint(UUID lcId,String code,String documentName,String fingerprint);void deleteAllByLcId(UUID lcId);}
