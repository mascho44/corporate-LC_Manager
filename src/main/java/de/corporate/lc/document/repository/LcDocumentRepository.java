package de.corporate.lc.document.repository;

import de.corporate.lc.document.domain.LcDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LcDocumentRepository extends JpaRepository<LcDocument, UUID> {
    List<LcDocument> findByLetterOfCreditIdOrderByUploadedAtDesc(UUID lcId);
    long countByLetterOfCreditId(UUID lcId);
}
