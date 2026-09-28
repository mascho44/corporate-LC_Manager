package de.corporate.lc.document.repository;

import de.corporate.lc.document.domain.DocumentDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DocumentDraftRepository extends JpaRepository<DocumentDraft, UUID> {
    List<DocumentDraft> findByLcIdOrderByUpdatedAtDesc(UUID lcId);
}
