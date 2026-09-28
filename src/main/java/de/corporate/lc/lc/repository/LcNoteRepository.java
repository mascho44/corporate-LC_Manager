package de.corporate.lc.lc.repository;

import de.corporate.lc.lc.domain.LcNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LcNoteRepository extends JpaRepository<LcNote, UUID> {
    List<LcNote> findTop100ByLetterOfCreditIdOrderByCreatedAtDesc(UUID letterOfCreditId);
}
