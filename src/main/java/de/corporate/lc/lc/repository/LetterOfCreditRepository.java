package de.corporate.lc.lc.repository;

import de.corporate.lc.lc.domain.LetterOfCredit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LetterOfCreditRepository extends JpaRepository<LetterOfCredit, UUID> {
    interface AssignmentTarget {
        UUID getId();
        String getReference();
        de.corporate.lc.lc.domain.LetterOfCreditStatus getStatus();
    }
    @org.springframework.data.jpa.repository.Query("select lc.id as id, lc.reference as reference, lc.status as status from LetterOfCredit lc order by lc.reference")
    List<AssignmentTarget> findAssignmentTargets();
    boolean existsByReference(String reference);
    Optional<LetterOfCredit> findByReference(String reference);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select lc from LetterOfCredit lc where lc.reference = :reference")
    Optional<LetterOfCredit> findForAmendment(@org.springframework.data.repository.query.Param("reference") String reference);
    boolean existsByReferenceAndIdNot(String reference, UUID id);

    @Override
    @EntityGraph(attributePaths = {"requiredDocuments", "additionalFields"})
    List<LetterOfCredit> findAll();

    @Override
    @EntityGraph(attributePaths = {"requiredDocuments", "additionalFields"})
    Optional<LetterOfCredit> findById(UUID id);
}
