package de.corporate.lc.lc.repository;

import de.corporate.lc.lc.domain.LetterOfCredit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LetterOfCreditRepository extends JpaRepository<LetterOfCredit, UUID> {
    boolean existsByReference(String reference);
    Optional<LetterOfCredit> findByReference(String reference);
    boolean existsByReferenceAndIdNot(String reference, UUID id);

    @Override
    @EntityGraph(attributePaths = "requiredDocuments")
    List<LetterOfCredit> findAll();

    @Override
    @EntityGraph(attributePaths = "requiredDocuments")
    Optional<LetterOfCredit> findById(UUID id);
}
