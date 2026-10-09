package de.ostms.lc.lc.repository;

import de.ostms.lc.lc.domain.LetterOfCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LetterOfCreditRepository extends de.ostms.lc.tenant.repository.TenantScopedRepository<LetterOfCredit, UUID> {
    interface AssignmentTarget {
        UUID getId();
        String getReference();
        de.ostms.lc.lc.domain.LetterOfCreditStatus getStatus();
    }
    @org.springframework.data.jpa.repository.Query("select lc.id as id, lc.reference as reference, lc.status as status from LetterOfCredit lc where lc.tenantId = :#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()} order by lc.reference")
    List<AssignmentTarget> findAssignmentTargets();
    @org.springframework.data.jpa.repository.Query("select count(lc)>0 from LetterOfCredit lc where lc.reference=:reference and lc.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
    boolean existsByReference(@org.springframework.data.repository.query.Param("reference") String reference);
    @org.springframework.data.jpa.repository.Query("select lc from LetterOfCredit lc where lc.reference=:reference and lc.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
    Optional<LetterOfCredit> findByReference(String reference);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select lc from LetterOfCredit lc where lc.reference = :reference and lc.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
    Optional<LetterOfCredit> findForAmendment(@org.springframework.data.repository.query.Param("reference") String reference);
    @org.springframework.data.jpa.repository.Query("select count(lc)>0 from LetterOfCredit lc where lc.reference=:reference and lc.id<>:id and lc.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
    boolean existsByReferenceAndIdNot(@org.springframework.data.repository.query.Param("reference") String reference,@org.springframework.data.repository.query.Param("id") UUID id);

    @Override
    @org.springframework.data.jpa.repository.Query("select lc from LetterOfCredit lc where lc.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
    List<LetterOfCredit> findAll();

    @Override
    @org.springframework.data.jpa.repository.Query("select lc from LetterOfCredit lc where lc.id=:id and lc.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
    Optional<LetterOfCredit> findById(UUID id);
    @Override @org.springframework.data.jpa.repository.Query("select count(lc) from LetterOfCredit lc where lc.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
    long count();
    @Override @org.springframework.data.jpa.repository.Query("select count(lc)>0 from LetterOfCredit lc where lc.id=:id and lc.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}")
    boolean existsById(UUID id);
}
